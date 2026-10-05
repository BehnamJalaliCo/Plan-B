package com.behnamjalali.planb.feature.notebooks

import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.DocumentFiles
import com.behnamjalali.planb.core.data.repository.NoteDraft
import com.behnamjalali.planb.core.data.repository.NoteHistoryRepository
import com.behnamjalali.planb.core.data.repository.NoteLinkRepository
import com.behnamjalali.planb.core.data.repository.NoteRepository
import com.behnamjalali.planb.core.data.repository.TemplateRepository
import com.behnamjalali.planb.core.data.security.BiometricKeyStore
import com.behnamjalali.planb.core.data.security.NoteVault
import javax.crypto.Cipher
import com.behnamjalali.planb.core.data.repository.AttachmentRepository
import com.behnamjalali.planb.core.model.Attachment
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.MarkdownAttachment
import com.behnamjalali.planb.feature.notebooks.media.HandwritingRecognition
import com.behnamjalali.planb.feature.notebooks.media.PickedContent
import com.behnamjalali.planb.feature.notebooks.media.SpeechTranscription
import com.behnamjalali.planb.feature.notebooks.media.TextRecognition
import com.behnamjalali.planb.feature.notebooks.rich.HandwritingConsent
import com.behnamjalali.planb.feature.notebooks.rich.RichEditor
import com.behnamjalali.planb.feature.notebooks.rich.RichMessage
import com.behnamjalali.planb.feature.notebooks.rich.RichWork
import com.behnamjalali.planb.feature.notebooks.rich.toNoteBlock
import kotlinx.serialization.json.JsonObject
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Markdown
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.NoteLinks
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EditorBlock(
    val id: String,
    val type: BlockType,
    val value: TextFieldValue = TextFieldValue(""),
    val checked: Boolean = false,
    /**
     * Bumped whenever the ViewModel itself changes [value] (a split, merge, cursor move or restored
     * draft). The block's text field keeps its own value while typing and only takes [value] over
     * when this changes, so a value that is a frame behind never overwrites fresh keystrokes.
     */
    val revision: Int = 0,
    /** Payload of a rich block (Plan-B Pro), see `core.model.rich`. */
    val data: JsonObject? = null,
    /** The attachment of an image, file, scan, recording or drawing block. */
    val attachmentId: Long? = null,
)

enum class SaveStatus { SAVED, SAVING, FAILED }

data class NoteEditorState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val noteId: EntityId = 0,
    val notebookId: EntityId = 0,
    val sectionId: EntityId? = null,
    val title: TextFieldValue = TextFieldValue(""),
    /** Like [EditorBlock.revision], for the title field. */
    val titleRevision: Int = 0,
    val blocks: List<EditorBlock> = emptyList(),
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val tags: List<Tag> = emptyList(),
    val saveStatus: SaveStatus = SaveStatus.SAVED,
    val draft: NoteDraft? = null,
    val focusId: String? = null,
    val focusVersion: Int = 0,
    /** A locked note (Plan-B Pro #36): its body is encrypted with the note passphrase. */
    val locked: Boolean = false,
    /** Locked and the vault is closed: the body is not loaded until the user unlocks. */
    val needsUnlock: Boolean = false,
    val passphraseDialog: PassphraseDialog? = null,
    val wrongPassphrase: Boolean = false,
    val busy: Boolean = false,
    /** Fingerprint can open locked notes on this device. */
    val fingerprint: Boolean = false,
    /** The note's attachments by id (rich blocks, Plan-B Pro). */
    val attachments: Map<Long, Attachment> = emptyMap(),
    /** Recognition or import running on a block, by block id. */
    val richWork: Map<String, RichWork> = emptyMap(),
    /** Asking before a handwriting model is downloaded. */
    val handwritingConsent: HandwritingConsent? = null,
)

enum class PassphraseDialog { SETUP, UNLOCK }

