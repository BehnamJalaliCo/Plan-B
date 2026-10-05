package com.behnamjalali.planb.feature.notebooks

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatListNumberedRtl
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.ui.ConfirmDeleteDialog
import com.behnamjalali.planb.core.ui.DeviceAuth
import com.behnamjalali.planb.core.ui.PassphraseSetupDialog
import com.behnamjalali.planb.core.ui.PassphraseUnlockDialog
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.findFragmentActivity
import com.behnamjalali.planb.core.ui.rememberProGuard
import com.behnamjalali.planb.feature.notebooks.knowledge.NoteKnowledgeViewModel
import androidx.activity.compose.BackHandler
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.feature.notebooks.knowledge.LocalNoteLinkRefs
import com.behnamjalali.planb.feature.notebooks.knowledge.NoteKnowledgeUi
import com.behnamjalali.planb.feature.notebooks.knowledge.NoteLinkAssist
import com.behnamjalali.planb.feature.notebooks.knowledge.linkedIds
import com.behnamjalali.planb.feature.notebooks.knowledge.noteLinkSections
import com.behnamjalali.planb.feature.notebooks.knowledge.rememberNoteLinkTransformation
import com.behnamjalali.planb.feature.notebooks.writing.WritingModeScreen
import androidx.compose.runtime.CompositionLocalProvider
import com.behnamjalali.planb.core.designsystem.component.PlannerEmptyState
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
fun blockTypeLabel(type: BlockType): String = stringResource(
    when (type) {
        BlockType.TEXT -> R.string.note_block_text
        BlockType.HEADING -> R.string.note_block_heading
        BlockType.CHECKLIST -> R.string.note_block_checklist
        BlockType.BULLET -> R.string.note_block_bullet
        BlockType.NUMBERED -> R.string.note_block_numbered
        BlockType.QUOTE -> R.string.note_block_quote
        BlockType.DIVIDER -> R.string.note_block_divider
        BlockType.CODE -> R.string.note_block_code
    },
)

private fun blockIcon(type: BlockType, rtl: Boolean) = when (type) {
    BlockType.TEXT -> Icons.AutoMirrored.Rounded.Subject
    BlockType.HEADING -> Icons.Rounded.Title
    BlockType.CHECKLIST -> Icons.Rounded.CheckBox
    BlockType.BULLET -> Icons.AutoMirrored.Rounded.FormatListBulleted
    // The numbered-list icon has no auto-mirrored variant; it has a separate RTL glyph.
    BlockType.NUMBERED -> if (rtl) Icons.Rounded.FormatListNumberedRtl else Icons.Rounded.FormatListNumbered
    BlockType.QUOTE -> Icons.Rounded.FormatQuote
    BlockType.DIVIDER -> Icons.Rounded.HorizontalRule
    BlockType.CODE -> Icons.Rounded.Code
}

