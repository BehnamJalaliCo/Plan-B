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
import com.behnamjalali.planb.core.data.repository.NoteRepository
import com.behnamjalali.planb.core.data.repository.TemplateRepository
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Markdown
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
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
)

enum class SaveStatus { SAVED, SAVING, FAILED }

data class NoteEditorState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val noteId: EntityId = 0,
    val notebookId: EntityId = 0,
    val sectionId: EntityId? = null,
    val title: TextFieldValue = TextFieldValue(""),
    val blocks: List<EditorBlock> = emptyList(),
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val tags: List<Tag> = emptyList(),
    val saveStatus: SaveStatus = SaveStatus.SAVED,
    val draft: NoteDraft? = null,
    val focusId: String? = null,
    val focusVersion: Int = 0,
)

sealed interface NoteEditorEvent {
    data object Deleted : NoteEditorEvent
    data class Duplicated(val id: EntityId) : NoteEditorEvent
    data object Exported : NoteEditorEvent
    data object ExportFailed : NoteEditorEvent
    data object TemplateSaved : NoteEditorEvent
    data object Failed : NoteEditorEvent
}

/** Pure block-editing rules; kept separate from Android so they are unit-testable. */
object BlockEditing {
    private val continuing = setOf(BlockType.CHECKLIST, BlockType.BULLET, BlockType.NUMBERED, BlockType.QUOTE)

    data class Result(val blocks: List<EditorBlock>, val focusId: String?, val cursor: Int)

    /**
     * Applies an edit to block [id]. Newlines (typed Enter or pasted text) split
     * the block, except in code blocks where newlines are content.
     */
    fun change(blocks: List<EditorBlock>, id: String, value: TextFieldValue, newId: () -> String): Result {
        val index = blocks.indexOfFirst { it.id == id }
        if (index < 0) return Result(blocks, null, 0)
        val block = blocks[index]
        if (block.type == BlockType.CODE || '\n' !in value.text) {
            return Result(blocks.toMutableList().also { it[index] = block.copy(value = value) }, null, 0)
        }
        val parts = value.text.split('\n')
        val first = parts.first()
        // Enter on an empty list item ends the list instead of adding another empty item.
        if (parts.size == 2 && first.isEmpty() && parts[1].isEmpty() && block.type in continuing) {
            val converted = block.copy(type = BlockType.TEXT, value = TextFieldValue(""))
            return Result(blocks.toMutableList().also { it[index] = converted }, block.id, 0)
        }
        val nextType = if (block.type in continuing) block.type else BlockType.TEXT
        val updated = blocks.toMutableList()
        updated[index] = block.copy(value = TextFieldValue(first, TextRange(first.length)))
        val inserted = parts.drop(1).map { EditorBlock(newId(), nextType, TextFieldValue(it)) }
        updated.addAll(index + 1, inserted)
        val focus = inserted.last()
        // Cursor goes to the start of the text that followed the caret.
        return Result(updated, focus.id, 0)
    }

    /** Backspace at the start of a block: merge with the previous text block or remove an empty one. */
    fun backspaceAtStart(blocks: List<EditorBlock>, id: String): Result {
        val index = blocks.indexOfFirst { it.id == id }
        if (index < 0) return Result(blocks, null, 0)
        val block = blocks[index]
        if (block.type != BlockType.TEXT) {
            return Result(blocks.toMutableList().also { it[index] = block.copy(type = BlockType.TEXT) }, block.id, 0)
        }
        if (index == 0) return Result(blocks, null, 0)
        val previous = blocks[index - 1]
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
    savedState: SavedStateHandle,
    private val notes: NoteRepository,
    private val templates: TemplateRepository,
    private val files: DocumentFiles,
    @ApplicationScope private val appScope: CoroutineScope,
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

    private fun newId() = UUID.randomUUID().toString()

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
    }

