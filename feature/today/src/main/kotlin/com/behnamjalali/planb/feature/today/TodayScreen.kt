package com.behnamjalali.planb.feature.today

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerErrorState
import com.behnamjalali.planb.core.designsystem.component.PlannerHeroSurface
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerProgressRing
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.DashboardSection
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.FocusStatus
import com.behnamjalali.planb.core.ui.PlannerEventCard
import com.behnamjalali.planb.core.ui.PlannerHabitCard
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.PlannerNoteCard
import com.behnamjalali.planb.core.ui.PlannerProjectCard
import com.behnamjalali.planb.core.ui.PlannerTaskCard

/** Navigation callbacks out of Today. */
data class TodayActions(
    val onOpenTask: (EntityId) -> Unit = {},
    val onOpenEvent: (EntityId) -> Unit = {},
    val onOpenHabit: (EntityId) -> Unit = {},
    val onOpenProject: (EntityId) -> Unit = {},
    val onOpenNote: (EntityId) -> Unit = {},
    val onOpenFocus: () -> Unit = {},
    val onOpenTasks: () -> Unit = {},
    val onOpenCalendar: () -> Unit = {},
    val onOpenHabits: () -> Unit = {},
    val onOpenProjects: () -> Unit = {},
    val onOpenNotebooks: () -> Unit = {},
    val onOpenSearch: () -> Unit = {},
    val onNewNote: () -> Unit = {},
    val onCustomize: () -> Unit = {},
)

@Composable
fun TodayScreen(
    state: TodayUiState,
    actions: TodayActions,
    onToggleTask: (EntityId, Boolean) -> Unit,
    onCheckInHabit: (EntityId, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    when (state) {
        TodayUiState.Loading -> PlannerLoadingState(modifier.fillMaxSize())
        TodayUiState.Error -> PlannerErrorState(stringResource(R.string.today_error), modifier.fillMaxSize())
        is TodayUiState.Success -> TodayContent(state.data, actions, onToggleTask, onCheckInHabit, modifier, contentPadding)
    }
}

@Composable
private fun TodayContent(
    data: TodayData,
    actions: TodayActions,
    onToggleTask: (EntityId, Boolean) -> Unit,
    onCheckInHabit: (EntityId, Boolean) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screen,
            end = Spacing.screen,
            top = contentPadding.calculateTopPadding() + Spacing.sm,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "header", contentType = "header") { TodayHeader(data, actions) }
        data.dashboard.visibleSections.forEach { section ->
            when (section) {
                DashboardSection.SUMMARY -> item(key = "summary", contentType = "summary") { SummaryRow(data) }
                DashboardSection.TIMELINE -> timelineSection(data, actions)
                DashboardSection.TASKS -> tasksSection(data, actions, onToggleTask)
                DashboardSection.UPCOMING -> upcomingSection(data, actions, onToggleTask)
                DashboardSection.HABITS -> habitsSection(data, actions, onCheckInHabit)
                DashboardSection.FOCUS -> item(key = "focus", contentType = "focus") { FocusCard(data, actions.onOpenFocus) }
                DashboardSection.PROJECTS -> projectsSection(data, actions)
                DashboardSection.NOTES -> notesSection(data, actions)
            }
        }
    }
}

