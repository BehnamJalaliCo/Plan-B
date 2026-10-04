package com.behnamjalali.planb.feature.notebooks

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerEmptyState
import com.behnamjalali.planb.core.designsystem.component.PlannerErrorState
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.ui.AccentColorPicker
import com.behnamjalali.planb.core.ui.ConfirmDeleteDialog
import com.behnamjalali.planb.core.ui.PlannerIconPicker
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.PlannerNoteCard
import com.behnamjalali.planb.core.ui.vector

data class NotebooksCallbacks(
    val onOpenNotebook: (EntityId) -> Unit = {},
    val onOpenNote: (EntityId) -> Unit = {},
    val onNewNote: () -> Unit = {},
    val onSaveNotebook: (Notebook) -> Unit = {},
    val onArchiveNotebook: (EntityId, Boolean) -> Unit = { _, _ -> },
    val onDeleteNotebook: (EntityId) -> Unit = {},
    val onMoveNotebook: (EntityId, Int) -> Unit = { _, _ -> },
    val onToggleArchive: () -> Unit = {},
    val onRestoreNote: (EntityId) -> Unit = {},
    val onImport: () -> Unit = {},
)

@Composable
fun NotebooksDestination(
    onOpenNotebook: (EntityId) -> Unit,
    onOpenNote: (EntityId) -> Unit,
    onNewNote: () -> Unit,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    viewModel: NotebooksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val untitled = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_untitled)
    val defaultNotebook = stringResource(com.behnamjalali.planb.core.data.R.string.data_default_notebook)
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importMarkdown(it, untitled, defaultNotebook) }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is NotebooksEvent.OpenNote -> onOpenNote(event.id)
                // Not awaited: a suspended collector would delay the events behind it.
                NotebooksEvent.Imported -> launch { snackbarHostState.showSnackbar(resources.getString(R.string.notebook_imported)) }
                NotebooksEvent.ImportFailed -> snackbarHostState.showSnackbar(resources.getString(R.string.notebook_import_failed))
                NotebooksEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(com.behnamjalali.planb.core.ui.R.string.ui_error_generic))
            }
        }
    }
    NotebooksScreen(
        state = state,
        contentPadding = contentPadding,
        callbacks = NotebooksCallbacks(
            onOpenNotebook = onOpenNotebook,
            onOpenNote = onOpenNote,
            onNewNote = onNewNote,
            onSaveNotebook = viewModel::saveNotebook,
            onArchiveNotebook = viewModel::setNotebookArchived,
            onDeleteNotebook = viewModel::deleteNotebook,
            onMoveNotebook = viewModel::move,
            onToggleArchive = viewModel::toggleArchive,
            onRestoreNote = viewModel::restoreNote,
            onImport = { importer.launch(arrayOf("text/markdown", "text/plain", "text/*")) },
        ),
    )
}

