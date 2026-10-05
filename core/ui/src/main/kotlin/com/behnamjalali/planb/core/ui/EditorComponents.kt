package com.behnamjalali.planb.core.ui

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.core.common.Digits
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerSurface
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.MinTouchTarget
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.datetime.CalendarEngines
import com.behnamjalali.planb.core.datetime.RecurrenceEngine
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.RecurrenceBasis
import com.behnamjalali.planb.core.model.RecurrenceFrequency
import com.behnamjalali.planb.core.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate

/** Label/value row that opens a picker; 56dp min height for touch. */
@Composable
fun EditorRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.md))
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        trailing?.invoke()
    }
}

/** Reminder offsets (minutes before the due moment) offered in editors. */
val ReminderOffsets: List<Int?> = listOf(null, 0, 5, 10, 15, 30, 60, 120, 1440)

@Composable
fun reminderLabel(offset: Int?): String {
    val numbers = PlannerLocals.numbers
    return when {
        offset == null -> stringResource(R.string.reminder_none)
        offset == 0 -> stringResource(R.string.reminder_at_time)
        offset == 1440 -> stringResource(R.string.reminder_day_before)
        offset > 1440 && offset % 1440 == 0 -> pluralStringResource(R.plurals.reminder_days_before, offset / 1440, numbers.format(offset / 1440))
        offset % 60 == 0 -> pluralStringResource(R.plurals.reminder_hours_before, offset / 60, numbers.format(offset / 60))
        else -> pluralStringResource(R.plurals.reminder_minutes_before, offset, numbers.format(offset))
    }
}

@Composable
fun ReminderMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Int?) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        ReminderOffsets.forEach { offset ->
            DropdownMenuItem(text = { Text(reminderLabel(offset)) }, onClick = {
                onSelect(offset)
                onDismiss()
            })
        }
    }
}

@Composable
fun calendarSystemLabel(system: CalendarSystem): String = stringResource(
    if (system == CalendarSystem.JALALI) R.string.calendar_jalali else R.string.calendar_gregorian,
)

/** The working week used by the "weekdays" preset for the given calendar. */
fun workWeek(system: CalendarSystem): Set<DayOfWeek> =
    if (system == CalendarSystem.JALALI) {
        setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)
    } else {
        setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    }

/** Human-readable summary of a recurrence rule in the current language. */
@Composable
fun recurrenceSummary(rule: RecurrenceRule?): String {
    if (rule == null) return stringResource(R.string.repeat_none)
    val formatter = PlannerLocals.formatter
    val numbers = PlannerLocals.numbers
    if (rule.basis == RecurrenceBasis.COMPLETION) {
        // "3 days after completion" (Plan-B Pro #4); weekdays play no part.
        var text = pluralStringResource(
            when (rule.frequency) {
                RecurrenceFrequency.DAILY -> R.plurals.repeat_summary_after_days
                RecurrenceFrequency.WEEKLY -> R.plurals.repeat_summary_after_weeks
                RecurrenceFrequency.MONTHLY -> R.plurals.repeat_summary_after_months
                RecurrenceFrequency.YEARLY -> R.plurals.repeat_summary_after_years
            },
            rule.interval,
            numbers.format(rule.interval),
        )
        rule.until?.let { text = stringResource(R.string.repeat_summary_until, text, formatter.mediumDate(it)) }
        rule.count?.let { text = stringResource(R.string.repeat_summary_count, text, numbers.format(it)) }
        return text
    }
    val base = when {
        rule.frequency == RecurrenceFrequency.WEEKLY && rule.interval == 1 && rule.weekdays == workWeek(rule.calendarSystem) ->
            stringResource(R.string.repeat_weekdays)
        rule.interval == 1 -> stringResource(
            when (rule.frequency) {
                RecurrenceFrequency.DAILY -> R.string.repeat_daily
                RecurrenceFrequency.WEEKLY -> R.string.repeat_weekly
                RecurrenceFrequency.MONTHLY -> R.string.repeat_monthly
                RecurrenceFrequency.YEARLY -> R.string.repeat_yearly
            },
        )
        else -> stringResource(
            when (rule.frequency) {
                RecurrenceFrequency.DAILY -> R.string.repeat_summary_every_n_days
                RecurrenceFrequency.WEEKLY -> R.string.repeat_summary_every_n_weeks
                RecurrenceFrequency.MONTHLY -> R.string.repeat_summary_every_n_months
                RecurrenceFrequency.YEARLY -> R.string.repeat_summary_every_n_years
            },
            numbers.format(rule.interval),
        )
    }
    var text = base
    val position = rule.setPosition
    if (rule.frequency == RecurrenceFrequency.MONTHLY && rule.weekdays.isNotEmpty() && position != null) {
        // "Every month on the second Monday" (Plan-B Pro #4).
        val separator = stringResource(R.string.ui_list_separator)
        val days = formatter.weekdays().filter { it in rule.weekdays }.joinToString(separator) { formatter.weekdayName(it) }
        text = stringResource(R.string.repeat_summary_position, text, positionLabel(position), days)
    } else if (
        (rule.frequency == RecurrenceFrequency.WEEKLY && rule.weekdays.isNotEmpty() && rule.weekdays != workWeek(rule.calendarSystem)) ||
        (rule.frequency == RecurrenceFrequency.MONTHLY && rule.weekdays.isNotEmpty())
    ) {
        val separator = stringResource(R.string.ui_list_separator)
        val days = formatter.weekdays().filter { it in rule.weekdays }.joinToString(separator) {
            formatter.weekdayShort(it)
        }
        text = stringResource(R.string.repeat_summary_on, text, days)
    }
    rule.until?.let { text = stringResource(R.string.repeat_summary_until, text, formatter.mediumDate(it)) }
    rule.count?.let { text = stringResource(R.string.repeat_summary_count, text, numbers.format(it)) }
    return text
}