@Composable
fun NoteEditorDestination(
    onClose: () -> Unit,
    onOpenNote: (EntityId) -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel(),
    /** Opens the note's activity history (Plan-B Pro); null hides the menu item. */
    onOpenActivity: ((EntityId) -> Unit)? = null,
    /** Opens a linked note on top of this one (Plan-B Pro #16); defaults to [onOpenNote]. */
    onOpenLinkedNote: ((EntityId) -> Unit)? = null,
    /** Opens the note's version history (Plan-B Pro #16); null hides the menu item. */
    onOpenHistory: ((EntityId) -> Unit)? = null,
    knowledgeViewModel: NoteKnowledgeViewModel = hiltViewModel(),
) {
    val defaultNotebook = stringResource(com.behnamjalali.planb.core.data.R.string.data_default_notebook)
    LaunchedEffect(viewModel) { viewModel.start(defaultNotebook) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notebooks by viewModel.notebooks.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    // Commit edits whenever the screen stops (backgrounding, navigation, process death risk).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.flush() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                NoteEditorEvent.Deleted -> onClose()
                is NoteEditorEvent.Duplicated -> onOpenNote(event.id)
                NoteEditorEvent.Exported -> snackbar.showSnackbar(resources.getString(R.string.note_exported))
                NoteEditorEvent.ExportFailed -> snackbar.showSnackbar(resources.getString(R.string.note_export_failed))
                NoteEditorEvent.TemplateSaved -> snackbar.showSnackbar(resources.getString(R.string.note_template_saved))
                NoteEditorEvent.Locked -> snackbar.showSnackbar(resources.getString(R.string.note_locked_done))
                NoteEditorEvent.LockRemoved -> snackbar.showSnackbar(resources.getString(R.string.note_lock_removed))
                NoteEditorEvent.Failed -> snackbar.showSnackbar(resources.getString(com.behnamjalali.planb.core.ui.R.string.ui_error_generic))
            }
        }
    }
    LaunchedEffect(state.missing) {
        if (state.missing) {
            snackbar.showSnackbar(resources.getString(R.string.note_not_found))
            onClose()
        }
    }
    val markdownExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        uri?.let { viewModel.export(it, markdown = true) }
    }
    val textExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { viewModel.export(it, markdown = false) }
    }
    val copySuffix by rememberUpdatedState(stringResource(R.string.note_copy_suffix))
    val fallbackName by rememberUpdatedState(stringResource(R.string.note_file_name_fallback))
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnActivity by rememberUpdatedState(onOpenActivity)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fingerprintTitle = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_passphrase_unlock_title)
    val cancelLabel = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_cancel)
    // One actions object for the screen's lifetime: rebuilding it on every keystroke would make
    // every block row recompose.
    val actions = remember(viewModel) {
        NoteEditorActions(
            onBack = {
                viewModel.flush()
                currentOnClose()
            },
            onTitle = viewModel::onTitleChange,
            onBlock = viewModel::onBlockChange,
            onBackspaceAtStart = viewModel::onBackspaceAtStart,
            onMergeWithPrevious = viewModel::mergeWithPrevious,
            onFocus = viewModel::onFocus,
            onType = viewModel::setType,
            onToggleChecked = viewModel::toggleChecked,
            onDeleteBlock = viewModel::deleteBlock,
            onMoveBlock = viewModel::moveBlock,
            onAddBlock = viewModel::addBlockAtEnd,
            onRestoreDraft = viewModel::restoreDraft,
            onDiscardDraft = viewModel::discardDraft,
            onPin = viewModel::togglePinned,
            onFavorite = viewModel::toggleFavorite,
            onArchive = viewModel::setArchived,
            onTags = viewModel::setTags,
            onMove = viewModel::moveTo,
            onDuplicate = { viewModel.duplicate(copySuffix) },
            onSaveTemplate = viewModel::saveAsTemplate,
            onDelete = viewModel::delete,
            onExportMarkdown = { markdownExport.launch(fileName(viewModel.state.value.title.text, "md", fallbackName)) },
            onExportText = { textExport.launch(fileName(viewModel.state.value.title.text, "txt", fallbackName)) },
            onLock = viewModel::lockNote,
            onRemoveLock = viewModel::removeLock,
            onRequestUnlock = viewModel::requestUnlock,
            onActivity = if (onOpenActivity != null) ({ currentOnActivity?.invoke(viewModel.state.value.noteId) }) else null,
        )
    }
    val passphraseDialog = state.passphraseDialog
    when (passphraseDialog) {
        PassphraseDialog.SETUP -> PassphraseSetupDialog(onConfirm = viewModel::setUpPassphrase, onDismiss = viewModel::dismissPassphrase, busy = state.busy)
        PassphraseDialog.UNLOCK -> PassphraseUnlockDialog(
            onUnlock = viewModel::unlock,
            onDismiss = viewModel::dismissPassphrase,
            wrong = state.wrongPassphrase,
            busy = state.busy,
            onFingerprint = if (state.fingerprint && DeviceAuth.canUseStrongBiometric(context)) {
                {
                    scope.launch {
                        val activity = context.findFragmentActivity() ?: return@launch
                        val cipher = viewModel.fingerprintCipher() ?: return@launch
                        DeviceAuth.authenticate(activity, fingerprintTitle, cancelLabel, cipher) { it?.let(viewModel::unlockWithFingerprint) }
                    }
                }
            } else {
                null
            },
        )
        null -> Unit
    }
    // Plan-B Pro notes knowledge (#16, #24): links, backlinks, history and writing mode.
    LaunchedEffect(state.noteId) { knowledgeViewModel.bind(state.noteId) }
    val linkIds = remember(state.blocks) { linkedIds(state.blocks.map { it.value.text }) }
    LaunchedEffect(linkIds) { knowledgeViewModel.setLinkIds(linkIds) }
    val refs by knowledgeViewModel.refs.collectAsStateWithLifecycle()
    val backlinks by knowledgeViewModel.backlinks.collectAsStateWithLifecycle()
    val suggestions by knowledgeViewModel.suggestions.collectAsStateWithLifecycle()
    var writingMode by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = writingMode) { writingMode = false }
    val openLinked by rememberUpdatedState(onOpenLinkedNote ?: onOpenNote)
    val openHistory by rememberUpdatedState(onOpenHistory)
    val knowledge = NoteKnowledgeUi(
        refs = refs,
        backlinks = backlinks,
        suggestionsFor = suggestions.query,
        suggestions = suggestions.notes,
        onSearch = knowledgeViewModel::search,
        onInsertLink = remember(viewModel) { { blockId, query, cursor, note -> viewModel.insertLink(blockId, query, cursor, note.id, note.title) } },
        onOpenNote = remember(viewModel) {
            { id ->
                viewModel.flush()
                openLinked(id)
            }
        },
        writingMode = writingMode,
        onWritingMode = { writingMode = it },
        onOpenHistory = if (onOpenHistory != null) {
            remember(viewModel) {
                {
                    viewModel.flush()
                    openHistory?.invoke(viewModel.state.value.noteId)
                }
            }
        } else {
            null
        },
    )
    NoteEditorScreen(
        state = state,
        notebooks = notebooks,
        snackbarHostState = snackbar,
        actions = actions,
        knowledge = knowledge,
    )
}

