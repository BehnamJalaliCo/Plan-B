package com.behnamjalali.planb.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerPill
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.model.DeadlineUrgency
import com.behnamjalali.planb.core.model.Deadlines
import com.behnamjalali.planb.core.model.TaskReminder
import com.behnamjalali.planb.core.model.TaskReminderKind
import java.time.LocalDate

/*
 * Shared pieces of Plan-B Pro planning (#11 deadlines, #12 reminders, #14 dependencies).
 */

/** Offsets offered for an extra reminder before the planned time. */
val ExtraReminderOffsets: List<Int> = listOf(0, 5, 10, 15, 30, 60, 120, 1440, 2880)

/** Offsets offered for a reminder before the deadline (counted from 09:00 of the deadline day). */
val DeadlineReminderOffsets: List<Int> = listOf(0, 60, 180, 1440, 2880, 4320, 10080)

/** "Deadline in 2 days", "Deadline today", "Deadline passed" or "Deadline 12 Mehr". */
@Composable
fun deadlineLabel(deadline: LocalDate, today: LocalDate): String {
    val numbers = PlannerLocals.numbers
    val days = Deadlines.daysLeft(deadline, today)
    return when (Deadlines.urgency(deadline, today)) {
        DeadlineUrgency.OVERDUE -> stringResource(R.string.ui_deadline_passed)
        DeadlineUrgency.TODAY -> stringResource(R.string.ui_deadline_today)
        DeadlineUrgency.SOON -> pluralStringResource(R.plurals.ui_deadline_in_days, days.toInt(), numbers.format(days))
        DeadlineUrgency.LATER -> if (days <= 14) {
            pluralStringResource(R.plurals.ui_deadline_in_days, days.toInt(), numbers.format(days))
        } else {
            stringResource(R.string.ui_deadline_on, PlannerLocals.formatter.shortDate(deadline, today))
        }
    }
}

/** Container/content colors of a deadline badge: error when passed or today, warning when soon. */
@Composable
fun deadlineColors(urgency: DeadlineUrgency): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (urgency) {
        DeadlineUrgency.OVERDUE, DeadlineUrgency.TODAY -> scheme.errorContainer to scheme.onErrorContainer
        DeadlineUrgency.SOON -> PlanBTheme.colors.warning.copy(alpha = 0.16f) to PlanBTheme.colors.warning
        DeadlineUrgency.LATER -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
}

/** The deadline badge of task rows (Plan-B Pro #11). */
@Composable
fun DeadlinePill(deadline: LocalDate, today: LocalDate, modifier: Modifier = Modifier) {
    val (container, content) = deadlineColors(Deadlines.urgency(deadline, today))
    PlannerPill(deadlineLabel(deadline, today), modifier = modifier, container = container, content = content, icon = Icons.Rounded.EventBusy)
}

/** A label for an offset before the deadline. */
@Composable
fun deadlineReminderLabel(offset: Int): String =
    if (offset == 0) stringResource(R.string.reminder_deadline_day) else stringResource(R.string.reminder_before_deadline, reminderLabel(offset))

/** A label for an extra reminder (Plan-B Pro #12). */
@Composable
fun taskReminderLabel(reminder: TaskReminder): String {
    val formatter = PlannerLocals.formatter
    return when (reminder.kind) {
        TaskReminderKind.OFFSET -> reminderLabel(reminder.offsetMinutes)
        TaskReminderKind.DEADLINE -> deadlineReminderLabel(reminder.offsetMinutes ?: 0)
        TaskReminderKind.ABSOLUTE -> reminder.at?.let { at ->
            val local = at.atZone(java.time.ZoneId.systemDefault())
            formatter.weekdayDate(local.toLocalDate(), PlannerLocals.today) + stringResource(R.string.ui_list_separator) + formatter.time(local.toLocalTime())
        }.orEmpty()
    }
}

/** Asks before completing a task that still waits for [openBlockers] other tasks (Plan-B Pro #14). */
@Composable
fun CompleteBlockedDialog(openBlockers: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    PlannerDialog(
        title = stringResource(R.string.ui_blocked_title),
        message = pluralStringResource(R.plurals.ui_blocked_message, openBlockers, PlannerLocals.numbers.format(openBlockers)),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.ui_blocked_confirm),
        onConfirm = onConfirm,
    )
}