enum class RecurrencePreset { NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY, CUSTOM }

fun presetRule(preset: RecurrencePreset, system: CalendarSystem): RecurrenceRule? = when (preset) {
    RecurrencePreset.NONE, RecurrencePreset.CUSTOM -> null
    RecurrencePreset.DAILY -> RecurrenceRule(RecurrenceFrequency.DAILY, calendarSystem = system)
    RecurrencePreset.WEEKDAYS -> RecurrenceRule(RecurrenceFrequency.WEEKLY, weekdays = workWeek(system), calendarSystem = system)
    RecurrencePreset.WEEKLY -> RecurrenceRule(RecurrenceFrequency.WEEKLY, calendarSystem = system)
    RecurrencePreset.MONTHLY -> RecurrenceRule(RecurrenceFrequency.MONTHLY, calendarSystem = system)
    RecurrencePreset.YEARLY -> RecurrenceRule(RecurrenceFrequency.YEARLY, calendarSystem = system)
}

@Composable
fun RecurrenceMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (RecurrencePreset) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        RecurrencePreset.entries.forEach { preset ->
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            when (preset) {
                                RecurrencePreset.NONE -> R.string.repeat_none
                                RecurrencePreset.DAILY -> R.string.repeat_daily
                                RecurrencePreset.WEEKDAYS -> R.string.repeat_weekdays
                                RecurrencePreset.WEEKLY -> R.string.repeat_weekly
                                RecurrencePreset.MONTHLY -> R.string.repeat_monthly
                                RecurrencePreset.YEARLY -> R.string.repeat_yearly
                                RecurrencePreset.CUSTOM -> R.string.repeat_custom
                            },
                        ),
                    )
                },
                onClick = {
                    onSelect(preset)
                    onDismiss()
                },
            )
        }
    }
}

private enum class EndMode { NEVER, DATE, COUNT }

/** The positions offered for "the n-th weekday of the month" (Plan-B Pro #4): first to fifth, and last. */
val RecurrencePositions: List<Int> = listOf(1, 2, 3, 4, 5, -1)

/** "second", "last", … for a [RecurrenceRule.setPosition]. */
@Composable
fun positionLabel(position: Int): String {
    val names = stringArrayResource(R.array.repeat_positions)
    return when (position) {
        in 1..5 -> names[position - 1]
        -1 -> names[5]
        else -> stringResource(R.string.repeat_position_from_end, names[(-position - 1).coerceIn(0, 4)])
    }
}

/** The ordinal of [date]'s weekday in its month of [system] (5 reads as "last"). */
private fun defaultPosition(date: LocalDate, system: CalendarSystem): Int {
    val day = CalendarEngines.of(system).toCalendarDate(date).day
    val ordinal = (day - 1) / 7 + 1
    return if (ordinal >= 5) -1 else ordinal
}