/** Safe file name from a (possibly Persian) title; falls back to the localized [fallback]. */
internal fun fileName(title: String, extension: String, fallback: String): String {
    val cleaned = title.trim().replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), " ").replace(Regex("\\s+"), " ").take(60).trim()
    return "${cleaned.ifBlank { fallback }}.$extension"
}

data class NoteEditorActions(
    val onBack: () -> Unit = {},
    val onTitle: (TextFieldValue) -> Unit = {},
    val onBlock: (String, TextFieldValue) -> Unit = { _, _ -> },
    val onBackspaceAtStart: (String) -> Unit = {},
    val onMergeWithPrevious: (String) -> Unit = {},
    val onFocus: (String) -> Unit = {},
    val onType: (String, BlockType) -> Unit = { _, _ -> },
    val onToggleChecked: (String) -> Unit = {},
    val onDeleteBlock: (String) -> Unit = {},
    val onMoveBlock: (String, Int) -> Unit = { _, _ -> },
    val onAddBlock: () -> Unit = {},
    val onRestoreDraft: () -> Unit = {},
    val onDiscardDraft: () -> Unit = {},
    val onPin: () -> Unit = {},
    val onFavorite: () -> Unit = {},
    val onArchive: (Boolean) -> Unit = {},
    val onTags: (String) -> Unit = {},
    val onMove: (EntityId, EntityId?) -> Unit = { _, _ -> },
    val onDuplicate: () -> Unit = {},
    val onSaveTemplate: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onExportMarkdown: () -> Unit = {},
    val onExportText: () -> Unit = {},
    val onLock: () -> Unit = {},
    val onRemoveLock: () -> Unit = {},
    val onRequestUnlock: () -> Unit = {},
    val onActivity: (() -> Unit)? = null,
)

