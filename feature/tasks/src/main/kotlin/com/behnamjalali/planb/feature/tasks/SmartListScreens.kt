package com.behnamjalali.planb.feature.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerEmptyState
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerSurface
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.PlannerIcon
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.SavedFilter
import com.behnamjalali.planb.core.model.SmartDateRange
import com.behnamjalali.planb.core.model.Tag
import com.behnamjalali.planb.core.model.TaskSort
import com.behnamjalali.planb.core.model.TaskStatus
import com.behnamjalali.planb.core.ui.AccentColorPicker
import com.behnamjalali.planb.core.ui.ConfirmDeleteDialog
import com.behnamjalali.planb.core.ui.EditorRow
import com.behnamjalali.planb.core.ui.PlannerDatePickerDialog
import com.behnamjalali.planb.core.ui.PlannerIconPicker
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProGate
import com.behnamjalali.planb.core.ui.priorityLabel
import com.behnamjalali.planb.core.ui.vector
import java.time.LocalDate

@Composable
fun SmartListEditorDestination(onClose: () -> Unit, viewModel: SmartListEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val matches by viewModel.matchCount.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SmartListEvent.Saved, SmartListEvent.Deleted -> onClose()
                SmartListEvent.Failed -> snackbar.showSnackbar(resources.getString(R.string.tasks_error))
            }
        }
    }
    SmartListEditorScreen(
        form = form,
        isNew = viewModel.isNew,
        projects = projects,
        tags = tags,
        matchCount = matches,
        snackbarHostState = snackbar,
        onClose = onClose,
        onUpdate = viewModel::update,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

private fun <T> List<T>.toggle(value: T): List<T> = if (value in this) this - value else this + value

/** The filter builder of a smart list (Plan-B Pro #10). Free users see the Pro teaser. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartListEditorScreen(
    form: SmartListForm,
    isNew: Boolean,
    projects: List<Project>,
    tags: List<Tag>,
    matchCount: Int?,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onUpdate: ((SmartListForm) -> SmartListForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val formatter = PlannerLocals.formatter
    val numbers = PlannerLocals.numbers
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var nameTouched by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onClose)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PlannerTopBar(
                title = stringResource(if (isNew) R.string.smart_list_new else R.string.smart_list_edit),
                onBack = onClose,
                actions = {
                    if (!isNew) PlannerIconButton(Icons.Rounded.Delete, stringResource(R.string.smart_list_delete), { confirmDelete = true })
                },
            )
        },
        bottomBar = {
            ProGate(ProFeature.SMART_LISTS, teaser = {}) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = Spacing.screen, vertical = Spacing.md)) {
                    PlannerButton(
                        stringResource(R.string.smart_list_save),
                        {
                            nameTouched = true
                            onSave()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = form.canSave,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ProGate(ProFeature.SMART_LISTS) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    val tones = PlanBTheme.colors.accent(AccentColor.fromKey(form.color))
                    PlannerSurface(Modifier.fillMaxWidth(), color = tones.container) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            Icon(PlannerIcon.fromKey(form.icon).vector, contentDescription = null, tint = tones.onContainer, modifier = Modifier.size(IconSize.lg))
                            Text(
                                matchCount?.let { pluralStringResource(R.plurals.smart_list_matches, it, numbers.format(it)) }.orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                                color = tones.onContainer,
                            )
                        }
                    }
                    PlannerTextField(
                        value = form.name,
                        onValueChange = { v ->
                            nameTouched = true
                            onUpdate { it.copy(name = v.take(60)) }
                        },
                        label = stringResource(R.string.smart_list_name),
                        isError = nameTouched && form.name.isBlank(),
                        supportingText = if (nameTouched && form.name.isBlank()) stringResource(R.string.smart_list_name_required) else null,
                    )
                    Label(R.string.smart_list_icon)
                    PlannerIconPicker(PlannerIcon.fromKey(form.icon), { icon -> onUpdate { it.copy(icon = icon.key) } }, AccentColor.fromKey(form.color))
                    Label(R.string.smart_list_color)
                    AccentColorPicker(AccentColor.fromKey(form.color), { color -> onUpdate { it.copy(color = color.key) } })
                    Text(stringResource(R.string.smart_list_any_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Label(R.string.smart_list_projects)
                    ChipFlow {
                        PlannerChip(stringResource(R.string.smart_list_no_project), form.noProject, { onUpdate { it.copy(noProject = !it.noProject) } })
                        projects.forEach { p ->
                            PlannerChip(p.title, p.id in form.projectIds, { onUpdate { it.copy(projectIds = it.projectIds.toggle(p.id)) } }, icon = p.icon.vector)
                        }
                    }
                    if (tags.isNotEmpty()) {
                        Label(R.string.smart_list_tags)
                        ChipFlow {
                            tags.forEach { t -> PlannerChip("#${t.name}", t.id in form.tagIds, { onUpdate { it.copy(tagIds = it.tagIds.toggle(t.id)) } }) }
                        }
                    }
                    Label(R.string.smart_list_priority)
                    ChipFlow {
                        Priority.entries.reversed().forEach { p ->
                            PlannerChip(priorityLabel(p), p.name in form.priorities, { onUpdate { it.copy(priorities = it.priorities.toggle(p.name)) } })
                        }
                    }
                    Label(R.string.smart_list_status)
                    ChipFlow {
                        TaskStatus.entries.forEach { s ->
                            PlannerChip(
                                stringResource(
                                    when (s) {
                                        TaskStatus.TODO -> R.string.task_status_todo
                                        TaskStatus.IN_PROGRESS -> R.string.task_status_in_progress
                                        TaskStatus.DONE -> R.string.task_status_done
                                    },
                                ),
                                s.name in form.statuses,
                                { onUpdate { it.copy(statuses = it.statuses.toggle(s.name)) } },
                            )
                        }
                    }
                    Label(R.string.smart_list_date)
                    ChipFlow {
                        SmartDateRange.entries.forEach { range ->
                            PlannerChip(dateRangeLabel(range), form.dateRange == range.name, { onUpdate { it.copy(dateRange = range.name) } })
                        }
                    }
                    if (form.dateRange == SmartDateRange.CUSTOM.name) {
                        val today = PlannerLocals.today
                        val notSet = stringResource(R.string.task_editor_not_set)
                        EditorRow(Icons.Rounded.Event, stringResource(R.string.smart_list_from), form.from?.let { formatter.weekdayDate(LocalDate.ofEpochDay(it), today) } ?: notSet, { picker = "from" })
                        EditorRow(Icons.Rounded.Event, stringResource(R.string.smart_list_to), form.to?.let { formatter.weekdayDate(LocalDate.ofEpochDay(it), today) } ?: notSet, { picker = "to" })
                    }
                    Label(R.string.smart_list_deadline)
                    ChipFlow {
                        listOf(null to R.string.smart_list_deadline_any, true to R.string.smart_list_deadline_yes, false to R.string.smart_list_deadline_no)
                            .forEach { (value, label) -> PlannerChip(stringResource(label), form.hasDeadline == value, { onUpdate { it.copy(hasDeadline = value) } }) }
                    }
                    PlannerTextField(
                        value = form.text,
                        onValueChange = { v -> onUpdate { it.copy(text = v.take(100)) } },
                        label = stringResource(R.string.smart_list_text),
                    )
                    Label(R.string.smart_list_sort)
                    ChipFlow {
                        TaskSort.entries.forEach { sort ->
                            PlannerChip(sortName(sort), form.sort == sort.name, { onUpdate { it.copy(sort = sort.name) } })
                        }
                    }
                    Spacer(Modifier.height(Spacing.xxl))
                }
            }
        }
    }
    when (picker) {
        "from", "to" -> PlannerDatePickerDialog(
            initial = (if (picker == "from") form.from else form.to)?.let(LocalDate::ofEpochDay),
            onDismiss = { picker = null },
            onConfirm = { date ->
                val epoch = date?.toEpochDay()
                onUpdate { if (picker == "from") it.copy(from = epoch) else it.copy(to = epoch) }
                picker = null
            },
        )
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.smart_list_delete),
            message = stringResource(R.string.smart_list_delete_confirm),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}

@Composable
private fun Label(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.xs))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) { content() }
}

@Composable
private fun dateRangeLabel(range: SmartDateRange): String = when (range) {
    SmartDateRange.ANY -> stringResource(R.string.smart_list_date_any)
    SmartDateRange.OVERDUE -> stringResource(R.string.smart_list_date_overdue)
    SmartDateRange.TODAY -> stringResource(R.string.smart_list_date_today)
    SmartDateRange.NEXT_7_DAYS -> stringResource(R.string.smart_list_date_next_7, PlannerLocals.numbers.format(7))
    SmartDateRange.NO_DATE -> stringResource(R.string.smart_list_date_none)
    SmartDateRange.CUSTOM -> stringResource(R.string.smart_list_date_custom)
}

@Composable
private fun sortName(sort: TaskSort): String = stringResource(
    when (sort) {
        TaskSort.MANUAL -> R.string.tasks_sort_manual
        TaskSort.DUE_DATE -> R.string.tasks_sort_due
        TaskSort.PRIORITY -> R.string.tasks_sort_priority
        TaskSort.CREATED -> R.string.tasks_sort_created
        TaskSort.TITLE -> R.string.tasks_sort_title
        TaskSort.DEADLINE -> R.string.tasks_sort_deadline
    },
)

@Composable
fun SmartListsDestination(
    onBack: () -> Unit,
    onEdit: (EntityId?) -> Unit,
    viewModel: SmartListsViewModel = hiltViewModel(),
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    SmartListsScreen(lists, onBack, onEdit, viewModel::move, viewModel::delete)
}

/** Reorders, edits and deletes the custom smart lists (Plan-B Pro #10). */
@Composable
fun SmartListsScreen(
    lists: List<SavedFilter>?,
    onBack: () -> Unit,
    onEdit: (EntityId?) -> Unit,
    onMove: (EntityId, Int) -> Unit,
    onDelete: (EntityId) -> Unit,
) {
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(
            title = stringResource(R.string.smart_lists_title),
            onBack = onBack,
            actions = { PlannerIconButton(Icons.Rounded.Add, stringResource(R.string.smart_list_new), { onEdit(null) }) },
        )
        Box(Modifier.fillMaxSize()) {
            ProGate(ProFeature.SMART_LISTS, modifier = Modifier.padding(Spacing.screen)) {
                when {
                    lists == null -> PlannerLoadingState()
                    lists.isEmpty() -> PlannerEmptyState(
                        Icons.Rounded.FilterAlt,
                        stringResource(R.string.smart_lists_empty_title),
                        stringResource(R.string.smart_lists_empty),
                        actionLabel = stringResource(R.string.smart_list_new),
                        onAction = { onEdit(null) },
                    )
                    else -> LazyColumn(contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        itemsIndexed(lists, key = { _, it -> it.id }) { index, list ->
                            val tones = PlanBTheme.colors.accent(list.color)
                            PlannerCard(Modifier.fillMaxWidth().animateItem(), onClick = { onEdit(list.id) }, contentPadding = PaddingValues(Spacing.sm)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(Radius.sm), color = tones.container) {
                                        Icon(list.icon.vector, contentDescription = null, tint = tones.onContainer, modifier = Modifier.padding(Spacing.sm).size(IconSize.md))
                                    }
                                    Text(
                                        list.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(horizontal = Spacing.md),
                                    )
                                    PlannerIconButton(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.smart_lists_move_up, list.name), { onMove(list.id, -1) }, enabled = index > 0)
                                    PlannerIconButton(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.smart_lists_move_down, list.name), { onMove(list.id, 1) }, enabled = index < lists.lastIndex)
                                    PlannerIconButton(Icons.Rounded.Edit, stringResource(R.string.smart_lists_edit, list.name), { onEdit(list.id) })
                                    PlannerIconButton(Icons.Rounded.Delete, stringResource(R.string.smart_lists_delete, list.name), { deleting = list.id })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { id ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.smart_list_delete),
            message = stringResource(R.string.smart_list_delete_confirm),
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                onDelete(id)
            },
        )
    }
}