    private suspend fun load(defaultNotebookTitle: String) {
        runCatchingSafely {
            var id = route.noteId
            if (id == 0L) {
                val notebookId = route.notebookId ?: notes.ensureDefaultNotebook(defaultNotebookTitle)
                id = notes.saveNote(Note(notebookId = notebookId, sectionId = route.sectionId, title = ""))
                createdNew = true
            }
            val note = notes.getNote(id)
            if (note == null) {
                _state.value = NoteEditorState(loading = false, missing = true)
                return@runCatchingSafely
            }
            val draft = notes.getDraft(id)?.takeIf { it.updatedAt > note.updatedAt }
            val blocks = BlockEditing.ensureNotEmpty(note.document.blocks.map { it.toEditor() }, ::newId)
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
            )
        }.onFailure { _state.value = NoteEditorState(loading = false, missing = true) }
    }

    private fun NoteBlock.toEditor() = EditorBlock(id, type, TextFieldValue(text), checked)

    private fun document(s: NoteEditorState = _state.value) =
        NoteDocument(blocks = s.blocks.map { NoteBlock(it.id, it.type, it.value.text, it.checked) })

    private fun edited(transform: (NoteEditorState) -> NoteEditorState) {
        _state.update { transform(it).copy(saveStatus = SaveStatus.SAVING) }
        dirty = true
        edits.tryEmit(Unit)
    }

    fun onTitleChange(value: TextFieldValue) {
        val text = value.text.replace('\n', ' ')
        val changed = text != _state.value.title.text
        if (!changed) {
            _state.update { it.copy(title = value.copy(text = text)) }
            return
        }
        edited { it.copy(title = value.copy(text = text)) }
    }

    fun onBlockChange(id: String, value: TextFieldValue) {
        val current = _state.value.blocks.firstOrNull { it.id == id } ?: return
        if (current.value.text == value.text) {
            // Selection-only change: no save needed.
            _state.update { s -> s.copy(blocks = s.blocks.map { if (it.id == id) it.copy(value = value) else it }) }
            return
        }
        val result = BlockEditing.change(_state.value.blocks, id, value, ::newId)
        edited { s ->
            s.copy(
                blocks = result.blocks,
                focusId = result.focusId ?: s.focusId,
                focusVersion = if (result.focusId != null) s.focusVersion + 1 else s.focusVersion,
            )
        }
        result.focusId?.let { focusId -> setCursor(focusId, result.cursor) }
    }

    private fun setCursor(id: String, cursor: Int) {
        _state.update { s -> s.copy(blocks = s.blocks.map { if (it.id == id) it.copy(value = it.value.copy(selection = TextRange(cursor))) else it }) }
    }

    fun onBackspaceAtStart(id: String) {
        val result = BlockEditing.backspaceAtStart(_state.value.blocks, id)
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
        if (s.loading || s.missing || !dirty) return@withLock
        dirty = false
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
        notes.setArchived(_state.value.noteId, archived)
        _state.update { it.copy(archived = archived) }
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
        viewModelScope.launch {
            save()
            val s = _state.value
            runCatchingSafely {
                val text = if (markdown) Markdown.export(s.title.text, document(s)) else Markdown.plainText(s.title.text, document(s))
                files.writeText(uri, text)
            }.onSuccess { _events.tryEmit(NoteEditorEvent.Exported) }
                .onFailure { _events.tryEmit(NoteEditorEvent.ExportFailed) }
        }
    }

    fun sectionsFor(notebookId: EntityId) = notes.observeSections(notebookId)

    override fun onCleared() {
        val s = _state.value
        val pendingDirty = dirty
        appScope.launch {
            runCatchingSafely {
                if (pendingDirty) notes.updateContent(s.noteId, s.title.text.trim(), document(s))
                // A note created by opening the editor and left completely empty is discarded.
                if (createdNew && s.title.text.isBlank() && document(s).isBlank() && !s.missing) notes.deleteNote(s.noteId)
            }
        }
    }

    companion object {
        const val TITLE_FOCUS = "title"
        private const val AUTOSAVE_DELAY_MS = 700L
        private const val DRAFT_DELAY_MS = 250L
    }
}