@Composable
fun NoteEditorScreen(
    state: NoteEditorState,
    notebooks: List<Notebook>,
    snackbarHostState: SnackbarHostState,
    actions: NoteEditorActions,
    /** Plan-B Pro links, history and writing mode (#16, #24); off by default. */
    knowledge: NoteKnowledgeUi = NoteKnowledgeUi(),
) {
    if (knowledge.writingMode && !state.loading && !state.needsUnlock) {
        CompositionLocalProvider(LocalNoteLinkRefs provides knowledge.refs) { WritingModeScreen(state, actions, knowledge) }
        return
    }
    var menu by remember { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val guard = rememberProGuard()
    val focusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    fun requester(id: String) = focusRequesters.getOrPut(id) { FocusRequester() }
    val listState = rememberLazyListState()
    // The block toolbar acts on body blocks only; while the title has focus it is disabled.
    var titleFocused by remember { mutableStateOf(false) }

    LaunchedEffect(state.focusVersion) {
        val id = state.focusId ?: return@LaunchedEffect
        // A block just added below the last visible line is not composed yet, so its focus
        // requester is not attached: scroll it into the composed range first, then focus it.
        val keys = buildList {
            if (state.draft != null) add("draft")
            add(NoteEditorViewModel.TITLE_FOCUS)
            if (state.tags.isNotEmpty()) add("tags")
            state.blocks.forEach { add(it.id) }
        }
        repeat(FOCUS_ATTEMPTS) {
            val layout = listState.layoutInfo
            if (layout.visibleItemsInfo.any { it.key == id }) {
                if (runCatching { requester(id).requestFocus() }.isSuccess) return@LaunchedEffect
            } else {
                val index = keys.indexOf(id)
                if (index < 0) return@LaunchedEffect
                val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                if (index > lastVisible && lastVisible >= 0) {
                    listState.scrollBy(layout.viewportSize.height / 3f)
                } else {
                    listState.scrollToItem(index)
                }
            }
            withFrameNanos { }
        }
    }

    CompositionLocalProvider(LocalNoteLinkRefs provides knowledge.refs) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PlannerTopBar(
                title = "",
                subtitle = stringResource(
                    when (state.saveStatus) {
                        SaveStatus.SAVED -> R.string.note_saved
                        SaveStatus.SAVING -> R.string.note_saving
                        SaveStatus.FAILED -> R.string.note_save_failed
                    },
                ),
                onBack = actions.onBack,
                actions = {
                    PlannerIconButton(
                        if (state.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        stringResource(if (state.pinned) R.string.note_unpin else R.string.note_pin),
                        actions.onPin,
                        tint = if (state.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PlannerIconButton(
                        if (state.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        stringResource(if (state.favorite) R.string.note_unfavorite else R.string.note_favorite),
                        actions.onFavorite,
                        tint = if (state.favorite) PlanBTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box {
                        PlannerIconButton(Icons.Rounded.MoreVert, stringResource(com.behnamjalali.planb.core.ui.R.string.ui_more), { menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            buildList {
                                add(R.string.note_tags to { dialog = "tags" })
                                add(R.string.note_move to { dialog = "move" })
                                add(R.string.note_duplicate to actions.onDuplicate)
                                // A locked note's body is never exported or copied into a template.
                                if (!state.locked) {
                                    add(R.string.note_export_markdown to actions.onExportMarkdown)
                                    add(R.string.note_export_text to actions.onExportText)
                                    add(R.string.note_save_template to actions.onSaveTemplate)
                                    add(R.string.note_lock to { guard.run(ProFeature.APP_LOCK, actions.onLock) })
                                } else if (!state.needsUnlock) {
                                    add(R.string.note_remove_lock to actions.onRemoveLock)
                                }
                                actions.onActivity?.let { open -> add(R.string.note_activity to { guard.run(ProFeature.TRASH_HISTORY, open) }) }
                                if (!state.needsUnlock) add(R.string.writing_mode to { guard.run(ProFeature.FOCUS_WRITING) { knowledge.onWritingMode(true) } })
                                knowledge.onOpenHistory?.let { open -> if (!state.locked) add(R.string.history_title to { guard.run(ProFeature.NOTE_LINKS, open) }) }
                                add((if (state.archived) R.string.note_unarchive else R.string.note_archive) to { actions.onArchive(!state.archived) })
                                add(R.string.note_delete to { dialog = "delete" })
                            }.forEach { (label, action) ->
                                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                                    menu = false
                                    action()
                                })
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            val focusedIndex = if (titleFocused) -1 else state.blocks.indexOfFirst { it.id == state.focusId }
            val focusedBlock = state.blocks.getOrNull(focusedIndex)
            Column(Modifier.navigationBarsPadding().imePadding()) {
                // The "[[" note picker and "Open link" (Plan-B Pro #16).
                if (!state.needsUnlock) NoteLinkAssist(focusedBlock?.id, focusedBlock?.value, knowledge)
                BlockToolbar(
                    focused = focusedBlock,
                    canMerge = focusedIndex > 0,
                    enabled = !titleFocused && !state.needsUnlock,
                    actions = actions,
                    modifier = Modifier,
                )
            }
        },
    ) { padding ->
        if (state.loading) {
            PlannerLoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val textColor = MaterialTheme.colorScheme.onSurface
        val linkIds = remember(state.blocks) { linkedIds(state.blocks.map { it.value.text }) }
        val isPro = LocalProAccess.current.isPro
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            if (state.draft != null) {
                item(key = "draft") {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(Radius.md)) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text(stringResource(R.string.note_draft_found), style = MaterialTheme.typography.bodyMedium)
                            Row {
                                TextButton(onClick = actions.onRestoreDraft) { Text(stringResource(R.string.note_draft_restore)) }
                                TextButton(onClick = actions.onDiscardDraft) { Text(stringResource(R.string.note_draft_discard)) }
                            }
                        }
                    }
                }
            }
            if (state.needsUnlock) {
                item(key = "locked") {
                    PlannerEmptyState(
                        icon = Icons.Rounded.Lock,
                        title = state.title.text.ifBlank { stringResource(R.string.note_locked_title) },
                        message = stringResource(R.string.note_locked_message),
                        actionLabel = stringResource(R.string.note_unlock),
                        onAction = actions.onRequestUnlock,
                    )
                }
                return@LazyColumn
            }
            item(key = NoteEditorViewModel.TITLE_FOCUS) {
                val titleHint = stringResource(R.string.note_title_hint)
                // The field owns its value while typing; the ViewModel's copy only wins when it
                // changed the title itself (see NoteEditorState.titleRevision).
                var title by remember(state.titleRevision) { mutableStateOf(state.title) }
                BasicTextField(
                    value = title,
                    onValueChange = {
                        title = it.copy(text = it.text.replace('\n', ' '))
                        actions.onTitle(it)
                    },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = textColor),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.md)
                        .focusRequester(requester(NoteEditorViewModel.TITLE_FOCUS))
                        .onFocusChanged { titleFocused = it.isFocused }
                        .semantics { contentDescription = titleHint },
                    decorationBox = { inner ->
                        if (title.text.isEmpty()) {
                            Text(titleHint, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        inner()
                    },
                )
            }
            if (state.tags.isNotEmpty()) {
                item(key = "tags") {
                    Text(
                        state.tags.joinToString("  ") { "#${it.name}" },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                }
            }
            itemsIndexed(state.blocks, key = { _, b -> b.id }) { index, block ->
                val number = if (block.type == BlockType.NUMBERED) {
                    state.blocks.subList(0, index + 1).takeLastWhile { it.type == BlockType.NUMBERED }.size
                } else {
                    0
                }
                BlockRow(block, number, requester(block.id), actions)
            }
            item(key = "add") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp),
                ) {
                    TextButton(onClick = actions.onAddBlock) { Text(stringResource(R.string.note_add_block)) }
                }
            }
            noteLinkSections(knowledge, linkIds, isPro)
        }
    }
    }

    when (dialog) {
        "delete" -> ConfirmDeleteDialog(
            stringResource(R.string.note_delete),
            stringResource(R.string.note_delete_confirm),
            { dialog = null },
            {
                dialog = null
                actions.onDelete()
            },
        )
        "tags" -> {
            var text by rememberSaveable { mutableStateOf(state.tags.joinToString(", ") { it.name }) }
            PlannerDialog(
                title = stringResource(R.string.note_tags),
                onDismiss = { dialog = null },
                confirmLabel = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_save),
                onConfirm = {
                    actions.onTags(text)
                    dialog = null
                },
            ) {
                PlannerTextField(text, { text = it }, stringResource(R.string.note_tags_hint))
            }
        }
        "move" -> PlannerDialog(
            title = stringResource(R.string.note_move),
            onDismiss = { dialog = null },
            confirmLabel = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_cancel),
            dismissLabel = "",
            onConfirm = { dialog = null },
        ) {
            notebooks.forEach { nb ->
                TextButton(onClick = {
                    actions.onMove(nb.id, null)
                    dialog = null
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(nb.title, modifier = Modifier.fillMaxWidth(), color = if (nb.id == state.notebookId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun BlockRow(block: EditorBlock, number: Int, focusRequester: FocusRequester, actions: NoteEditorActions) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    if (block.type == BlockType.DIVIDER) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) actions.onFocus(block.id) },
            contentAlignment = Alignment.Center,
        ) {
            HorizontalDivider(color = scheme.outlineVariant)
        }
        return
    }
    val baseStyle: TextStyle = when (block.type) {
        BlockType.HEADING -> typography.titleLarge
        BlockType.CODE -> typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        BlockType.QUOTE -> typography.bodyLarge.copy(color = scheme.onSurfaceVariant)
        else -> typography.bodyLarge
    }
    val style = baseStyle.copy(
        color = if (block.type == BlockType.QUOTE) scheme.onSurfaceVariant else if (block.checked) scheme.outline else scheme.onSurface,
        textDecoration = if (block.type == BlockType.CHECKLIST && block.checked) TextDecoration.LineThrough else null,
    )
    val hint = stringResource(
        when (block.type) {
            BlockType.HEADING -> R.string.note_heading_hint
            BlockType.CODE -> R.string.note_code_hint
            else -> R.string.note_block_hint
        },
    )
    // The field owns its value while typing (fast typing and IME batch edits never meet a value
    // that is a frame behind); it takes the ViewModel's value only when the ViewModel changed this
    // block itself, which bumps EditorBlock.revision.
    var value by remember(block.id, block.revision) { mutableStateOf(block.value) }
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (block.type == BlockType.CODE) {
                    Modifier.background(scheme.surfaceContainer, RoundedCornerShape(Radius.sm)).padding(Spacing.sm)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = if (block.type == BlockType.CHECKLIST) Alignment.CenterVertically else Alignment.Top,
    ) {
        when (block.type) {
            BlockType.CHECKLIST -> Checkbox(checked = block.checked, onCheckedChange = { actions.onToggleChecked(block.id) })
            BlockType.BULLET -> Text("•", style = typography.bodyLarge, modifier = Modifier.padding(horizontal = Spacing.sm))
            BlockType.NUMBERED -> Text(
                "${PlannerLocals.numbers.format(number)}.",
                style = typography.bodyLarge,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
            BlockType.QUOTE -> Box(
                Modifier
                    .padding(end = Spacing.md, top = Spacing.xxs)
                    .width(3.dp)
                    .height(24.dp)
                    .background(scheme.primary, RoundedCornerShape(Radius.pill)),
            )
            else -> Unit
        }
        BasicTextField(
            value = value,
            onValueChange = {
                value = it
                actions.onBlock(block.id, it)
            },
            textStyle = style,
            cursorBrush = SolidColor(scheme.primary),
            // Links to other notes show as their titles (Plan-B Pro #16).
            visualTransformation = rememberNoteLinkTransformation(value.text),
            keyboardOptions = KeyboardOptions(
                capitalization = if (block.type == BlockType.CODE) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
                autoCorrectEnabled = block.type != BlockType.CODE,
            ),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = Spacing.xs)
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) actions.onFocus(block.id) }
                .onPreviewKeyEvent { event ->
                    val atStart = value.selection.start == 0 && value.selection.end == 0
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && atStart) {
                        actions.onBackspaceAtStart(block.id)
                        true
                    } else {
                        false
                    }
                },
            decorationBox = { inner ->
                Box {
                    if (value.text.isEmpty()) Text(hint, style = style.copy(color = scheme.outline, textDecoration = null))
                    inner()
                }
            },
        )
    }
}

