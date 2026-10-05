package com.behnamjalali.planb.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.behnamjalali.planb.core.common.SearchNormalizer
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerSearchBar
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.MinTouchTarget
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskReminderKind
import com.behnamjalali.planb.core.model.TaskReminderRules
import com.behnamjalali.planb.core.ui.DeadlineReminderOffsets
import com.behnamjalali.planb.core.ui.EditorRow
import com.behnamjalali.planb.core.ui.ExtraReminderOffsets
import com.behnamjalali.planb.core.ui.PlannerDatePickerDialog
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.PlannerTimePickerDialog
import com.behnamjalali.planb.core.ui.ProBadge
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProSwitchRow
import com.behnamjalali.planb.core.ui.deadlineLabel
import com.behnamjalali.planb.core.ui.deadlineReminderLabel
import com.behnamjalali.planb.core.ui.metaSeparator
import com.behnamjalali.planb.core.ui.reminderLabel
import com.behnamjalali.planb.core.ui.rememberProGuard
import com.behnamjalali.planb.core.ui.taskReminderLabel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import com.behnamjalali.planb.core.ui.R as UiR

/** Editor intents of Plan-B Pro planning (#11 deadline, #12 reminders, #14 dependencies). */
data class TaskPlanningCallbacks(
    val onDeadline: () -> Unit = {},
    val onAddReminder: (ReminderForm) -> Unit = {},
    val onRemoveReminder: (Int) -> Unit = {},
    val onNag: (Boolean, Int?) -> Unit = { _, _ -> },
    val onAddBlocker: (EntityId) -> Unit = {},
    val onRemoveBlocker: (EntityId) -> Unit = {},
)

