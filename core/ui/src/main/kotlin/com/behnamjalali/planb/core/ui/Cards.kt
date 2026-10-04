package com.behnamjalali.planb.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.core.designsystem.component.AnimatedTaskCheckbox
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerPill
import com.behnamjalali.planb.core.designsystem.component.PlannerProgressBar
import com.behnamjalali.planb.core.designsystem.component.PlannerProgressRing
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.Opacity
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.Goal
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.ProjectStatus
import com.behnamjalali.planb.core.model.ProjectSummary
import com.behnamjalali.planb.core.model.Task
import java.time.LocalDate

@Composable
fun priorityLabel(priority: Priority): String = stringResource(
    when (priority) {
        Priority.NONE -> R.string.ui_priority_none
        Priority.LOW -> R.string.ui_priority_low
        Priority.MEDIUM -> R.string.ui_priority_medium
        Priority.HIGH -> R.string.ui_priority_high
    },
)

@Composable
fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.HIGH -> PlanBTheme.colors.priorityHigh
    Priority.MEDIUM -> PlanBTheme.colors.priorityMedium
    Priority.LOW -> PlanBTheme.colors.priorityLow
    Priority.NONE -> MaterialTheme.colorScheme.outline
}

@Composable
fun accentName(accent: AccentColor): String = stringResource(
    when (accent) {
        AccentColor.LAVENDER -> R.string.ui_color_lavender
        AccentColor.MINT -> R.string.ui_color_mint
        AccentColor.PEACH -> R.string.ui_color_peach
        AccentColor.POWDER_BLUE -> R.string.ui_color_powder_blue
        AccentColor.ROSE -> R.string.ui_color_rose
        AccentColor.SAND -> R.string.ui_color_sand
        AccentColor.SAGE -> R.string.ui_color_sage
        AccentColor.SLATE -> R.string.ui_color_slate
    },
)

@Composable
fun projectStatusLabel(status: ProjectStatus): String = stringResource(
    when (status) {
        ProjectStatus.ACTIVE -> R.string.ui_status_active
        ProjectStatus.PAUSED -> R.string.ui_status_paused
        ProjectStatus.COMPLETED -> R.string.ui_status_completed
        ProjectStatus.ARCHIVED -> R.string.ui_status_archived
    },
)

@Composable
private fun MetaItem(icon: ImageVector, text: String?, tint: Color, description: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (description != null) Modifier.semantics { contentDescription = description } else Modifier,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(IconSize.xs))
        if (text != null) {
            Spacer(Modifier.width(Spacing.xxs))
            Text(text, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
        }
    }
}

