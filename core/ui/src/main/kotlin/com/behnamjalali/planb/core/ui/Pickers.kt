package com.behnamjalali.planb.core.ui

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.behnamjalali.planb.core.datetime.CalendarMonth
import com.behnamjalali.planb.core.datetime.MonthGrid
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.MinTouchTarget
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.PlannerIcon
import java.time.LocalDate
import java.time.LocalTime

/**
 * Calendar-system aware month grid used by the date picker and the calendar
 * month view. Weekday headers and numbers follow the user's settings.
 */
@Composable
fun MonthGridView(
    month: CalendarMonth,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    markers: (LocalDate) -> Int = { 0 },
    /** Days drawn as days off (official holidays and the weekend, Plan-B Pro #2). */
    offDay: (LocalDate) -> Boolean = { false },
    /** Spoken after the date, e.g. the holiday's name. */
    extraDescription: (LocalDate) -> String? = { null },
    /** Weekday headers drawn as the weekend. */
    weekendDay: (java.time.DayOfWeek) -> Boolean = { false },
) {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    val scheme = MaterialTheme.colorScheme
    val weeks = MonthGrid.build(formatter.engine, month, formatter.firstDayOfWeek)
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            formatter.weekdays().forEach { day ->
                Text(
                    formatter.weekdayNarrow(day),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = formatter.weekdayName(day) },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (weekendDay(day)) scheme.error else scheme.onSurfaceVariant,
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    val isSelected = cell.date == selected
                    val isToday = cell.date == today
                    val count = markers(cell.date)
                    // The whole cell is the touch target, at least 48dp tall; the day's circle sits inside.
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = MinTouchTarget)
                            .aspectRatio(1f)
                            .selectable(selected = isSelected, role = Role.Button, onClick = { onSelect(cell.date) })
                            .semantics {
                                contentDescription = listOfNotNull(formatter.fullDate(cell.date), extraDescription(cell.date)).joinToString(", ")
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        DayCircle(isSelected, isToday, count > 0) {
                            Text(
                            formatter.dayNumber(cell.date),
                            style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    isSelected -> scheme.onPrimary
                                    offDay(cell.date) -> if (cell.inMonth) scheme.error else scheme.error.copy(alpha = 0.5f)
                                    !cell.inMonth -> scheme.outline
                                    else -> scheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCircle(selected: Boolean, today: Boolean, marked: Boolean, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (selected) scheme.primary else androidx.compose.ui.graphics.Color.Transparent)
            .then(if (today && !selected) Modifier.border(1.5.dp, scheme.primary, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (marked) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .size(4.dp)
                    .background(if (selected) scheme.onPrimary else scheme.tertiary, CircleShape),
            )
        }
    }
}

@Composable
fun MonthHeader(
    month: CalendarMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = PlannerLocals.formatter
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatter.monthYear(month),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.sm),
        )
        // AutoMirrored arrows: "previous" points toward the start edge in both LTR and RTL.
        PlannerIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.ui_previous_month), onPrevious)
        PlannerIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.ui_next_month), onNext)
    }
}

/** Date picker dialog supporting Jalali and Gregorian display. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlannerDatePickerDialog(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate?) -> Unit,
    allowClear: Boolean = true,
) {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    var selectedEpoch by rememberSaveable { mutableLongStateOf((initial ?: today).toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    val baseMonth = formatter.monthOf(initial ?: today)
    val month = baseMonth.plus(monthOffset)
    // Like Material's own date picker dialog, this one is up to 360dp wide with slim side padding,
    // so each day cell is a full 48dp touch target even on a 360dp-wide phone.
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = DatePickerMaxWidth),
    ) {
        Surface(shape = RoundedCornerShape(Radius.xl), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DatePickerSidePadding, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    formatter.fullDate(selected),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
                MonthHeader(month, { monthOffset-- }, { monthOffset++ })
                MonthGridView(month, selected, { selectedEpoch = it.toEpochDay() })
                FlowRow(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                    TextButton(onClick = {
                        selectedEpoch = today.toEpochDay()
                        monthOffset = formatter.monthOf(today).let { m -> (m.year - baseMonth.year) * 12 + (m.month - baseMonth.month) }
                    }) { Text(stringResource(R.string.ui_today)) }
                    Spacer(Modifier.weight(1f))
                    if (allowClear) TextButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.ui_clear)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
                    TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.ui_done)) }
                }
            }
        }
    }
}

private val DatePickerMaxWidth = 360.dp
private val DatePickerSidePadding = 12.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerTimePickerDialog(
    initial: LocalTime?,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime?) -> Unit,
    allowClear: Boolean = true,
) {
    val start = initial ?: LocalTime.of(9, 0)
    // Same clock as the app's time format: 24-hour in Persian, else the device's 12/24-hour setting.
    val is24Hour = PlannerLocals.numbers.persianDigits || DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(start.hour, start.minute, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.xl),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        title = { Text(stringResource(R.string.ui_pick_time), style = MaterialTheme.typography.titleMedium) },
        text = {
            // Clock faces are numeric layouts; keep them LTR so digits stay in clock order.
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
            ) {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(clockDialColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.ui_done)) }
        },
        dismissButton = {
            Row {
                if (allowClear) TextButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.ui_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentColorPicker(
    selected: AccentColor,
    onSelect: (AccentColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        AccentColor.entries.forEach { accent ->
            val tones = PlanBTheme.colors.accent(accent)
            val isSelected = accent == selected
            val name = accentName(accent)
            // 48dp touch target around the 44dp swatch.
            Box(
                Modifier
                    .size(MinTouchTarget)
                    .selectable(isSelected, role = Role.RadioButton) { onSelect(accent) }
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = tones.strong,
                    border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                    modifier = Modifier.size(44.dp),
                ) {
                    if (isSelected) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(IconSize.sm))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlannerIconPicker(
    selected: PlannerIcon,
    onSelect: (PlannerIcon) -> Unit,
    accent: AccentColor,
    modifier: Modifier = Modifier,
) {
    val tones = PlanBTheme.colors.accent(accent)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PlannerIcon.entries.forEach { icon ->
            val isSelected = icon == selected
            Surface(
                shape = RoundedCornerShape(Radius.sm),
                color = if (isSelected) tones.container else MaterialTheme.colorScheme.surfaceContainer,
                border = if (isSelected) BorderStroke(2.dp, tones.strong) else null,
                modifier = Modifier
                    .size(48.dp)
                    .selectable(isSelected, role = Role.RadioButton) { onSelect(icon) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon.vector,
                        contentDescription = icon.label(),
                        tint = if (isSelected) tones.onContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