/**
 * Deadline, extra reminders, nagging and "waits for" in the task editor. Free users see each
 * row with a Pro badge; a tap opens the Pro screen. Data set while Pro was active stays visible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskPlanningSection(
    form: TaskForm,
    blockers: List<Task>,
    candidates: List<Task>,
    callbacks: TaskPlanningCallbacks,
) {
    val guard = rememberProGuard()
    val today = PlannerLocals.today
    val numbers = PlannerLocals.numbers
    var addingReminder by rememberSaveable { mutableStateOf(false) }
    var pickingBlocker by rememberSaveable { mutableStateOf(false) }

    // #11 Deadline
    EditorRow(
        Icons.Rounded.EventBusy,
        stringResource(R.string.task_editor_deadline),
        form.deadlineDate?.let { date -> PlannerLocals.formatter.weekdayDate(date, today) + metaSeparator() + deadlineLabel(date, today) }
            ?: stringResource(R.string.task_editor_not_set),
        { guard.run(ProFeature.DEADLINES, callbacks.onDeadline) },
        trailing = { if (!guard.isPro) ProBadge() },
    )

    // #12 More reminders and nagging
    SectionTitle(stringResource(R.string.task_editor_more_reminders), showBadge = !guard.isPro)
    form.extraReminders.forEachIndexed { index, reminder ->
        val model = reminder.toModel() ?: return@forEachIndexed
        val label = taskReminderLabel(model)
        Row(Modifier.fillMaxWidth().heightIn(min = MinTouchTarget), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.sm))
            Spacer(Modifier.width(Spacing.md))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            PlannerIconButton(Icons.Rounded.Close, stringResource(R.string.task_editor_remove_reminder, label), { callbacks.onRemoveReminder(index) })
        }
    }
    if (form.canAddReminder) {
        PlannerButton(
            stringResource(R.string.task_editor_add_reminder),
            { guard.run(ProFeature.MULTIPLE_REMINDERS) { addingReminder = true } },
            style = PlannerButtonStyle.Tonal,
            icon = Icons.Rounded.Add,
        )
    } else {
        Text(
            stringResource(R.string.task_editor_reminders_full, numbers.format(TaskReminderRules.MAX_EXTRA + 1)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ProSwitchRow(
        title = stringResource(R.string.task_editor_nag),
        subtitle = stringResource(R.string.task_editor_nag_hint),
        checked = form.nag,
        isPro = guard.isPro,
        onCheckedChange = { on -> if (on) guard.run(ProFeature.MULTIPLE_REMINDERS) { callbacks.onNag(true, null) } else callbacks.onNag(false, null) },
    )
    if (form.nag) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TaskReminderRules.NAG_INTERVALS.forEach { minutes ->
                PlannerChip(
                    stringResource(R.string.task_editor_nag_every, numbers.format(minutes)),
                    form.nagInterval == minutes,
                    { callbacks.onNag(true, minutes) },
                )
            }
        }
    }

    // #14 Waits for
    SectionTitle(stringResource(R.string.task_editor_blocked_by), showBadge = !guard.isPro)
    blockers.forEach { blocker ->
        Row(Modifier.fillMaxWidth().heightIn(min = MinTouchTarget), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (blocker.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                contentDescription = if (blocker.isCompleted) stringResource(R.string.task_editor_blocker_done) else null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.sm),
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                blocker.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (blocker.isCompleted) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PlannerIconButton(Icons.Rounded.Close, stringResource(R.string.task_editor_remove_blocker, blocker.title), { callbacks.onRemoveBlocker(blocker.id) })
        }
    }
    PlannerButton(
        stringResource(R.string.task_editor_add_blocker),
        { guard.run(ProFeature.DEPENDENCIES) { pickingBlocker = true } },
        style = PlannerButtonStyle.Tonal,
        icon = Icons.Rounded.Lock,
    )

    if (addingReminder) {
        AddReminderDialog(form, onDismiss = { addingReminder = false }) {
            callbacks.onAddReminder(it)
            addingReminder = false
        }
    }
    if (pickingBlocker) {
        DependencyPickerDialog(
            candidates = candidates.filter { it.id !in form.blockedBy },
            onDismiss = { pickingBlocker = false },
        ) {
            callbacks.onAddBlocker(it)
            pickingBlocker = false
        }
    }
}

@Composable
private fun SectionTitle(title: String, showBadge: Boolean) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f, fill = false))
        if (showBadge) ProBadge()
    }
}

/** Chooses one extra reminder: before the planned time, before the deadline, or at a date and time. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddReminderDialog(form: TaskForm, onDismiss: () -> Unit, onAdd: (ReminderForm) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(if (form.due != null) TaskReminderKind.OFFSET else if (form.deadline != null) TaskReminderKind.DEADLINE else TaskReminderKind.ABSOLUTE) }
    var offset by rememberSaveable { mutableStateOf(60) }
    var date by rememberSaveable { mutableStateOf<Long?>(null) }
    var time by rememberSaveable { mutableStateOf<Int?>(null) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    val result = when (kind) {
        TaskReminderKind.OFFSET -> ReminderForm(kind.name, offset = offset).takeIf { form.due != null }
        TaskReminderKind.DEADLINE -> ReminderForm(kind.name, offset = offset).takeIf { form.deadline != null }
        TaskReminderKind.ABSOLUTE -> {
            val d = date
            val t = time
            if (d == null || t == null) {
                null
            } else {
                val at = LocalDate.ofEpochDay(d).atTime(LocalTime.ofSecondOfDay(t.toLong())).atZone(ZoneId.systemDefault()).toInstant()
                ReminderForm(kind.name, at = at.toEpochMilli())
            }
        }
    }
    PlannerDialog(
        title = stringResource(R.string.task_editor_add_reminder),
        onDismiss = onDismiss,
        confirmLabel = stringResource(UiR.string.ui_done),
        confirmEnabled = result != null,
        onConfirm = { result?.let(onAdd) },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf(
                    TaskReminderKind.OFFSET to UiR.string.reminder_kind_due,
                    TaskReminderKind.DEADLINE to UiR.string.reminder_kind_deadline,
                    TaskReminderKind.ABSOLUTE to UiR.string.reminder_kind_absolute,
                ).forEach { (k, label) ->
                    PlannerChip(stringResource(label), kind == k, {
                        kind = k
                        offset = if (k == TaskReminderKind.DEADLINE) 1440 else 60
                    })
                }
            }
            when (kind) {
                TaskReminderKind.OFFSET, TaskReminderKind.DEADLINE -> {
                    val missing = if (kind == TaskReminderKind.OFFSET) form.due == null else form.deadline == null
                    if (missing) {
                        Text(
                            stringResource(if (kind == TaskReminderKind.OFFSET) R.string.task_editor_reminder_needs_due else R.string.task_editor_reminder_needs_deadline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            val offsets = if (kind == TaskReminderKind.OFFSET) ExtraReminderOffsets else DeadlineReminderOffsets
                            offsets.forEach { o ->
                                PlannerChip(
                                    if (kind == TaskReminderKind.OFFSET) reminderLabel(o) else deadlineReminderLabel(o),
                                    offset == o,
                                    { offset = o },
                                )
                            }
                        }
                    }
                }
                TaskReminderKind.ABSOLUTE -> {
                    EditorRow(
                        Icons.Rounded.EventBusy,
                        stringResource(R.string.task_editor_reminder_date),
                        date?.let { formatter.weekdayDate(LocalDate.ofEpochDay(it), today) } ?: stringResource(R.string.task_editor_not_set),
                        { picker = "date" },
                    )
                    EditorRow(
                        Icons.Rounded.NotificationsActive,
                        stringResource(R.string.task_editor_reminder_time),
                        time?.let { formatter.time(LocalTime.ofSecondOfDay(it.toLong())) } ?: stringResource(R.string.task_editor_not_set),
                        { picker = "time" },
                    )
                }
            }
        }
    }
    when (picker) {
        "date" -> PlannerDatePickerDialog(
            initial = date?.let(LocalDate::ofEpochDay) ?: form.due,
            onDismiss = { picker = null },
            onConfirm = {
                date = it?.toEpochDay()
                picker = null
            },
            allowClear = false,
        )
        "time" -> PlannerTimePickerDialog(
            initial = time?.let { LocalTime.ofSecondOfDay(it.toLong()) } ?: form.dueAt,
            onDismiss = { picker = null },
            onConfirm = {
                time = it?.toSecondOfDay()
                picker = null
            },
            allowClear = false,
        )
    }
}

/** Picks a task this one waits for; the list can be searched (Persian or English, either keyboard). */
@Composable
private fun DependencyPickerDialog(candidates: List<Task>, onDismiss: () -> Unit, onPick: (EntityId) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalized = SearchNormalizer.normalize(query)
    val shown = candidates.filter { normalized.isBlank() || SearchNormalizer.normalize(it.title).contains(normalized) }.take(MAX_CANDIDATES)
    PlannerDialog(
        title = stringResource(R.string.task_editor_pick_blocker),
        onDismiss = onDismiss,
        confirmLabel = stringResource(UiR.string.ui_cancel),
        onConfirm = onDismiss,
        dismissLabel = "",
    ) {
        PlannerSearchBar(query = query, onQueryChange = { query = it }, placeholder = stringResource(R.string.task_editor_blocker_search))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (shown.isEmpty()) {
                Text(stringResource(R.string.task_editor_no_candidates), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            shown.forEach { task ->
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinTouchTarget)
                        .clickable(role = Role.Button) { onPick(task.id) }
                        .padding(vertical = Spacing.md),
                )
            }
        }
    }
}

private const val MAX_CANDIDATES = 100
