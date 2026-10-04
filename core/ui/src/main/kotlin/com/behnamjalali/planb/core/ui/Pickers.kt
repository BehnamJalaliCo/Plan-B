package com.behnamjalali.planb.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.core.datetime.CalendarMonth
import com.behnamjalali.planb.core.datetime.MonthGrid
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.theme.IconSize
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
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    val isSelected = cell.date == selected
                    val isToday = cell.date == today
                    val count = markers(cell.date)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) scheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, scheme.primary, CircleShape) else Modifier)
                            .selectable(selected = isSelected, role = Role.Button, onClick = { onSelect(cell.date) })
                            .semantics { contentDescription = formatter.fullDate(cell.date) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            formatter.dayNumber(cell.date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                isSelected -> scheme.onPrimary
                                !cell.inMonth -> scheme.outline
                                else -> scheme.onSurface
                            },
                        )
                        if (count > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 5.dp)
                                    .size(4.dp)
                                    .background(if (isSelected) scheme.onPrimary else scheme.tertiary, CircleShape),
                            )
                        }
                    }
                }
            }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.xl),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        title = { Text(formatter.fullDate(selected), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                MonthHeader(month, { monthOffset-- }, { monthOffset++ })
                MonthGridView(month, selected, { selectedEpoch = it.toEpochDay() })
                TextButton(onClick = {
                    selectedEpoch = today.toEpochDay()
                    monthOffset = formatter.monthOf(today).let { m -> (m.year - baseMonth.year) * 12 + (m.month - baseMonth.month) }
                }) { Text(stringResource(R.string.ui_today)) }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.ui_done)) } },
        dismissButton = {
            Row {
                if (allowClear) TextButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.ui_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerTimePickerDialog(
    initial: LocalTime?,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime?) -> Unit,
    allowClear: Boolean = true,
) {
    val start = initial ?: LocalTime.of(9, 0)
    val state = rememberTimePickerState(start.hour, start.minute, is24Hour = true)
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
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AccentColor.entries.forEach { accent ->
            val tones = PlanBTheme.colors.accent(accent)
            val isSelected = accent == selected
            val name = accentName(accent)
            Surface(
                shape = CircleShape,
                color = tones.strong,
                border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                modifier = Modifier
                    .size(44.dp)
                    .selectable(isSelected, role = Role.RadioButton) { onSelect(accent) }
                    .semantics { contentDescription = name },
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