sealed interface NoteEditorEvent {
    data object Deleted : NoteEditorEvent
    data class Duplicated(val id: EntityId) : NoteEditorEvent
    data object Exported : NoteEditorEvent
    data object ExportFailed : NoteEditorEvent
    data object TemplateSaved : NoteEditorEvent
    data object Locked : NoteEditorEvent
    data object LockRemoved : NoteEditorEvent
    data object Failed : NoteEditorEvent
    data class Rich(val message: RichMessage) : NoteEditorEvent
}

/** Pure block-editing rules; kept separate from Android so they are unit-testable. */
object BlockEditing {
    private val continuing = setOf(BlockType.CHECKLIST, BlockType.BULLET, BlockType.NUMBERED, BlockType.QUOTE)

    data class Result(val blocks: List<EditorBlock>, val focusId: String?, val cursor: Int)

    /**
     * Applies an edit to block [id]. A newline this edit inserted (typed Enter or pasted text)
     * splits the block; newlines already in the text (e.g. an imported multi-line paragraph) are
     * content, and code blocks never split.
     */
    fun change(blocks: List<EditorBlock>, id: String, value: TextFieldValue, newId: () -> String): Result {
        val index = blocks.indexOfFirst { it.id == id }
        if (index < 0) return Result(blocks, null, 0)
        val block = blocks[index]
        val plain = Result(blocks.toMutableList().also { it[index] = block.copy(value = value) }, null, 0)
        if (block.type == BlockType.CODE || '\n' !in value.text) return plain
        val old = block.value.text
        val new = value.text
        val (start, end) = insertedRange(old, value)
        val inserted = new.substring(start, end)
        if ('\n' !in inserted) return plain
        val prefix = new.substring(0, start)
        val suffix = new.substring(end)
        val updated = blocks.toMutableList()
        // Enter on an empty list item ends the list instead of adding another empty item.
        if (old.isEmpty() && inserted == "\n" && block.type in continuing) {
            updated[index] = block.copy(type = BlockType.TEXT, value = TextFieldValue(""))
            return Result(updated, block.id, 0)
        }
        // Enter at the very start of a block opens an empty block of the same kind above it; this
        // block keeps its text, type and check mark, and keeps the caret.
        if (prefix.isEmpty() && inserted == "\n" && suffix.isNotEmpty()) {
            updated[index] = block.copy(value = TextFieldValue(suffix, TextRange(0)))
            updated.add(index, EditorBlock(newId(), block.type))
            return Result(updated, block.id, 0)
        }
        val parts = inserted.split('\n')
        val nextType = if (block.type in continuing) block.type else BlockType.TEXT
        val first = prefix + parts.first()
        updated[index] = block.copy(value = TextFieldValue(first, TextRange(first.length)))
        val rest = parts.drop(1)
        val added = rest.mapIndexed { i, part -> EditorBlock(newId(), nextType, TextFieldValue(if (i == rest.lastIndex) part + suffix else part)) }
        updated.addAll(index + 1, added)
        // The caret goes right after the inserted text, i.e. before what followed the old caret.
        return Result(updated, added.last().id, rest.last().length)
    }