@Composable
fun NotebooksScreen(state: NotebooksUiState, callbacks: NotebooksCallbacks, contentPadding: PaddingValues = PaddingValues()) {
    var editing by remember { mutableStateOf<Notebook?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(
            title = stringResource(R.string.notebooks_title),
            actions = {
                PlannerIconButton(Icons.Rounded.FileOpen, stringResource(R.string.notebook_import_markdown), callbacks.onImport)
                PlannerIconButton(
                    Icons.Rounded.CreateNewFolder,
                    stringResource(R.string.notebooks_new),
                    { editing = Notebook(title = "", color = AccentColor.entries[state.notebooks.size % AccentColor.entries.size]) },
                )
                PlannerIconButton(Icons.AutoMirrored.Rounded.NoteAdd, stringResource(R.string.notebooks_new_note), callbacks.onNewNote)
            },
        )
        when {
            state.loading -> PlannerLoadingState()
            state.error -> PlannerErrorState(stringResource(R.string.notebooks_error))
            else -> LazyColumn(
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = contentPadding.calculateBottomPadding() + 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (state.pinned.isNotEmpty()) {
                    item(key = "pinned_h") { PlannerSectionHeader(stringResource(R.string.notebooks_pinned)) }
                    item(key = "pinned") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            items(state.pinned, key = { it.id }) { note ->
                                PlannerNoteCard(note, Modifier.width(240.dp), onClick = { callbacks.onOpenNote(note.id) })
                            }
                        }
                    }
                }
                item(key = "all_h") {
                    PlannerSectionHeader(stringResource(R.string.notebooks_all), trailing = PlannerLocals.numbers.format(state.notebooks.size))
                }
                if (state.notebooks.isEmpty()) {
                    item(key = "empty") {
                        PlannerEmptyState(
                            icon = Icons.Rounded.CreateNewFolder,
                            title = stringResource(R.string.notebooks_empty_title),
                            message = stringResource(R.string.notebooks_empty),
                            actionLabel = stringResource(R.string.notebooks_new_note),
                            onAction = callbacks.onNewNote,
                        )
                    }
                }
                items(state.notebooks, key = { "nb${it.id}" }) { nb ->
                    NotebookRow(
                        notebook = nb,
                        onClick = { callbacks.onOpenNotebook(nb.id) },
                        onEdit = { editing = nb },
                        onArchive = { callbacks.onArchiveNotebook(nb.id, true) },
                        onDelete = { deleting = nb.id },
                        onMove = { callbacks.onMoveNotebook(nb.id, it) },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (state.recent.isNotEmpty()) {
                    item(key = "recent_h") { PlannerSectionHeader(stringResource(R.string.notebooks_recent)) }
                    items(state.recent, key = { "r${it.id}" }) { note ->
                        PlannerNoteCard(note, onClick = { callbacks.onOpenNote(note.id) })
                    }
                }
                item(key = "archive_toggle") {
                    PlannerChip(
                        stringResource(R.string.notebooks_archived),
                        state.showArchive,
                        callbacks.onToggleArchive,
                        icon = Icons.Rounded.Archive,
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
                if (state.showArchive) {
                    item(key = "arch_nb_h") { PlannerSectionHeader(stringResource(R.string.notebooks_archived_notebooks)) }
                    items(state.archivedNotebooks, key = { "anb${it.id}" }) { nb ->
                        NotebookRow(nb, onClick = { callbacks.onOpenNotebook(nb.id) }, onEdit = { editing = nb },
                            onArchive = { callbacks.onArchiveNotebook(nb.id, false) }, onDelete = { deleting = nb.id }, onMove = null, archived = true)
                    }
                    item(key = "arch_notes_h") { PlannerSectionHeader(stringResource(R.string.notebooks_archived_notes)) }
                    items(state.archivedNotes, key = { "an${it.id}" }) { note ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlannerNoteCard(note, Modifier.weight(1f), onClick = { callbacks.onOpenNote(note.id) })
                            PlannerIconButton(Icons.Rounded.Unarchive, stringResource(R.string.note_unarchive), { callbacks.onRestoreNote(note.id) })
                        }
                    }
                }
            }
        }
    }
    editing?.let { nb ->
        NotebookDialog(nb, onDismiss = { editing = null }, onSave = {
            callbacks.onSaveNotebook(it)
            editing = null
        })
    }
    deleting?.let { id ->
        ConfirmDeleteDialog(
            stringResource(R.string.notebook_delete),
            stringResource(R.string.notebook_delete_confirm),
            { deleting = null },
            {
                callbacks.onDeleteNotebook(id)
                deleting = null
            },
        )
    }
}

@Composable
private fun NotebookRow(
    notebook: Notebook,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onMove: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    archived: Boolean = false,
) {
    val tones = PlanBTheme.colors.accent(notebook.color)
    var menu by remember { mutableStateOf(false) }
    PlannerCard(modifier.fillMaxWidth(), onClick = onClick, onLongClick = { menu = true }, contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(Radius.sm), color = tones.container) {
                Icon(notebook.icon.vector, contentDescription = null, tint = tones.onContainer, modifier = Modifier.padding(Spacing.sm).size(IconSize.md))
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(notebook.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.notebooks_note_count, notebook.noteCount, PlannerLocals.numbers.format(notebook.noteCount)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                PlannerIconButton(Icons.Rounded.MoreVert, stringResource(com.behnamjalali.planb.core.ui.R.string.ui_more), { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.notebook_rename)) }, onClick = { menu = false; onEdit() })
                    if (onMove != null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.notebook_move_up)) }, onClick = { menu = false; onMove(-1) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.notebook_move_down)) }, onClick = { menu = false; onMove(1) })
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(if (archived) R.string.notebook_restore else R.string.notebook_archive)) },
                        onClick = { menu = false; onArchive() },
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.notebook_delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun NotebookDialog(notebook: Notebook, onDismiss: () -> Unit, onSave: (Notebook) -> Unit) {
    var title by rememberSaveable { mutableStateOf(notebook.title) }
    var color by rememberSaveable { mutableStateOf(notebook.color) }
    var icon by rememberSaveable { mutableStateOf(notebook.icon) }
    PlannerDialog(
        title = stringResource(if (notebook.id == 0L) R.string.notebooks_new else R.string.notebook_rename),
        onDismiss = onDismiss,
        confirmLabel = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_save),
        confirmEnabled = title.isNotBlank(),
        onConfirm = { onSave(notebook.copy(title = title.trim(), color = color, icon = icon)) },
    ) {
        PlannerTextField(title, { title = it }, stringResource(R.string.notebook_name))
        Text(stringResource(R.string.notebook_color), style = MaterialTheme.typography.labelLarge)
        AccentColorPicker(color, { color = it })
        Text(stringResource(R.string.notebook_icon), style = MaterialTheme.typography.labelLarge)
        PlannerIconPicker(icon, { icon = it }, color)
    }
}
