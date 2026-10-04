package com.behnamjalali.planb.feature.habits

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerEmptyState
import com.behnamjalali.planb.core.designsystem.component.PlannerErrorState
import com.behnamjalali.planb.core.designsystem.component.PlannerHeroSurface
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerProgressRing
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.HabitSchedule
import com.behnamjalali.planb.core.model.PlannerIcon
import com.behnamjalali.planb.core.model.Streak
import com.behnamjalali.planb.core.ui.AccentColorPicker
import com.behnamjalali.planb.core.ui.ConfirmDeleteDialog
import com.behnamjalali.planb.core.ui.DiscardChangesDialog
import com.behnamjalali.planb.core.ui.EditorRow
import com.behnamjalali.planb.core.ui.PlannerDatePickerDialog
import com.behnamjalali.planb.core.ui.PlannerHabitCard
import com.behnamjalali.planb.core.ui.PlannerHeatmap
import com.behnamjalali.planb.core.ui.PlannerIconPicker
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.PlannerTimePickerDialog
import com.behnamjalali.planb.core.ui.rememberNotificationPermissionRequest
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun streakText(streak: Streak): String {
    val n = PlannerLocals.numbers.format(streak.count)
    return pluralStringResource(if (streak.unit == Streak.Unit.WEEKS) R.plurals.habit_streak_weeks else R.plurals.habit_streak_days, streak.count, n)
}

@Composable
fun scheduleSummary(schedule: HabitSchedule): String {
    val formatter = PlannerLocals.formatter
    return when (schedule) {
        HabitSchedule.Daily -> stringResource(R.string.habit_schedule_daily)
        is HabitSchedule.SelectedDays -> formatter.weekdays().filter { it in schedule.days }
            .joinToString(stringResource(com.behnamjalali.planb.core.ui.R.string.ui_list_separator)) { formatter.weekdayShort(it) }
        is HabitSchedule.TimesPerWeek -> pluralStringResource(R.plurals.habit_summary_times_week, schedule.times, formatter.numbers.format(schedule.times))
        is HabitSchedule.EveryNDays -> pluralStringResource(R.plurals.habit_summary_every_n, schedule.interval, formatter.numbers.format(schedule.interval))
    }
}

@Composable
fun HabitsDestination(
    onBack: () -> Unit,
    onOpenHabit: (EntityId) -> Unit,
    onNewHabit: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: HabitsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.failed.collect { snackbarHostState.showSnackbar(resources.getString(com.behnamjalali.planb.core.ui.R.string.ui_error_generic)) }
    }
    HabitsScreen(state, onBack, onOpenHabit, onNewHabit, viewModel::checkIn, viewModel::toggleArchived)
}