/**
 * Task row card. [projectName]/[projectColor] are optional context.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlannerTaskCard(
    task: Task,
    onToggleComplete: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    projectName: String? = null,
    projectColor: AccentColor? = null,
    selected: Boolean = false,
    showDate: Boolean = true,
) {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    val scheme = MaterialTheme.colorScheme
    val completed = task.isCompleted
    val overdue = task.isOverdue(today)
    val container by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.surfaceContainerLowest,
        PlanBTheme.motion.standard(),
        label = "taskCard",
    )
    PlannerCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        onLongClick = onLongClick,
        containerColor = container,
        contentPadding = PaddingValues(start = Spacing.xs, end = Spacing.md, top = Spacing.xs, bottom = Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedTaskCheckbox(
                checked = completed,
                onCheckedChange = onToggleComplete,
                color = if (task.priority == Priority.NONE) scheme.primary else priorityColor(task.priority),
                contentDescription = task.title,
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (completed) scheme.onSurfaceVariant else scheme.onSurface,
                    textDecoration = if (completed) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(if (completed) Opacity.muted else 1f),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    val metaTint = if (overdue) scheme.error else scheme.onSurfaceVariant
                    if (showDate && task.dueDate != null) {
                        val dateText = buildString {
                            append(formatter.relativeDate(task.dueDate!!, today))
                            task.dueTime?.let { append(" · ").append(formatter.time(it)) }
                        }
                        MetaItem(Icons.Rounded.Schedule, dateText, metaTint)
                    } else if (task.dueTime != null) {
                        MetaItem(Icons.Rounded.Schedule, formatter.time(task.dueTime!!), metaTint)
                    }
                    if (task.subtaskCount > 0) {
                        val numbers = formatter.numbers
                        MetaItem(
                            Icons.Rounded.SubdirectoryArrowRight,
                            stringResource(
                                R.string.ui_subtasks_progress,
                                numbers.format(task.completedSubtaskCount),
                                numbers.format(task.subtaskCount),
                            ),
                            scheme.onSurfaceVariant,
                            stringResource(
                                R.string.ui_subtasks_description,
                                numbers.format(task.completedSubtaskCount),
                                numbers.format(task.subtaskCount),
                            ),
                        )
                    }
                    if (task.isRecurring) {
                        MetaItem(Icons.Rounded.Repeat, null, scheme.onSurfaceVariant, stringResource(R.string.ui_repeats))
                    }
                    if (task.reminderOffsetMinutes != null) {
                        MetaItem(
                            Icons.Rounded.NotificationsActive,
                            null,
                            scheme.onSurfaceVariant,
                            stringResource(R.string.ui_has_reminder),
                        )
                    }
                    if (projectName != null) {
                        val tones = PlanBTheme.colors.accent(projectColor ?: AccentColor.LAVENDER)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(tones.strong, CircleShape),
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                projectName,
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    task.tags.take(2).forEach { tag ->
                        val tones = PlanBTheme.colors.accent(tag.color)
                        PlannerPill("#${tag.name}", container = tones.container, content = tones.onContainer)
                    }
                }
            }
            if (task.priority == Priority.HIGH || task.priority == Priority.MEDIUM) {
                Icon(
                    Icons.Rounded.Flag,
                    contentDescription = stringResource(R.string.ui_priority_label, priorityLabel(task.priority)),
                    tint = priorityColor(task.priority),
                    modifier = Modifier.size(IconSize.sm),
                )
            }
        }
    }
}

@Composable
fun PlannerEventCard(
    event: CalendarEvent,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    dateLabel: String? = null,
) {
    val formatter = PlannerLocals.formatter
    val tones = PlanBTheme.colors.accent(event.color)
    PlannerCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = tones.container,
        contentColor = tones.onContainer,
        border = null,
        shadowElevation = 0.dp,
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(Modifier.heightIn(min = 56.dp)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(tones.strong),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                Text(event.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val time = when {
                    event.allDay || event.startTime == null -> stringResource(R.string.ui_all_day)
                    else -> formatter.timeRange(event.startTime!!, event.endTime)
                }
                Text(
                    listOfNotNull(dateLabel, time).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = tones.onContainer.copy(alpha = 0.8f),
                )
            }
            if (event.recurrence != null) {
                Icon(
                    Icons.Rounded.Repeat,
                    contentDescription = stringResource(R.string.ui_repeats),
                    modifier = Modifier
                        .padding(Spacing.md)
                        .size(IconSize.sm),
                )
            }
        }
    }
}

/** Habit card with a check-in button and today's progress. */
@Composable
fun PlannerHabitCard(
    habit: Habit,
    todayAmount: Int,
    streak: Int,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    subtitle: String? = null,
) {
    val numbers = PlannerLocals.numbers
    val tones = PlanBTheme.colors.accent(habit.color)
    val done = todayAmount >= habit.target
    val progress = if (habit.target <= 0) 0f else todayAmount.toFloat() / habit.target
    val checkInLabel = stringResource(R.string.ui_habit_check_in, habit.title)
    val doneLabel = if (done) stringResource(R.string.ui_done) else ""
    PlannerCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlannerProgressRing(
                progress = progress,
                size = 48.dp,
                strokeWidth = 4.dp,
                color = tones.strong,
                trackColor = tones.container,
            ) {
                Icon(habit.icon.vector, contentDescription = null, tint = tones.strong, modifier = Modifier.size(IconSize.md))
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(habit.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val unit = habit.unit.ifBlank { "" }
                Text(
                    stringResource(R.string.ui_habit_progress, numbers.format(todayAmount), numbers.format(habit.target), unit).trim(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (streak > 0) {
                    MetaItem(
                        Icons.Rounded.LocalFireDepartment,
                        stringResource(R.string.ui_streak, numbers.format(streak)),
                        PlanBTheme.colors.warning,
                    )
                }
            }
            Surface(
                onClick = onCheckIn,
                shape = CircleShape,
                color = if (done) tones.strong else tones.container,
                contentColor = if (done) MaterialTheme.colorScheme.surface else tones.onContainer,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = checkInLabel
                        stateDescription = doneLabel
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.md),
                    )
                }
            }
        }
    }
}

