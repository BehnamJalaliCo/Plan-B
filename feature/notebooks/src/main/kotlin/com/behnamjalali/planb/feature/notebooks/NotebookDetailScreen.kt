package com.behnamjalali.planb.feature.notebooks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerEmptyState
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.NotebookSection
import com.behnamjalali.planb.core.ui.ConfirmDeleteDialog
import com.behnamjalali.planb.core.ui.PlannerNoteCard

data class NotebookDetailCallbacks(
    val onBack: () -> Unit = {},
    val onOpenNote: (EntityId) -> Unit = {},
    val onNewNote: (EntityId?) -> Unit = {},
    val onSelectSection: (EntityId?) -> Unit = {},
    val onSaveSection: (NotebookSection) -> Unit = {},
    val onMoveSection: (EntityId, EntityId) -> Unit = { _, _ -> },
    val onDeleteSection: (EntityId) -> Unit = {},
)

@Composable
fun NotebookDetailDestination(
    onBack: () -> Unit,
    onOpenNote: (EntityId) -> Unit,
    onNewNote: (EntityId, EntityId?) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: NotebookDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.failed.collect { snackbarHostState.showSnackbar(context.getString(com.behnamjalali.planb.core.ui.R.string.ui_error_generic)) }
    }
    LaunchedEffect(state.missing) { if (state.missing) onBack() }
    NotebookDetailScreen(
        state = state,
        callbacks = NotebookDetailCallbacks(
            onBack = onBack,
            onOpenNote = onOpenNote,
            onNewNote = { section -> onNewNote(viewModel.notebookId, section) },
            onSelectSection = viewModel::selectSection,
            onSaveSection = viewModel::saveSection,
            onMoveSection = viewModel::moveSection,
            onDeleteSection = viewModel::deleteSection,
        ),
    )
}

@Composable
fun NotebookDetailScreen(state: NotebookDetailUiState, callbacks: NotebookDetailCallbacks) {
    var sectionDialog by remember { mutableStateOf<NotebookSection?>(null) }
    var moving by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    var menu by remember { mutableStateOf(false) }
    val notebook = state.notebook
    val selected = state.sections.firstOrNull { it.id == state.selectedSection }
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(
            title = notebook?.title.orEmpty(),
            onBack = callbacks.onBack,
            actions = {
                PlannerIconButton(Icons.AutoMirrored.Rounded.NoteAdd, stringResource(R.string.notebooks_new_note), { callbacks.onNewNote(state.selectedSection) })
                if (selected != null) {
                    Box {
                        PlannerIconButton(Icons.Rounded.MoreVert, stringResource(com.behnamjalali.planb.core.ui.R.string.ui_more), { menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.notebook_section_rename)) }, onClick = { menu = false; sectionDialog = selected })
                            if (state.otherNotebooks.isNotEmpty()) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.notebook_section_move)) }, onClick = { menu = false; moving = selected.id })
                            }
                            DropdownMenuItem(text = { Text(stringResource(R.string.notebook_section_delete)) }, onClick = { menu = false; deleting = selected.id })
                        }
                    }
                }
            },
        )
        if (state.loading || notebook == null) {
            PlannerLoadingState()
            return@Column
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { PlannerChip(stringResource(R.string.notebook_sections_all), state.selectedSection == null, { callbacks.onSelectSection(null) }) }
            items(state.sections, key = { it.id }) { s ->
                PlannerChip(s.title, s.id == state.selectedSection, { callbacks.onSelectSection(s.id) })
            }
            item {
                PlannerChip(stringResource(R.string.notebook_new_section), false, {
                    sectionDialog = NotebookSection(notebookId = notebook.id, title = "")
                }, icon = Icons.Rounded.Add)
            }
        }
        if (state.notes.isEmpty()) {
            PlannerEmptyState(
                icon = Icons.AutoMirrored.Rounded.NoteAdd,
                title = stringResource(R.string.notebook_empty_title),
                message = stringResource(R.string.notebook_empty),
                actionLabel = stringResource(R.string.notebooks_new_note),
                onAction = { callbacks.onNewNote(state.selectedSection) },
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.notes, key = { it.id }) { note ->
                    PlannerNoteCard(note, Modifier.fillMaxWidth().animateItem(), onClick = { callbacks.onOpenNote(note.id) }, accent = notebook.color)
                }
            }
        }
    }
    sectionDialog?.let { section ->
        var title by rememberSaveable { mutableStateOf(section.title) }
        PlannerDialog(
            title = stringResource(if (section.id == 0L) R.string.notebook_new_section else R.string.notebook_section_rename),
            onDismiss = { sectionDialog = null },
            confirmLabel = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_save),
            confirmEnabled = title.isNotBlank(),
            onConfirm = {
                callbacks.onSaveSection(section.copy(title = title.trim()))
                sectionDialog = null
            },
        ) {
            PlannerTextField(title, { title = it }, stringResource(R.string.notebook_section_name))
        }
    }
    moving?.let { sectionId ->
        PlannerDialog(
            title = stringResource(R.string.notebook_section_move),
            onDismiss = { moving = null },
            confirmLabel = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_cancel),
            dismissLabel = "",
            onConfirm = { moving = null },
        ) {
            state.otherNotebooks.forEach { nb ->
                TextButton(onClick = {
                    callbacks.onMoveSection(sectionId, nb.id)
                    moving = null
                }, modifier = Modifier.fillMaxWidth()) { Text(nb.title, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
    deleting?.let { id ->
        ConfirmDeleteDialog(
            stringResource(R.string.notebook_section_delete),
            stringResource(R.string.notebook_section_delete_confirm),
            { deleting = null },
            {
                callbacks.onDeleteSection(id)
                deleting = null
            },
        )
    }
}