@Composable
private fun TodayHeader(data: TodayData, actions: TodayActions) {
    val formatter = PlannerLocals.formatter
    val numbers = PlannerLocals.numbers
    val greeting = stringResource(
        when (data.greeting) {
            Greeting.MORNING -> R.string.today_greeting_morning
            Greeting.AFTERNOON -> R.string.today_greeting_afternoon
            Greeting.EVENING -> R.string.today_greeting_evening
            Greeting.NIGHT -> R.string.today_greeting_night
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    formatter.fullDate(data.date),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            PlannerIconButton(Icons.Rounded.Search, stringResource(R.string.today_search), actions.onOpenSearch)
            PlannerIconButton(Icons.Rounded.Dashboard, stringResource(R.string.today_customize), actions.onCustomize)
        }
        PlannerHeroSurface(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val progressText = numbers.percent(data.progress)
                PlannerProgressRing(
                    progress = data.progress,
                    size = 76.dp,
                    strokeWidth = 8.dp,
                    contentDescription = stringResource(com.behnamjalali.planb.core.designsystem.R.string.ds_progress, progressText),
                ) {
                    Text(progressText, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.width(Spacing.lg))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.today_progress_label), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (data.totalForProgress == 0) {
                            stringResource(R.string.today_progress_empty)
                        } else {
                            stringResource(
                                R.string.today_progress_value,
                                numbers.format(data.completedToday),
                                numbers.format(data.totalForProgress),
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(data: TodayData) {
    val numbers = PlannerLocals.numbers
    val colors = PlanBTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SummaryTile(Icons.Rounded.Warning, numbers.format(data.overdueCount), stringResource(R.string.today_summary_overdue),
            if (data.overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
        SummaryTile(Icons.Rounded.WbSunny, numbers.format(data.dueTodayCount), stringResource(R.string.today_summary_due),
            colors.accent(com.behnamjalali.planb.core.model.AccentColor.PEACH).strong, Modifier.weight(1f))
        SummaryTile(Icons.Rounded.Event, numbers.format(data.events.size), stringResource(R.string.today_summary_events),
            colors.accent(com.behnamjalali.planb.core.model.AccentColor.POWDER_BLUE).strong, Modifier.weight(1f))
        SummaryTile(Icons.Rounded.CheckCircle, "${numbers.format(data.habitsDone)}/${numbers.format(data.habits.size)}",
            stringResource(R.string.today_summary_habits), colors.success, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryTile(icon: ImageVector, value: String, label: String, tint: Color, modifier: Modifier) {
    PlannerCard(modifier = modifier, contentPadding = PaddingValues(Spacing.md)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(IconSize.sm))
        Spacer(Modifier.height(Spacing.xs))
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun LazyListScope.header(key: String, title: Int, onAction: (() -> Unit)?) {
    item(key = "h_$key", contentType = "sectionHeader") {
        PlannerSectionHeader(
            title = stringResource(title),
            actionLabel = if (onAction != null) stringResource(R.string.today_see_all) else null,
            onAction = onAction,
        )
    }
}

private fun LazyListScope.emptyLine(key: String, text: Int) {
    item(key = "e_$key", contentType = "empty") {
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = Spacing.xs),
        )
    }
}

private fun LazyListScope.timelineSection(data: TodayData, actions: TodayActions) {
    header("timeline", R.string.today_section_timeline, actions.onOpenCalendar)
    val timeline = data.timeline
    if (timeline.isEmpty()) {
        emptyLine("timeline", R.string.today_empty_timeline)
        return
    }
    items(timeline, key = { item ->
        when (item) {
            is TimelineItem.TaskItem -> "tl_task_${item.task.id}"
            is TimelineItem.EventItem -> "tl_event_${item.occurrence.event.id}_${item.occurrence.date}"
        }
    }, contentType = { "timeline" }) { item ->
        TimelineRow(item, actions)
    }
}

@Composable
private fun TimelineRow(item: TimelineItem, actions: TodayActions) {
    val formatter = PlannerLocals.formatter
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.width(56.dp).padding(top = Spacing.md)) {
            Text(
                item.time?.let { formatter.time(it) } ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (item) {
            is TimelineItem.EventItem -> PlannerEventCard(item.occurrence.event, Modifier.weight(1f), onClick = {
                actions.onOpenEvent(item.occurrence.event.id)
            })
            is TimelineItem.TaskItem -> PlannerCard(
                modifier = Modifier.weight(1f),
                onClick = { actions.onOpenTask(item.task.id) },
            ) {
                Text(item.task.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun LazyListScope.tasksSection(data: TodayData, actions: TodayActions, onToggle: (EntityId, Boolean) -> Unit) {
    header("tasks", R.string.today_section_tasks, actions.onOpenTasks)
    if (data.todayTasks.isEmpty()) {
        item(key = "e_tasks", contentType = "emptyCard") {
            PlannerCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.today_empty_tasks_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.today_empty_tasks_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    items(data.todayTasks.take(MAX_TASKS), key = { "task_${it.id}" }, contentType = { "task" }) { task ->
        PlannerTaskCard(
            task = task,
            onToggleComplete = { onToggle(task.id, it) },
            onClick = { actions.onOpenTask(task.id) },
            modifier = Modifier.animateItem(),
        )
    }
}

private fun LazyListScope.upcomingSection(data: TodayData, actions: TodayActions, onToggle: (EntityId, Boolean) -> Unit) {
    header("upcoming", R.string.today_section_upcoming, actions.onOpenTasks)
    if (data.upcoming.isEmpty()) {
        emptyLine("upcoming", R.string.today_empty_upcoming)
        return
    }
    items(data.upcoming, key = { "up_${it.id}" }, contentType = { "task" }) { task ->
        PlannerTaskCard(
            task = task,
            onToggleComplete = { onToggle(task.id, it) },
            onClick = { actions.onOpenTask(task.id) },
            modifier = Modifier.animateItem(),
        )
    }
}

private fun LazyListScope.habitsSection(data: TodayData, actions: TodayActions, onCheckIn: (EntityId, Boolean) -> Unit) {
    header("habits", R.string.today_section_habits, actions.onOpenHabits)
    if (data.habits.isEmpty()) {
        emptyLine("habits", R.string.today_empty_habits)
        return
    }
    items(data.habits, key = { "habit_${it.habit.habit.id}" }, contentType = { "habit" }) { h ->
        val done = h.amount >= h.habit.habit.target
        PlannerHabitCard(
            habit = h.habit.habit,
            todayAmount = h.amount,
            streak = h.streak.count,
            onCheckIn = { onCheckIn(h.habit.habit.id, done) },
            onClick = { actions.onOpenHabit(h.habit.habit.id) },
        )
    }
}

@Composable
private fun FocusCard(data: TodayData, onOpen: () -> Unit) {
    val formatter = PlannerLocals.formatter
    val active = data.activeFocus
    val tones = PlanBTheme.colors.accent(com.behnamjalali.planb.core.model.AccentColor.MINT)
    Column {
        PlannerSectionHeader(stringResource(R.string.today_section_focus))
        PlannerCard(Modifier.fillMaxWidth().animateContentSize(), onClick = onOpen, containerColor = tones.container, contentColor = tones.onContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(IconSize.lg))
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    val now by androidx.compose.runtime.produceState(java.time.Instant.now(), active) {
                        while (active?.status == FocusStatus.RUNNING) {
                            value = java.time.Instant.now()
                            kotlinx.coroutines.delay(1_000)
                        }
                    }
                    Text(
                        when {
                            active == null -> stringResource(R.string.today_focus_start)
                            active.status == FocusStatus.PAUSED ->
                                stringResource(R.string.today_focus_paused, formatter.timer(active.remainingMillis(now)))
                            else -> stringResource(R.string.today_focus_running, formatter.timer(active.remainingMillis(now)))
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.today_focus_today, formatter.duration(data.focusMinutesToday)),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (active == null) Icon(Icons.Rounded.Add, contentDescription = null)
            }
        }
    }
}

private fun LazyListScope.projectsSection(data: TodayData, actions: TodayActions) {
    header("projects", R.string.today_section_projects, actions.onOpenProjects)
    if (data.projects.isEmpty()) {
        emptyLine("projects", R.string.today_empty_projects)
        return
    }
    item(key = "projects_row", contentType = "projects") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            items(data.projects, key = { it.project.id }) { summary ->
                PlannerProjectCard(summary, Modifier.width(260.dp), onClick = { actions.onOpenProject(summary.project.id) })
            }
        }
    }
}

private fun LazyListScope.notesSection(data: TodayData, actions: TodayActions) {
    header("notes", R.string.today_section_notes, actions.onOpenNotebooks)
    if (data.notes.isEmpty()) {
        emptyLine("notes", R.string.today_empty_notes)
    } else {
        items(data.notes, key = { "note_${it.id}" }, contentType = { "note" }) { note ->
            PlannerNoteCard(note, onClick = { actions.onOpenNote(note.id) })
        }
    }
    item(key = "new_note", contentType = "action") {
        com.behnamjalali.planb.core.designsystem.component.PlannerButton(
            text = stringResource(R.string.today_new_note),
            onClick = actions.onNewNote,
            style = com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle.Tonal,
            icon = Icons.Rounded.Add,
        )
    }
}

private const val MAX_TASKS = 8