@Composable
private fun BlockToolbar(focused: EditorBlock?, canMerge: Boolean, enabled: Boolean, actions: NoteEditorActions, modifier: Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(BlockType.entries.size) { i ->
                val type = BlockType.entries[i]
                PlannerIconButton(
                    blockIcon(type, rtl),
                    blockTypeLabel(type),
                    { focused?.let { actions.onType(it.id, type) } ?: actions.onAddBlock() },
                    tint = if (focused?.type == type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = enabled,
                )
            }
            item { Spacer(Modifier.width(Spacing.sm)) }
            item {
                // Soft keyboards send nothing for Backspace at the very start of a field, so joining
                // blocks also has a button.
                PlannerIconButton(
                    Icons.AutoMirrored.Rounded.MergeType,
                    stringResource(R.string.note_block_merge),
                    { focused?.let { actions.onMergeWithPrevious(it.id) } },
                    enabled = enabled && focused != null && canMerge,
                )
            }
            item {
                PlannerIconButton(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.note_block_up), { focused?.let { actions.onMoveBlock(it.id, -1) } }, enabled = enabled && focused != null)
            }
            item {
                PlannerIconButton(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.note_block_down), { focused?.let { actions.onMoveBlock(it.id, 1) } }, enabled = enabled && focused != null)
            }
            item {
                PlannerIconButton(Icons.Rounded.DeleteOutline, stringResource(R.string.note_block_delete), { focused?.let { actions.onDeleteBlock(it.id) } }, enabled = enabled && focused != null)
            }
        }
    }
}

private const val FOCUS_ATTEMPTS = 6