    /** Start and end (exclusive) in the new text of what this edit inserted. */
    internal fun insertedRange(old: String, value: TextFieldValue): Pair<Int, Int> {
        val new = value.text
        val delta = new.length - old.length
        val caret = value.selection.end
        if (delta > 0 && value.selection.collapsed && caret in delta..new.length) {
            val start = caret - delta
            if (new.regionMatches(0, old, 0, start) && new.regionMatches(caret, old, start, new.length - caret)) return start to caret
        }
        var prefix = 0
        while (prefix < old.length && prefix < new.length && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < old.length - prefix && suffix < new.length - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
        return prefix to new.length - suffix
    }

    /**
     * Joins block [id] onto the end of the previous block (which keeps its type), or removes a
     * divider right above it. Used by the toolbar's merge action, which works on any keyboard.
     */
    fun mergeWithPrevious(blocks: List<EditorBlock>, id: String): Result {
        val index = blocks.indexOfFirst { it.id == id }
        if (index <= 0) return Result(blocks, null, 0)
        val block = blocks[index]
        val previous = blocks[index - 1]
        // Text never merges into or out of a rich block (a table, an image, …).
        if (block.type.isRich || previous.type.isRich) return Result(blocks, null, 0)
        val updated = blocks.toMutableList()
        if (previous.type == BlockType.DIVIDER) {
            updated.removeAt(index - 1)
            return Result(updated, block.id, 0)
        }
        val joinAt = previous.value.text.length
        updated[index - 1] = previous.copy(value = TextFieldValue(previous.value.text + block.value.text))
        updated.removeAt(index)
        return Result(updated, previous.id, joinAt)
    }

    /** Backspace at the start of a block: merge with the previous text block or remove an empty one. */
    fun backspaceAtStart(blocks: List<EditorBlock>, id: String): Result {
        val index = blocks.indexOfFirst { it.id == id }
        if (index < 0) return Result(blocks, null, 0)
        val block = blocks[index]
        if (block.type.isRich) return Result(blocks, null, 0)
        if (block.type != BlockType.TEXT) {
            return Result(blocks.toMutableList().also { it[index] = block.copy(type = BlockType.TEXT) }, block.id, 0)
        }
        if (index == 0) return Result(blocks, null, 0)
        val previous = blocks[index - 1]
        if (previous.type.isRich) return Result(blocks, previous.id, 0)
        val updated = blocks.toMutableList()
        if (previous.type == BlockType.DIVIDER) {
            updated.removeAt(index - 1)
            return Result(updated, block.id, 0)
        }
        val joinAt = previous.value.text.length
        updated[index - 1] = previous.copy(value = TextFieldValue(previous.value.text + block.value.text))
        updated.removeAt(index)
        return Result(updated, previous.id, joinAt)
    }

    fun ensureNotEmpty(blocks: List<EditorBlock>, newId: () -> String): List<EditorBlock> =
        if (blocks.none { it.type != BlockType.DIVIDER }) blocks + EditorBlock(newId(), BlockType.TEXT) else blocks
}

@OptIn(FlowPreview::class)
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val notes: NoteRepository,
    private val templates: TemplateRepository,
    private val files: DocumentFiles,
    @ApplicationScope private val appScope: CoroutineScope,
    private val vault: NoteVault? = null,
    private val keyStore: BiometricKeyStore? = null,
    /** Plan-B Pro rich blocks: their files and recognition engines (absent in older tests). */
    private val attachments: AttachmentRepository? = null,
    textRecognition: TextRecognition? = null,
    handwriting: HandwritingRecognition? = null,
    speech: SpeechTranscription? = null,
    picked: PickedContent? = null,
    /** Note history (Plan-B Pro #16); null keeps no versions. */
    private val history: NoteHistoryRepository? = null,
    /** Current titles of linked notes for exports (Plan-B Pro #16). */
    private val links: NoteLinkRepository? = null,
) : ViewModel() {
    private val route = runCatching { savedState.toRoute<NoteEditorRoute>() }.getOrDefault(NoteEditorRoute())
    private val _state = MutableStateFlow(NoteEditorState())
    val state: StateFlow<NoteEditorState> = _state.asStateFlow()

    val notebooks: StateFlow<List<Notebook>> = notes.observeNotebooks(false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<NoteEditorEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<NoteEditorEvent> = _events

    private val edits = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    private val saveMutex = Mutex()
    private var dirty = false
    private var createdNew = false
    /** The note's state before this editing session was kept as a version (Plan-B Pro #16). */
    private var sessionSnapshotTaken = false

    private fun newId() = UUID.randomUUID().toString()

    /** Rich blocks (Plan-B Pro #15, #17–#20, #23); see [RichEditor]. */
    internal val rich = RichEditor(
        object : RichEditor.Host {
            override val scope get() = viewModelScope
            override val state get() = _state.value
            override fun edit(ownId: String?, transform: (NoteEditorState) -> NoteEditorState) {
                val own = ownId?.let { id -> transform(_state.value).blocks.firstOrNull { it.id == id }?.value }
                edited(ownId, own, transform)
            }
            override fun update(transform: (NoteEditorState) -> NoteEditorState) = _state.update(transform)
            override fun message(message: RichMessage) {
                _events.tryEmit(NoteEditorEvent.Rich(message))
            }
        },
        attachments, textRecognition, handwriting, speech, picked, ::newId,
    )

    private var started = false

    /**
     * Starts loading. [defaultNotebookTitle] is the localized name used only when
     * a new note needs a notebook and none exists yet.
     */
    fun start(defaultNotebookTitle: String) {
        if (started) return
        started = true
        viewModelScope.launch { load(defaultNotebookTitle) }
    }

    init {
        viewModelScope.launch { edits.debounce(DRAFT_DELAY_MS).collect { saveDraft() } }
        viewModelScope.launch { edits.debounce(AUTOSAVE_DELAY_MS).collect { save() } }
        vault?.let { v ->
            // The vault closed (App lock, long in the background): hide the body of a locked note.
            viewModelScope.launch {
                v.unlocked.collect { open ->
                    val s = _state.value
                    if (!open && s.locked && !s.needsUnlock && !s.loading) {
                        dirty = false
                        _state.update { it.copy(blocks = emptyList(), needsUnlock = true, draft = null, saveStatus = SaveStatus.SAVED) }
                    }
                }
            }
            viewModelScope.launch { v.biometricEnabled.collect { on -> _state.update { it.copy(fingerprint = on) } } }
        }
    }

    private suspend fun load(defaultNotebookTitle: String) {
        runCatchingSafely {
            var id = route.noteId
            if (id == 0L) {
                // After process death the route still says "new note": reuse the one already created.
                val created = savedState.get<Long>(KEY_CREATED_ID)
                if (created != null) {
                    id = created
                } else {
                    val notebookId = route.notebookId ?: notes.ensureDefaultNotebook(defaultNotebookTitle)
                    id = notes.saveNote(Note(notebookId = notebookId, sectionId = route.sectionId, title = ""))
                    savedState[KEY_CREATED_ID] = id
                }
                createdNew = true
            }
            val note = notes.getNote(id)
            if (note == null) {
                _state.value = NoteEditorState(loading = false, missing = true)
                return@runCatchingSafely
            }
            val draft = notes.getDraft(id)?.takeIf { it.updatedAt > note.updatedAt }
            val locked = note.locked
            val payload = if (locked) notes.encryptedPayload(id) else null
            val canOpen = !locked || (vault != null && (payload == null || vault.canDecrypt(payload)) && vault.unlocked.value)
            val document = when {
                !canOpen -> null
                locked -> notes.lockedContent(id)
                else -> note.document
            }
            val blocks = document?.let { d -> BlockEditing.ensureNotEmpty(d.blocks.map { it.toEditor() }, ::newId) }.orEmpty()
            _state.value = NoteEditorState(
                loading = false,
                noteId = note.id,
                notebookId = note.notebookId,
                sectionId = note.sectionId,
                title = TextFieldValue(note.title),
                blocks = blocks,
                pinned = note.pinned,
                favorite = note.favorite,
                archived = note.archived,
                tags = note.tags,
                draft = draft,
                focusId = if (createdNew) TITLE_FOCUS else null,
                focusVersion = 1,
                locked = locked,
                needsUnlock = !canOpen,
                fingerprint = _state.value.fingerprint,
            )
            rich.observe(note.id)
        }.onFailure { _state.value = NoteEditorState(loading = false, missing = true) }
    }

    private fun NoteBlock.toEditor() = EditorBlock(id, type, TextFieldValue(text), checked, data = data, attachmentId = attachmentId)

    private fun document(s: NoteEditorState = _state.value) = NoteDocument(blocks = s.blocks.map { it.toNoteBlock() })

    /**
     * Applies [transform] and bumps the revision of every block whose value the ViewModel changed,
     * except a plain edit of [ownId] that just takes the field's own [ownValue].
     */
    private fun edited(ownId: String? = null, ownValue: TextFieldValue? = null, transform: (NoteEditorState) -> NoteEditorState) {
        _state.update { old ->
            val new = transform(old)
            new.copy(blocks = withRevisions(old.blocks, new.blocks, ownId, ownValue), saveStatus = SaveStatus.SAVING)
        }
        dirty = true
        edits.tryEmit(Unit)
    }

    private fun withRevisions(old: List<EditorBlock>, new: List<EditorBlock>, ownId: String?, ownValue: TextFieldValue?): List<EditorBlock> {
        if (old === new) return new
        val before = old.associateBy { it.id }
        return new.map { block ->
            val previous = before[block.id] ?: return@map block
            when {
                block.value == previous.value && block.data == previous.data && block.revision == previous.revision -> block
                block.id == ownId && block.value == ownValue -> block.copy(revision = previous.revision)
                else -> block.copy(revision = previous.revision + 1)
            }
        }
    }

    fun onTitleChange(value: TextFieldValue) {
        val text = value.text.replace('\n', ' ')
        // A pasted newline is replaced, so the field must take this value over.
        val revision = if (text != value.text) 1 else 0
        val changed = text != _state.value.title.text
        if (!changed) {
            _state.update { it.copy(title = value.copy(text = text), titleRevision = it.titleRevision + revision) }
            return
        }
        edited { it.copy(title = value.copy(text = text), titleRevision = it.titleRevision + revision) }
    }

    fun onBlockChange(id: String, value: TextFieldValue) {
        val current = _state.value.blocks.firstOrNull { it.id == id } ?: return
        // A deletion that cut into a link to another note removes the whole link (Plan-B Pro #16).
        NoteLinks.repairDeletion(current.value.text, value.text)?.let { (text, caret) ->
            replaceBlockText(id, TextFieldValue(text, TextRange(caret)))
            return
        }
        if (current.value.text == value.text) {
            // Selection-only change: no save needed.
            _state.update { s -> s.copy(blocks = s.blocks.map { if (it.id == id) it.copy(value = value) else it }) }
            return
        }
        val result = BlockEditing.change(_state.value.blocks, id, value, ::newId)
        edited(ownId = id, ownValue = value) { s ->
            s.copy(
                blocks = result.blocks,
                focusId = result.focusId ?: s.focusId,
                focusVersion = if (result.focusId != null) s.focusVersion + 1 else s.focusVersion,
            )
        }
        result.focusId?.let { focusId -> setCursor(focusId, result.cursor) }
    }

    /** Replaces a block's text and caret from here (the field takes the new value over). */
    private fun replaceBlockText(id: String, value: TextFieldValue) = edited { s ->
        s.copy(blocks = s.blocks.map { if (it.id == id) it.copy(value = value) else it }, focusId = id)
    }

    /** Replaces the "[[" search [query] in block [id] with a link to [noteId] (Plan-B Pro #16). */
    fun insertLink(id: String, query: NoteLinks.Query, cursor: Int, noteId: EntityId, title: String) {
        val block = _state.value.blocks.firstOrNull { it.id == id } ?: return
        if (query.start !in 0..block.value.text.length || cursor !in query.start..block.value.text.length) return
        val (text, caret) = NoteLinks.insert(block.value.text, query, cursor, noteId, title)
        replaceBlockText(id, TextFieldValue(text, TextRange(caret)))
    }

    private fun setCursor(id: String, cursor: Int) {
        _state.update { s ->
            s.copy(
                blocks = s.blocks.map {
                    if (it.id == id) it.copy(value = it.value.copy(selection = TextRange(cursor)), revision = it.revision + 1) else it
                },
            )
        }
    }

    fun onBackspaceAtStart(id: String) = applyJoin(BlockEditing.backspaceAtStart(_state.value.blocks, id))

    /** Toolbar action: joins the focused block onto the previous one (works with any keyboard). */
    fun mergeWithPrevious(id: String) = applyJoin(BlockEditing.mergeWithPrevious(_state.value.blocks, id))

    private fun applyJoin(result: BlockEditing.Result) {
        if (result.blocks == _state.value.blocks) return
        edited { s ->
            s.copy(
                blocks = BlockEditing.ensureNotEmpty(result.blocks, ::newId),
                focusId = result.focusId,
                focusVersion = s.focusVersion + 1,
            )
        }
        result.focusId?.let { setCursor(it, result.cursor) }
    }

    fun onFocus(id: String) {
        _state.update { it.copy(focusId = id) }
    }

    fun setType(id: String, type: BlockType) {
        // A rich block keeps its kind (its content has no text form to convert).
        if (type.isRich || (type != BlockType.DIVIDER && _state.value.blocks.firstOrNull { it.id == id }?.type?.isRich == true)) return
        if (type == BlockType.DIVIDER) {
            edited { s ->
                val index = s.blocks.indexOfFirst { it.id == id }.coerceAtLeast(0)
                val after = EditorBlock(newId(), BlockType.TEXT)
                val list = s.blocks.toMutableList().apply {
                    add(index + 1, EditorBlock(newId(), BlockType.DIVIDER))
                    add(index + 2, after)
                }
                s.copy(blocks = list, focusId = after.id, focusVersion = s.focusVersion + 1)
            }
            return
        }
        edited { s -> s.copy(blocks = s.blocks.map { if (it.id == id) it.copy(type = type) else it }) }
    }

    fun toggleChecked(id: String) = edited { s ->
        s.copy(blocks = s.blocks.map { if (it.id == id) it.copy(checked = !it.checked) else it })
    }

    fun deleteBlock(id: String) = edited { s ->
        val index = s.blocks.indexOfFirst { it.id == id }
        val remaining = BlockEditing.ensureNotEmpty(s.blocks.filterNot { it.id == id }, ::newId)
        val focus = remaining.getOrNull((index - 1).coerceAtLeast(0))?.id
        s.copy(blocks = remaining, focusId = focus, focusVersion = s.focusVersion + 1)
    }

    fun moveBlock(id: String, delta: Int) = edited { s ->
        val list = s.blocks.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val target = i + delta
        if (i < 0 || target !in list.indices) return@edited s
        list.add(target, list.removeAt(i))
        s.copy(blocks = list)
    }

    fun addBlockAtEnd() = edited { s ->
        val block = EditorBlock(newId(), BlockType.TEXT)
        s.copy(blocks = s.blocks + block, focusId = block.id, focusVersion = s.focusVersion + 1)
    }

    fun restoreDraft() {
        val draft = _state.value.draft ?: return
        edited { s ->
            s.copy(
                title = TextFieldValue(draft.title),
                titleRevision = s.titleRevision + 1,
                blocks = BlockEditing.ensureNotEmpty(draft.document.blocks.map { it.toEditor() }, ::newId),
                draft = null,
            )
        }
    }

    fun discardDraft() {
        val id = _state.value.noteId
        _state.update { it.copy(draft = null) }
        viewModelScope.launch { runCatchingSafely { notes.clearDraft(id) } }
    }

    private suspend fun saveDraft() {
        val s = _state.value
        if (s.loading || s.missing || !dirty) return
        runCatchingSafely { notes.saveDraft(s.noteId, s.title.text, document(s)) }
    }

    /** Commits the current content. Safe to call repeatedly (e.g. on ON_STOP). */
    suspend fun save() = saveMutex.withLock {
        val s = _state.value
        if (s.loading || s.missing || s.needsUnlock || !dirty) return@withLock
        dirty = false
        // Version history: the state before this session, then at most one version per 10 minutes.
        history?.let { h ->
            runCatchingSafely { h.snapshot(s.noteId, force = !sessionSnapshotTaken) }
            sessionSnapshotTaken = true
        }
        runCatchingSafely { notes.updateContent(s.noteId, s.title.text.trim(), document(s)) }
            .onSuccess { if (!dirty) _state.update { it.copy(saveStatus = SaveStatus.SAVED) } }
            .onFailure {
                dirty = true
                // Keep the draft so nothing is lost; surface the state to the user.
                runCatchingSafely { notes.saveDraft(s.noteId, s.title.text, document(s)) }
                _state.update { it.copy(saveStatus = SaveStatus.FAILED) }
            }
    }

    fun flush() {
        appScope.launch { save() }
    }

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        runCatchingSafely { block() }.onFailure { _events.tryEmit(NoteEditorEvent.Failed) }
    }

    fun togglePinned() = launchSafely {
        val s = _state.value
        notes.setPinned(s.noteId, !s.pinned)
        _state.update { it.copy(pinned = !s.pinned) }
    }

    fun toggleFavorite() = launchSafely {
        val s = _state.value
        notes.setFavorite(s.noteId, !s.favorite)
        _state.update { it.copy(favorite = !s.favorite) }
    }

    fun setArchived(archived: Boolean) = launchSafely {
        save()
        val id = _state.value.noteId
        notes.setArchived(id, archived)
        // Archiving also unpins the note in the repository; keep the pin icon in step.
        val stored = notes.getNote(id)
        _state.update { it.copy(archived = archived, pinned = stored?.pinned ?: (it.pinned && !archived)) }
    }

    fun setTags(raw: String) = launchSafely {
        val tags = raw.split(',', '،').map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.distinct().map { Tag(name = it) }
        notes.setTags(_state.value.noteId, tags)
        _state.update { it.copy(tags = notes.getNote(it.noteId)?.tags ?: tags) }
    }

    fun moveTo(notebookId: EntityId, sectionId: EntityId?) = launchSafely {
        notes.moveNote(_state.value.noteId, notebookId, sectionId)
        _state.update { it.copy(notebookId = notebookId, sectionId = sectionId) }
    }

    fun duplicate(copySuffix: String) = launchSafely {
        save()
        _events.tryEmit(NoteEditorEvent.Duplicated(notes.duplicateNote(_state.value.noteId, copySuffix)))
    }

    fun saveAsTemplate() = launchSafely {
        // A locked note's body never leaves it (no templates, no exports).
        if (_state.value.locked) return@launchSafely
        save()
        val note = notes.getNote(_state.value.noteId) ?: return@launchSafely
        templates.saveNoteAsTemplate(note)
        _events.tryEmit(NoteEditorEvent.TemplateSaved)
    }

    fun delete() = launchSafely {
        notes.deleteNote(_state.value.noteId)
        _state.update { it.copy(missing = true) }
        _events.tryEmit(NoteEditorEvent.Deleted)
    }

    fun export(uri: Uri, markdown: Boolean) {
        if (_state.value.locked) return
        viewModelScope.launch {
            save()
            val s = _state.value
            runCatchingSafely {
                val doc = document(s)
                // A single file has no room for the attachments: rich blocks name their files.
                val attachmentLinks = { id: Long -> s.attachments[id]?.let { MarkdownAttachment(null, it.displayName.ifBlank { it.fileName }, it.transcript, it.ocrText) } }
                // Links read as the linked notes' current titles; Markdown links point at their .md files.
                val titles = links?.refs(NoteLinks.targets(doc))?.filterValues { !it.trashed }?.mapValues { it.value.title }.orEmpty()
                val text = if (markdown) {
                    Markdown.export(s.title.text, doc, attachments = attachmentLinks) { id, title -> titles[id]?.let { fileName(it, "md", title) } }
                } else {
                    Markdown.plainText(s.title.text, doc) { titles[it] }
                }
                files.writeText(uri, text)
            }.onSuccess { _events.tryEmit(NoteEditorEvent.Exported) }
                .onFailure { _events.tryEmit(NoteEditorEvent.ExportFailed) }
        }
    }

    fun sectionsFor(notebookId: EntityId) = notes.observeSections(notebookId)

    // region Locked notes (Plan-B Pro #36)

    /** "Lock note": asks for a passphrase first when none is set up or the vault is closed. */
    fun lockNote() = launchSafely {
        val v = vault ?: return@launchSafely
        when {
            !v.setUpDone() -> _state.update { it.copy(passphraseDialog = PassphraseDialog.SETUP) }
            !v.unlocked.value -> _state.update { it.copy(passphraseDialog = PassphraseDialog.UNLOCK, wrongPassphrase = false) }
            else -> {
                save()
                notes.lockNote(_state.value.noteId)
                _state.update { it.copy(locked = true, draft = null) }
                _events.tryEmit(NoteEditorEvent.Locked)
            }
        }
    }

    fun removeLock() = launchSafely {
        if (_state.value.needsUnlock) return@launchSafely
        save()
        notes.removeLock(_state.value.noteId)
        _state.update { it.copy(locked = false) }
        _events.tryEmit(NoteEditorEvent.LockRemoved)
    }

    fun requestUnlock() = _state.update { it.copy(passphraseDialog = PassphraseDialog.UNLOCK, wrongPassphrase = false) }

    fun dismissPassphrase() = _state.update { it.copy(passphraseDialog = null, wrongPassphrase = false) }

    fun setUpPassphrase(passphrase: CharArray) = busy {
        try {
            vault?.setUp(passphrase)
        } finally {
            passphrase.fill(' ')
        }
        _state.update { it.copy(passphraseDialog = null) }
        lockNote()
    }

    fun unlock(passphrase: CharArray) = busy {
        val v = vault ?: return@busy
        val sample = notes.encryptedPayload(_state.value.noteId)
        val ok = try {
            v.unlock(passphrase, sample)
        } finally {
            passphrase.fill(' ')
        }
        if (ok && (sample == null || v.canDecrypt(sample))) afterUnlock() else _state.update { it.copy(wrongPassphrase = true) }
    }

    /** A Keystore cipher for the fingerprint prompt; null when fingerprint unlock is not set up. */
    suspend fun fingerprintCipher(): Cipher? {
        val iv = vault?.biometricKey()?.iv ?: return null
        return keyStore?.decryptCipher(iv)
    }

    fun unlockWithFingerprint(cipher: Cipher) = busy {
        val v = vault ?: return@busy
        val sample = notes.encryptedPayload(_state.value.noteId)
        if (v.unlockWithBiometric(cipher) && (sample == null || v.canDecrypt(sample))) afterUnlock() else requestUnlock()
    }

    private suspend fun afterUnlock() {
        val s = _state.value
        _state.update { it.copy(passphraseDialog = null, wrongPassphrase = false) }
        if (s.locked && s.needsUnlock) {
            val document = notes.lockedContent(s.noteId)
            _state.update {
                it.copy(
                    needsUnlock = false,
                    blocks = BlockEditing.ensureNotEmpty(document.blocks.map { b -> b.toEditor() }, ::newId),
                    focusVersion = it.focusVersion + 1,
                )
            }
        } else if (!s.locked) {
            lockNote()
        }
    }

    private fun busy(block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        try {
            runCatchingSafely { block() }.onFailure { _events.tryEmit(NoteEditorEvent.Failed) }
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    // endregion

    override fun onCleared() {
        val s = _state.value
        val pendingDirty = dirty
        appScope.launch {
            runCatchingSafely {
                if (pendingDirty && !s.needsUnlock) notes.updateContent(s.noteId, s.title.text.trim(), document(s))
                // Closing a changed note keeps its final state as a version (Plan-B Pro #16).
                if ((pendingDirty || sessionSnapshotTaken) && !s.missing) history?.snapshot(s.noteId, force = true)
                // A note created by opening the editor and left completely empty is discarded.
                if (createdNew && s.title.text.isBlank() && document(s).isBlank() && !s.missing && !s.locked) {
                    notes.discardNote(s.noteId)
                } else if (!s.missing) {
                    // Files of rich blocks deleted while editing (older than a few minutes; the rest at the next start).
                    attachments?.deleteUnused(s.noteId)
                }
            }
        }
    }

    companion object {
        const val TITLE_FOCUS = "title"
        private const val KEY_CREATED_ID = "note_editor_created_id"
        private const val AUTOSAVE_DELAY_MS = 700L
        private const val DRAFT_DELAY_MS = 250L
    }
}