/**
 * Full custom recurrence editor: interval, unit, weekdays and end condition. Plan-B Pro #4 adds
 * "the n-th weekday of the month" for monthly rules, "count from completion" when
 * [allowAfterCompletion] (tasks), and "every N weeks" counted in the user's own weeks. Free
 * users see those options with a Pro badge; choosing one opens the Pro screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomRecurrenceDialog(
    initial: RecurrenceRule?,
    calendarSystem: CalendarSystem,
    anchor: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (RecurrenceRule) -> Unit,
    allowAfterCompletion: Boolean = false,
) {
    val formatter = PlannerLocals.formatter
    val guard = rememberProGuard()
    var frequency by rememberSaveable { mutableStateOf(initial?.frequency ?: RecurrenceFrequency.WEEKLY) }
    var intervalText by rememberSaveable { mutableStateOf((initial?.interval ?: 1).toString()) }
    var weekdays by rememberSaveable { mutableStateOf((initial?.weekdays ?: setOf(anchor.dayOfWeek)).map { it.value }.toSet()) }
    var endMode by rememberSaveable {
        mutableStateOf(
            when {
                initial?.until != null -> EndMode.DATE
                initial?.count != null -> EndMode.COUNT
                else -> EndMode.NEVER
            },
        )
    }
    var untilEpoch by rememberSaveable { mutableLongStateOf((initial?.until ?: anchor.plusMonths(3)).toEpochDay()) }
    var countText by rememberSaveable { mutableStateOf((initial?.count ?: 10).toString()) }
    var pickingUntil by rememberSaveable { mutableStateOf(false) }
    var afterCompletion by rememberSaveable { mutableStateOf(initial?.basis == RecurrenceBasis.COMPLETION) }
    var byWeekday by rememberSaveable { mutableStateOf(initial?.frequency == RecurrenceFrequency.MONTHLY && initial.setPosition != null) }
    var position by rememberSaveable { mutableIntStateOf(initial?.setPosition ?: defaultPosition(anchor, calendarSystem)) }
    var positionDay by rememberSaveable { mutableIntStateOf((initial?.weekdays?.firstOrNull() ?: anchor.dayOfWeek).value) }

    val interval = Digits.toLatin(intervalText).toIntOrNull()?.takeIf { it in 1..999 }
    val count = Digits.toLatin(countText).toIntOrNull()?.takeIf { it in 1..9999 }
    val completion = allowAfterCompletion && afterCompletion
    val monthlyWeekday = !completion && frequency == RecurrenceFrequency.MONTHLY && byWeekday
    val valid = interval != null && (endMode != EndMode.COUNT || count != null) &&
        (completion || frequency != RecurrenceFrequency.WEEKLY || weekdays.isNotEmpty())
    val rule = if (!valid) {
        null
    } else {
        RecurrenceRule(
            frequency = frequency,
            interval = interval!!,
            weekdays = when {
                completion -> emptySet()
                frequency == RecurrenceFrequency.WEEKLY -> weekdays.map { DayOfWeek.of(it) }.toSet()
                monthlyWeekday -> setOf(DayOfWeek.of(positionDay))
                else -> emptySet()
            },
            calendarSystem = calendarSystem,
            until = if (endMode == EndMode.DATE) LocalDate.ofEpochDay(untilEpoch) else null,
            count = if (endMode == EndMode.COUNT) count else null,
            setPosition = if (monthlyWeekday) position else null,
            basis = if (completion) RecurrenceBasis.COMPLETION else RecurrenceBasis.SCHEDULE,
            // "Every N weeks" counts the user's own weeks (Pro); free rules keep the calendar's week.
            weekStart = formatter.firstDayOfWeek.takeIf {
                guard.isPro && !completion && frequency == RecurrenceFrequency.WEEKLY && (interval ?: 1) > 1 &&
                    it != RecurrenceEngine.defaultWeekStart(calendarSystem)
            },
        )
    }

    PlannerDialog(
        title = stringResource(R.string.repeat_custom_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.ui_done),
        confirmEnabled = rule != null,
        onConfirm = { rule?.let(onConfirm) },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            // The rule in plain words, as it will be saved.
            PlannerSurface(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(rule?.let { recurrenceSummary(it) } ?: stringResource(R.string.repeat_invalid), style = MaterialTheme.typography.titleSmall)
            }
            PlannerTextField(
                value = intervalText,
                onValueChange = { intervalText = it.filter { c -> c.isDigit() }.take(3) },
                label = stringResource(R.string.repeat_every),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                RecurrenceFrequency.entries.forEach { f ->
                    PlannerChip(
                        stringResource(
                            when (f) {
                                RecurrenceFrequency.DAILY -> R.string.repeat_unit_days
                                RecurrenceFrequency.WEEKLY -> R.string.repeat_unit_weeks
                                RecurrenceFrequency.MONTHLY -> R.string.repeat_unit_months
                                RecurrenceFrequency.YEARLY -> R.string.repeat_unit_years
                            },
                        ),
                        f == frequency,
                        { frequency = f },
                    )
                }
            }
            if (allowAfterCompletion) {
                ProSwitchRow(
                    title = stringResource(R.string.repeat_after_completion),
                    subtitle = stringResource(R.string.repeat_after_completion_hint),
                    checked = afterCompletion,
                    isPro = guard.isPro,
                    onCheckedChange = { on -> if (on) guard.run(ProFeature.ADVANCED_RECURRENCE) { afterCompletion = true } else afterCompletion = false },
                )
            }
            if (frequency == RecurrenceFrequency.WEEKLY && !completion) {
                Text(stringResource(R.string.repeat_on_days), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    formatter.weekdays().forEach { day ->
                        val selected = day.value in weekdays
                        PlannerChip(formatter.weekdayShort(day), selected, {
                            weekdays = if (selected) weekdays - day.value else weekdays + day.value
                        })
                    }
                }
                if (guard.isPro && (interval ?: 1) > 1) {
                    Text(
                        stringResource(R.string.repeat_week_starts, formatter.weekdayName(formatter.firstDayOfWeek)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (frequency == RecurrenceFrequency.MONTHLY && !completion) {
                Column(Modifier.selectableGroup()) {
                    val dayOfMonth = CalendarEngines.of(calendarSystem).toCalendarDate(anchor).day
                    ChoiceRow(stringResource(R.string.repeat_monthly_on_day, formatter.numbers.format(dayOfMonth)), !byWeekday) { byWeekday = false }
                    ChoiceRow(stringResource(R.string.repeat_monthly_on_weekday), byWeekday, trailing = { if (!guard.isPro) ProBadge() }) {
                        guard.run(ProFeature.ADVANCED_RECURRENCE) { byWeekday = true }
                    }
                }
                if (byWeekday) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        RecurrencePositions.forEach { p -> PlannerChip(positionLabel(p), p == position, { position = p }) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        formatter.weekdays().forEach { day ->
                            PlannerChip(formatter.weekdayShort(day), day.value == positionDay, { positionDay = day.value })
                        }
                    }
                    if (position == 5) {
                        Text(
                            stringResource(R.string.repeat_fifth_hint, formatter.weekdayName(DayOfWeek.of(positionDay))),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (frequency == RecurrenceFrequency.MONTHLY || frequency == RecurrenceFrequency.YEARLY) {
                Text(
                    stringResource(R.string.repeat_calendar_hint, calendarSystemLabel(calendarSystem)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(stringResource(R.string.repeat_ends), style = MaterialTheme.typography.labelLarge)
            Column(Modifier.selectableGroup()) {
                EndMode.entries.forEach { mode ->
                    ChoiceRow(
                        when (mode) {
                            EndMode.NEVER -> stringResource(R.string.repeat_ends_never)
                            EndMode.DATE -> if (endMode == EndMode.DATE) {
                                formatter.mediumDate(LocalDate.ofEpochDay(untilEpoch))
                            } else {
                                stringResource(R.string.repeat_ends_on)
                            }
                            EndMode.COUNT -> stringResource(R.string.repeat_ends_after)
                        },
                        mode == endMode,
                        trailing = {
                            if (mode == EndMode.DATE && endMode == EndMode.DATE) {
                                androidx.compose.material3.TextButton(onClick = { pickingUntil = true }) {
                                    Text(stringResource(R.string.ui_pick_date))
                                }
                            }
                        },
                    ) { endMode = mode }
                }
            }
            if (endMode == EndMode.COUNT) {
                PlannerTextField(
                    value = countText,
                    onValueChange = { countText = it.filter { c -> c.isDigit() }.take(4) },
                    label = stringResource(R.string.repeat_count),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
    }
    if (pickingUntil) {
        PlannerDatePickerDialog(
            initial = LocalDate.ofEpochDay(untilEpoch),
            onDismiss = { pickingUntil = false },
            onConfirm = { date ->
                date?.let { untilEpoch = it.toEpochDay() }
                pickingUntil = false
            },
            allowClear = false,
        )
    }
}

/** A radio row of a single-choice group, 48dp tall. */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, trailing: @Composable () -> Unit = {}, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .selectable(selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Spacing.sm))
        Text(label, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** A switch row whose switch may be a Plan-B Pro option ([isPro] false shows the Pro badge). */
@Composable
fun ProSwitchRow(
    title: String,
    checked: Boolean,
    isPro: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!isPro) ProBadge()
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Shared confirmation shown when leaving an editor with unsaved changes. */
@Composable
fun DiscardChangesDialog(onDismiss: () -> Unit, onDiscard: () -> Unit) {
    PlannerDialog(
        title = stringResource(R.string.ui_discard_title),
        message = stringResource(R.string.ui_discard_message),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.ui_discard),
        dismissLabel = stringResource(R.string.ui_keep_editing),
        destructive = true,
        onConfirm = onDiscard,
    )
}

/** Shared destructive confirmation. */
@Composable
fun ConfirmDeleteDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    PlannerDialog(
        title = title,
        message = message,
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.ui_delete),
        destructive = true,
        onConfirm = onConfirm,
    )
}