@Composable
fun PlannerProjectCard(
    summary: ProjectSummary,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val project = summary.project
    val tones = PlanBTheme.colors.accent(project.color)
    val numbers = PlannerLocals.numbers
    val formatter = PlannerLocals.formatter
    PlannerCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(Radius.sm), color = tones.container) {
                Icon(
                    project.icon.vector,
                    contentDescription = null,
                    tint = tones.onContainer,
                    modifier = Modifier
                        .padding(Spacing.sm)
                        .size(IconSize.md),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(project.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        projectStatusLabel(project.status),
                        project.dueDate?.let { stringResource(R.string.ui_due, formatter.shortDate(it, PlannerLocals.today)) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(numbers.percent(summary.progress), style = MaterialTheme.typography.labelLarge, color = tones.strong)
        }
        Spacer(Modifier.height(Spacing.md))
        PlannerProgressBar(summary.progress, color = tones.strong, trackColor = tones.container)
    }
}

@Composable
fun PlannerGoalCard(
    goal: Goal,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val numbers = PlannerLocals.numbers
    val formatter = PlannerLocals.formatter
    PlannerCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlannerProgressRing(
                goal.progress,
                size = 56.dp,
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(numbers.percent(goal.progress), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(goal.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(
                        R.string.ui_goal_progress,
                        numbers.format(goal.currentValue),
                        numbers.format(goal.target),
                        goal.unit,
                    ).trim(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                goal.deadline?.let {
                    Text(
                        stringResource(R.string.ui_due, formatter.shortDate(it, PlannerLocals.today)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun PlannerNoteCard(
    note: Note,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    accent: AccentColor = AccentColor.LAVENDER,
) {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    val preview = note.document.plainText()
    PlannerCard(modifier = modifier.fillMaxWidth(), onClick = onClick, onLongClick = onLongClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                note.title.ifBlank { stringResource(R.string.ui_untitled) },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (note.pinned) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = stringResource(R.string.ui_pinned),
                    tint = PlanBTheme.colors.accent(accent).strong,
                    modifier = Modifier.size(IconSize.xs),
                )
            }
            if (note.favorite) {
                Spacer(Modifier.width(Spacing.xs))
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = stringResource(R.string.ui_favorite),
                    tint = PlanBTheme.colors.warning,
                    modifier = Modifier.size(IconSize.xs),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            preview.ifBlank { stringResource(R.string.ui_empty_note) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.sm))
        val edited = note.updatedAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        Text(
            stringResource(R.string.ui_updated, formatter.relativeDate(edited, today)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/**
 * Lightweight completion heatmap drawn on one Canvas (no per-cell composables).
 * Columns are weeks (oldest → newest following the reading direction), rows are
 * weekdays starting at the user's first day of week. [intensity] returns 0..1.
 */
@Composable
fun PlannerHeatmap(
    weeks: Int,
    endDate: LocalDate,
    firstDayOfWeek: java.time.DayOfWeek,
    accent: AccentColor,
    intensity: (LocalDate) -> Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val tones = PlanBTheme.colors.accent(accent)
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    val start = com.behnamjalali.planb.core.datetime.MonthGrid.weekStart(endDate, firstDayOfWeek).minusWeeks((weeks - 1).toLong())
    val cells = androidx.compose.runtime.remember(weeks, endDate, firstDayOfWeek, intensity) {
        (0 until weeks * 7).map { index ->
            val date = start.plusDays(index.toLong())
            if (date > endDate) -1f else intensity(date)
        }
    }
    androidx.compose.foundation.Canvas(
        modifier
            .fillMaxWidth()
            .height((7 * 16).dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val gap = 3.dp.toPx()
        val cell = minOf((size.width - gap * (weeks - 1)) / weeks, (size.height - gap * 6) / 7)
        val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
        val radius = androidx.compose.ui.geometry.CornerRadius(cell * 0.28f)
        cells.forEachIndexed { index, value ->
            if (value < 0f) return@forEachIndexed
            val week = index / 7
            val day = index % 7
            val column = if (rtl) weeks - 1 - week else week
            val color = when {
                value <= 0f -> empty
                value >= 1f -> tones.strong
                else -> androidx.compose.ui.graphics.lerp(tones.container, tones.strong, 0.25f + 0.5f * value)
            }
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(column * (cell + gap), day * (cell + gap)),
                size = androidx.compose.ui.geometry.Size(cell, cell),
                cornerRadius = radius,
            )
        }
    }
}