@Composable
fun HabitsScreen(
    state: HabitsUiState,
    onBack: (() -> Unit)?,
    onOpenHabit: (EntityId) -> Unit,
    onNewHabit: () -> Unit,
    onCheckIn: (HabitRow) -> Unit,
    onToggleArchived: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(
            title = stringResource(R.string.habits_title),
            onBack = onBack,
            actions = {
                PlannerIconButton(Icons.Rounded.Archive, stringResource(R.string.habits_archived), onToggleArchived,
                    tint = if (state.showArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                PlannerIconButton(Icons.Rounded.Add, stringResource(R.string.habits_new), onNewHabit)
            },
        )
        when {
            state.loading -> PlannerLoadingState()
            state.error -> PlannerErrorState(stringResource(R.string.habits_error))
            state.habits.isEmpty() -> PlannerEmptyState(
                Icons.Rounded.Repeat, stringResource(R.string.habits_empty_title), stringResource(R.string.habits_empty),
                actionLabel = stringResource(R.string.habits_new), onAction = onNewHabit,
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(state.habits, key = { it.habit.id }) { row ->
                    Column(Modifier.animateItem()) {
                        PlannerHabitCard(
                            habit = row.habit,
                            todayAmount = row.todayAmount,
                            streak = row.streak.count,
                            onCheckIn = { onCheckIn(row) },
                            onClick = { onOpenHabit(row.habit.id) },
                            subtitle = scheduleSummary(row.habit.schedule),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HabitDetailDestination(
    onBack: () -> Unit,
    onEdit: (EntityId) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: HabitDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HabitEvent.Deleted -> onBack()
                HabitEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(com.behnamjalali.planb.core.ui.R.string.ui_error_generic))
            }
        }
    }
    LaunchedEffect(state.missing) { if (state.missing) onBack() }
    HabitDetailScreen(state, onBack, { onEdit(viewModel.habitId) }, viewModel::adjust, viewModel::setArchived, viewModel::delete)
}

@Composable
fun HabitDetailScreen(
    state: HabitDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAdjust: (Int) -> Unit,
    onArchive: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val item = state.item
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(
            title = item?.habit?.title.orEmpty(),
            onBack = onBack,
            actions = {
                Box {
                    PlannerIconButton(Icons.Rounded.MoreVert, stringResource(com.behnamjalali.planb.core.ui.R.string.ui_more), { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.habit_edit)) }, onClick = { menu = false; onEdit() })
                        val archived = item?.habit?.archived == true
                        DropdownMenuItem(text = { Text(stringResource(if (archived) R.string.habit_restore else R.string.habit_archive)) }, onClick = { menu = false; onArchive(!archived) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.habit_delete)) }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            },
        )
        if (state.loading || item == null) {
            PlannerLoadingState()
            return@Column
        }
        val habit = item.habit
        val numbers = PlannerLocals.numbers
        val tones = PlanBTheme.colors.accent(habit.color)
        val todayAmount = item.amounts[state.today] ?: 0
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            PlannerHeroSurface(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlannerProgressRing(todayAmount.toFloat() / habit.target, size = 88.dp, strokeWidth = 9.dp, color = tones.strong, trackColor = tones.container) {
                        Text("${numbers.format(todayAmount)}/${numbers.format(habit.target)}", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.width(Spacing.lg))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.habit_today), style = MaterialTheme.typography.titleMedium)
                        Text(scheduleSummary(habit.schedule), style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocalFireDepartment, null, tint = PlanBTheme.colors.warning)
                            Text(streakText(state.streak), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                // Wraps to a second line instead of truncating with large fonts.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PlannerButton(stringResource(R.string.habit_remove_one), { onAdjust(-1) }, style = com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle.Outlined, icon = Icons.Rounded.Remove, enabled = todayAmount > 0)
                    PlannerButton(stringResource(R.string.habit_add_one), { onAdjust(1) }, icon = Icons.Rounded.Add)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(stringResource(R.string.habit_best_streak), numbers.format(state.bestStreak), Modifier.weight(1f))
                StatTile(stringResource(R.string.habit_rate_30), numbers.percent(state.rate30), Modifier.weight(1f))
            }
            PlannerSectionHeader(stringResource(R.string.habit_history))
            val weeks = 20
            val doneDays = item.amounts.count { (date, amount) -> amount >= habit.target && date > state.today.minusWeeks(weeks.toLong()) }
            PlannerCard(Modifier.fillMaxWidth()) {
                PlannerHeatmap(
                    weeks = weeks,
                    endDate = state.today,
                    firstDayOfWeek = state.firstDayOfWeek,
                    accent = habit.color,
                    intensity = { date -> ((item.amounts[date] ?: 0).toFloat() / habit.target).coerceAtMost(1f) },
                    contentDescription = stringResource(R.string.habit_heatmap_description, numbers.format(weeks), numbers.format(doneDays)),
                )
            }
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(stringResource(R.string.habit_delete), stringResource(R.string.habit_delete_confirm), { confirmDelete = false }, {
            confirmDelete = false
            onDelete()
        })
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    PlannerCard(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitEditorDestination(onClose: () -> Unit, viewModel: HabitEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val formatter = PlannerLocals.formatter
    var discard by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val requestNotifications = rememberNotificationPermissionRequest()
    LaunchedEffect(viewModel) {
        viewModel.saved.collect { ok ->
            if (ok) onClose() else snackbar.showSnackbar(resources.getString(com.behnamjalali.planb.core.ui.R.string.ui_error_generic))
        }
    }
    val requestClose = { if (viewModel.isDirty) discard = true else onClose() }
    BackHandler(onBack = requestClose)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { PlannerTopBar(stringResource(if (viewModel.isNew) R.string.habit_editor_new else R.string.habit_editor_edit), onBack = requestClose) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = Spacing.screen, vertical = Spacing.md)) {
                PlannerButton(stringResource(com.behnamjalali.planb.core.ui.R.string.ui_save), viewModel::save, Modifier.fillMaxWidth(), enabled = form.valid)
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            PlannerTextField(form.title, { v -> viewModel.update { it.copy(title = v) } }, stringResource(R.string.habit_editor_title))
            Text(stringResource(R.string.habit_editor_schedule), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ScheduleKind.entries.forEach { kind ->
                    PlannerChip(
                        stringResource(
                            when (kind) {
                                ScheduleKind.DAILY -> R.string.habit_schedule_daily
                                ScheduleKind.DAYS -> R.string.habit_schedule_days
                                ScheduleKind.WEEKLY -> R.string.habit_schedule_weekly
                                ScheduleKind.INTERVAL -> R.string.habit_schedule_interval
                            },
                        ),
                        form.kind == kind,
                        { viewModel.update { it.copy(kind = kind) } },
                    )
                }
            }
            when (form.kind) {
                ScheduleKind.DAYS -> FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    formatter.weekdays().forEach { day ->
                        val selected = day.value in form.days
                        PlannerChip(formatter.weekdayShort(day), selected, {
                            viewModel.update { it.copy(days = if (selected) it.days - day.value else it.days + day.value) }
                        })
                    }
                }
                ScheduleKind.WEEKLY -> PlannerTextField(
                    form.timesPerWeek, { v -> viewModel.update { it.copy(timesPerWeek = v.take(1)) } }, stringResource(R.string.habit_editor_times_per_week),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = !form.timesValid,
                )
                ScheduleKind.INTERVAL -> PlannerTextField(
                    form.interval, { v -> viewModel.update { it.copy(interval = v.take(3)) } }, stringResource(R.string.habit_editor_interval),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = !form.intervalValid,
                )
                ScheduleKind.DAILY -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PlannerTextField(
                    form.target, { v -> viewModel.update { it.copy(target = v.take(3)) } }, stringResource(R.string.habit_editor_target),
                    modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !form.targetValid, supportingText = if (!form.targetValid) stringResource(R.string.habit_editor_invalid_number) else null,
                )
                PlannerTextField(form.unit, { v -> viewModel.update { it.copy(unit = v) } }, stringResource(R.string.habit_editor_unit), modifier = Modifier.weight(1f))
            }
            EditorRow(
                Icons.Rounded.Notifications, stringResource(R.string.habit_editor_reminder),
                form.reminder?.let { formatter.time(LocalTime.ofSecondOfDay(it.toLong())) } ?: stringResource(R.string.habit_editor_no_reminder),
                { picker = "time" },
            )
            EditorRow(Icons.Rounded.Event, stringResource(R.string.habit_editor_start), formatter.mediumDate(LocalDate.ofEpochDay(form.startDate)), { picker = "date" })
            Text(stringResource(R.string.habit_editor_color), style = MaterialTheme.typography.labelLarge)
            AccentColorPicker(AccentColor.fromKey(form.color), { c -> viewModel.update { it.copy(color = c.key) } })
            Text(stringResource(R.string.habit_editor_icon), style = MaterialTheme.typography.labelLarge)
            PlannerIconPicker(PlannerIcon.fromKey(form.icon), { i -> viewModel.update { it.copy(icon = i.key) } }, AccentColor.fromKey(form.color))
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
    when (picker) {
        "time" -> PlannerTimePickerDialog(form.reminder?.let { LocalTime.ofSecondOfDay(it.toLong()) }, { picker = null }, { t ->
            viewModel.update { it.copy(reminder = t?.toSecondOfDay()) }
            if (t != null) requestNotifications()
            picker = null
        })
        "date" -> PlannerDatePickerDialog(LocalDate.ofEpochDay(form.startDate), { picker = null }, { d ->
            d?.let { date -> viewModel.update { it.copy(startDate = date.toEpochDay()) } }
            picker = null
        }, allowClear = false)
    }
    if (discard) DiscardChangesDialog({ discard = false }, { discard = false; onClose() })
}
