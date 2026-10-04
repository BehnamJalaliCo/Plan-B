package com.behnamjalali.planb.feature.projects

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.PlannerIcon
import com.behnamjalali.planb.core.model.ProgressMode
import com.behnamjalali.planb.core.ui.AccentColorPicker
import com.behnamjalali.planb.core.ui.DiscardChangesDialog
import com.behnamjalali.planb.core.ui.EditorRow
import com.behnamjalali.planb.core.ui.PlannerDatePickerDialog
import com.behnamjalali.planb.core.ui.PlannerIconPicker
import com.behnamjalali.planb.core.ui.PlannerLocals
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectEditorDestination(
    onClose: () -> Unit,
    onSaved: (Long, Boolean) -> Unit,
    viewModel: ProjectEditorViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var discard by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    LaunchedEffect(viewModel) {
        viewModel.saved.collect { id ->
            if (id != null) onSaved(id, viewModel.isNew)
            else snackbar.showSnackbar(context.getString(com.behnamjalali.planb.core.ui.R.string.ui_error_generic))
        }
    }
    val requestClose = { if (viewModel.isDirty) discard = true else onClose() }
    BackHandler(onBack = requestClose)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            PlannerTopBar(stringResource(if (viewModel.isNew) R.string.project_editor_new else R.string.project_editor_edit), onBack = requestClose)
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = Spacing.screen, vertical = Spacing.md)) {
                PlannerButton(stringResource(com.behnamjalali.planb.core.ui.R.string.ui_save), viewModel::save, Modifier.fillMaxWidth(), enabled = form.title.isNotBlank())
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            PlannerTextField(form.title, { v -> viewModel.update { it.copy(title = v) } }, stringResource(R.string.project_editor_title))
            PlannerTextField(
                form.description, { v -> viewModel.update { it.copy(description = v) } },
                stringResource(R.string.project_editor_description), singleLine = false, minLines = 3,
            )
            EditorRow(Icons.Rounded.EventAvailable, stringResource(R.string.project_start),
                form.startDate?.let { formatter.mediumDate(LocalDate.ofEpochDay(it)) } ?: stringResource(com.behnamjalali.planb.core.ui.R.string.ui_not_set), { picker = "start" })
            EditorRow(Icons.Rounded.Event, stringResource(R.string.project_due),
                form.dueDate?.let { formatter.relativeDate(LocalDate.ofEpochDay(it), today) } ?: stringResource(com.behnamjalali.planb.core.ui.R.string.ui_not_set), { picker = "due" })
            Text(stringResource(R.string.project_editor_progress_mode), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ProgressMode.entries.forEach { mode ->
                    PlannerChip(
                        stringResource(
                            when (mode) {
                                ProgressMode.TASKS -> R.string.project_progress_tasks
                                ProgressMode.MILESTONES -> R.string.project_progress_milestones
                                ProgressMode.MANUAL -> R.string.project_progress_manual
                            },
                        ),
                        form.progressMode == mode,
                        { viewModel.update { it.copy(progressMode = mode) } },
                    )
                }
            }
            if (form.progressMode == ProgressMode.MANUAL) {
                Text(stringResource(R.string.project_manual_progress, PlannerLocals.numbers.percent(form.manualProgress)))
                Slider(value = form.manualProgress, onValueChange = { v -> viewModel.update { it.copy(manualProgress = v) } }, steps = 19)
            }
            Text(stringResource(R.string.project_editor_color), style = MaterialTheme.typography.labelLarge)
            AccentColorPicker(AccentColor.fromKey(form.color), { c -> viewModel.update { it.copy(color = c.key) } })
            Text(stringResource(R.string.project_editor_icon), style = MaterialTheme.typography.labelLarge)
            PlannerIconPicker(PlannerIcon.fromKey(form.icon), { i -> viewModel.update { it.copy(icon = i.key) } }, AccentColor.fromKey(form.color))
            PlannerTextField(form.tags, { v -> viewModel.update { it.copy(tags = v) } }, stringResource(R.string.project_tags))
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
    when (picker) {
        "start", "due" -> PlannerDatePickerDialog(
            initial = (if (picker == "start") form.startDate else form.dueDate)?.let(LocalDate::ofEpochDay),
            onDismiss = { picker = null },
            onConfirm = { d ->
                val epoch = d?.toEpochDay()
                if (picker == "start") viewModel.update { it.copy(startDate = epoch) } else viewModel.update { it.copy(dueDate = epoch) }
                picker = null
            },
        )
    }
    if (discard) DiscardChangesDialog({ discard = false }, { discard = false; onClose() })
}
