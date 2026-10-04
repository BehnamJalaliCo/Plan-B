package com.behnamjalali.planb.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.TemplateRepository
import com.behnamjalali.planb.core.data.repository.TemplateResult
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
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
import com.behnamjalali.planb.core.model.PlannerTemplate
import com.behnamjalali.planb.core.model.TemplateType
import com.behnamjalali.planb.core.ui.ConfirmDeleteDialog
import com.behnamjalali.planb.core.ui.PlannerLocals
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object TemplatesRoute

data class TemplatesUiState(val loading: Boolean = true, val error: Boolean = false, val builtIn: List<PlannerTemplate> = emptyList(), val custom: List<PlannerTemplate> = emptyList())

sealed interface TemplatesEvent {
    data class Applied(val result: TemplateResult) : TemplatesEvent
    data object Failed : TemplatesEvent
}

@HiltViewModel
class TemplatesViewModel @Inject constructor(private val templates: TemplateRepository) : ViewModel() {
    private val _events = MutableSharedFlow<TemplatesEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<TemplatesEvent> = _events

    val uiState: StateFlow<TemplatesUiState> = templates.observeTemplates().map { all ->
        TemplatesUiState(loading = false, builtIn = all.filter { it.builtIn }, custom = all.filterNot { it.builtIn })
    }.catch { emit(TemplatesUiState(loading = false, error = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplatesUiState())

    fun apply(template: PlannerTemplate, dateLabel: String, notebookTitle: String) = viewModelScope.launch {
        runCatchingSafely { templates.apply(template, dateLabel, notebookTitle) }
            .onSuccess { _events.tryEmit(TemplatesEvent.Applied(it)) }
            .onFailure { _events.tryEmit(TemplatesEvent.Failed) }
    }

    fun rename(id: EntityId, title: String) = viewModelScope.launch {
        runCatchingSafely { templates.rename(id, title) }.onFailure { _events.tryEmit(TemplatesEvent.Failed) }
    }

    fun delete(id: EntityId) = viewModelScope.launch {
        runCatchingSafely { templates.delete(id) }.onFailure { _events.tryEmit(TemplatesEvent.Failed) }
    }
}

private fun style(type: TemplateType) = when (type) {
    TemplateType.NOTE -> Icons.AutoMirrored.Rounded.Notes to AccentColor.LAVENDER
    TemplateType.PROJECT -> Icons.Rounded.RocketLaunch to AccentColor.PEACH
    TemplateType.TASKS -> Icons.Rounded.CheckCircle to AccentColor.POWDER_BLUE
    TemplateType.HABITS -> Icons.Rounded.Repeat to AccentColor.MINT
}

@Composable
fun TemplatesDestination(
    onBack: () -> Unit,
    onOpenResult: (TemplateResult) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: TemplatesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    val notebookTitle = stringResource(com.behnamjalali.planb.core.data.R.string.data_planner_notebook)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is TemplatesEvent.Applied -> {
                    val r = snackbarHostState.showSnackbar(
                        context.getString(R.string.templates_applied),
                        actionLabel = context.getString(R.string.templates_open),
                        duration = SnackbarDuration.Short,
                    )
                    if (r == SnackbarResult.ActionPerformed) onOpenResult(e.result)
                }
                TemplatesEvent.Failed -> snackbarHostState.showSnackbar(context.getString(R.string.templates_failed))
            }
        }
    }
    TemplatesScreen(
        state = state,
        onBack = onBack,
        onApply = { viewModel.apply(it, formatter.mediumDate(today), notebookTitle) },
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
    )
}

@Composable
fun TemplatesScreen(
    state: TemplatesUiState,
    onBack: () -> Unit,
    onApply: (PlannerTemplate) -> Unit,
    onRename: (EntityId, String) -> Unit,
    onDelete: (EntityId) -> Unit,
) {
    var renaming by remember { mutableStateOf<PlannerTemplate?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(stringResource(R.string.templates_title), onBack = onBack)
        when {
            state.loading -> PlannerLoadingState()
            state.error -> PlannerErrorState(stringResource(R.string.templates_error))
            else -> LazyColumn(
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item(key = "b_h") { PlannerSectionHeader(stringResource(R.string.templates_builtin)) }
                items(state.builtIn, key = { "b_${it.builtInKey}" }) { t -> TemplateCard(t, onApply, null, null) }
                item(key = "c_h") { PlannerSectionHeader(stringResource(R.string.templates_custom)) }
                if (state.custom.isEmpty()) {
                    item(key = "c_e") { Text(stringResource(R.string.templates_custom_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(state.custom, key = { "c_${it.id}" }) { t -> TemplateCard(t, onApply, { renaming = t }, { deleting = t.id }) }
            }
        }
    }
    renaming?.let { t ->
        var title by rememberSaveable { mutableStateOf(t.title) }
        PlannerDialog(
            title = stringResource(R.string.templates_rename),
            onDismiss = { renaming = null },
            confirmLabel = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_save),
            confirmEnabled = title.isNotBlank(),
            onConfirm = { onRename(t.id, title); renaming = null },
        ) { PlannerTextField(title, { title = it }, stringResource(R.string.templates_name)) }
    }
    deleting?.let { id ->
        ConfirmDeleteDialog(stringResource(R.string.templates_delete), stringResource(R.string.templates_delete_confirm), { deleting = null }, {
            onDelete(id)
            deleting = null
        })
    }
}

@Composable
private fun TemplateCard(t: PlannerTemplate, onApply: (PlannerTemplate) -> Unit, onRename: (() -> Unit)?, onDelete: (() -> Unit)?) {
    val (icon, accent) = style(t.type)
    val tones = PlanBTheme.colors.accent(accent)
    val numbers = PlannerLocals.numbers
    var menu by remember { mutableStateOf(false) }
    PlannerCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(Radius.sm), color = tones.container) {
                Icon(icon, null, tint = tones.onContainer, modifier = Modifier.padding(Spacing.sm).size(IconSize.md))
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(t.title.ifBlank { stringResource(com.behnamjalali.planb.core.ui.R.string.ui_untitled) }, style = MaterialTheme.typography.titleSmall)
                val detail = when (t.type) {
                    TemplateType.NOTE -> pluralStringResource(R.plurals.templates_blocks, t.payload.blocks.size, numbers.format(t.payload.blocks.size))
                    TemplateType.PROJECT, TemplateType.TASKS -> pluralStringResource(R.plurals.templates_tasks_count, t.payload.tasks.size, numbers.format(t.payload.tasks.size))
                    TemplateType.HABITS -> pluralStringResource(R.plurals.templates_habits_count, t.payload.habits.size, numbers.format(t.payload.habits.size))
                }
                val type = stringResource(
                    when (t.type) {
                        TemplateType.NOTE -> R.string.templates_type_note
                        TemplateType.PROJECT -> R.string.templates_type_project
                        TemplateType.TASKS -> R.string.templates_type_tasks
                        TemplateType.HABITS -> R.string.templates_type_habits
                    },
                )
                Text("$type · $detail", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onRename != null && onDelete != null) {
                Box {
                    PlannerIconButton(Icons.Rounded.MoreVert, stringResource(com.behnamjalali.planb.core.ui.R.string.ui_more), { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.templates_rename)) }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.templates_delete)) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
        if (t.description.isNotBlank()) {
            Spacer(Modifier.height(Spacing.sm))
            Text(t.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.sm))
        PlannerButton(stringResource(R.string.templates_use), { onApply(t) }, style = PlannerButtonStyle.Tonal)
    }
}
