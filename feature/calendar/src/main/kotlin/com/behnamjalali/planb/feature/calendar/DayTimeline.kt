package com.behnamjalali.planb.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.MinTouchTarget
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.metaSeparator
import java.time.LocalDate

private val TimeColumn = 56.dp
private val RailWidth = 44.dp
private val Bubble = 36.dp

/**
 * The vertical day timeline (Plan-B Pro #7): events, time-blocked and timed tasks and device
 * events in time order on one rail, with colored icons, the free time between them ("1 hr 30
 * min free"), a "now" marker on today, and tasks checked off in place. All-day items and tasks
 * without a time sit in an "Anytime" group on top.
 */
@Composable
internal fun DayTimelineView(
    state: CalendarUiState,
    date: LocalDate,
    callbacks: CalendarCallbacks,
    contentPadding: PaddingValues,
    shown: ShownDecorations,
    nowMinute: Int,
) {
    val today = PlannerLocals.today
    val placed = remember(state.items, state.zone, date) { placeDay(state, date) }
    val timed = placed.filterNot { it.isAllDay() }
    val allDay = placed.filter { it.isAllDay() }
    val anytimeTasks = state.itemsOn(date).tasks.filter { it.scheduledStart == null && it.dueTime == null }
    val entries = remember(timed, nowMinute, date, today) { TimelineBuilder.build(timed, nowMinute.takeIf { date == today && it >= 0 }) }
    var deviceDialog by remember { mutableStateOf<Placed.Device?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = contentPadding.calculateBottomPadding() + 96.dp),
    ) {
        item(key = "details", contentType = "details") {
            IranDayDetails(state, date, shown, Modifier.padding(vertical = Spacing.xs))
        }
        if (allDay.isNotEmpty() || anytimeTasks.isNotEmpty()) {
            item(key = "anytime_header", contentType = "header") {
                Text(
                    stringResource(R.string.calendar_anytime),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs).semantics { heading() },
                )
            }
            items(allDay, key = { "ad_" + it.key }, contentType = { "anytime" }) { item ->
                TimelineRow(item, timeLabel = null, callbacks = callbacks, onDevice = { deviceDialog = it })
            }
            items(anytimeTasks, key = { "at_${it.id}" }, contentType = { "anytime" }) { task ->
                TimelineRow(Placed.TaskBlock(task, date, 0, 0, block = false), timeLabel = null, callbacks = callbacks, onDevice = {}, anytime = true)
            }
        }
        if (entries.isEmpty() && allDay.isEmpty() && anytimeTasks.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                Text(
                    stringResource(R.string.calendar_timeline_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.lg),
                )
            }
        }
        if (timed.isNotEmpty() || entries.isNotEmpty()) {
            item(key = "timed_header", contentType = "header") { Spacer(Modifier.height(Spacing.sm)) }
        }
        items(entries, key = { it.key }, contentType = { it::class.simpleName }) { entry ->
            when (entry) {
                is TimelineEntry.Item -> TimelineRow(
                    entry.placed,
                    timeLabel = PlannerLocals.formatter.time(TimeBlocks.timeOf(entry.placed.startMinute)),
                    callbacks = callbacks,
                    onDevice = { deviceDialog = it },
                )
                is TimelineEntry.Gap -> GapRow(entry)
                is TimelineEntry.Now -> NowRow(entry.minute)
            }
        }
    }
    deviceDialog?.let { item -> DeviceEventDialog(item.item, onDismiss = { deviceDialog = null }, onImport = { callbacks.onImportDevice(item.item); deviceDialog = null }) }
}

/** Draws the rail (a vertical line through the icon column) behind a row. */
private fun Modifier.rail(color: Color, rtl: Boolean, dashed: Boolean = false): Modifier = drawBehind {
    val center = (TimeColumn + RailWidth / 2).toPx()
    val x = if (rtl) size.width - center else center
    drawLine(
        color,
        Offset(x, 0f),
        Offset(x, size.height),
        strokeWidth = 2.dp.toPx(),
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null,
    )
}

@Composable
private fun TimelineRow(item: Placed, timeLabel: String?, callbacks: CalendarCallbacks, onDevice: (Placed.Device) -> Unit, anytime: Boolean = false) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scheme = MaterialTheme.colorScheme
    val formatter = PlannerLocals.formatter
    val colors = blockColors(item)
    val task = (item as? Placed.TaskBlock)?.task
    val icon: ImageVector = when (item) {
        is Placed.Event -> Icons.Rounded.Event
        is Placed.Device -> Icons.Rounded.EventAvailable
        is Placed.TaskBlock -> if (item.block) Icons.Rounded.Schedule else Icons.Rounded.TaskAlt
    }
    val details = buildList {
        if (!item.isAllDay() && !anytime) {
            add(formatter.timeRange(TimeBlocks.timeOf(item.startMinute), TimeBlocks.timeOf(item.endMinute)))
            add(formatter.duration(item.endMinute - item.startMinute))
        } else if (item.isAllDay()) {
            add(stringResource(R.string.calendar_all_day))
        }
        if (item is Placed.Device) add(stringResource(R.string.calendar_device_event, item.item.calendarName))
    }.joinToString(metaSeparator())
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .rail(scheme.outlineVariant, rtl, dashed = anytime || item.isAllDay()),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(TimeColumn).padding(top = Spacing.md), contentAlignment = Alignment.TopCenter) {
            if (timeLabel != null) {
                Text(timeLabel, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Box(Modifier.width(RailWidth).padding(vertical = Spacing.xs), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .size(Bubble)
                    .background(colors.container, CircleShape)
                    .border(2.dp, colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(IconSize.sm))
            }
        }
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = MinTouchTarget + Spacing.sm)
                .padding(vertical = Spacing.xs)
                .clip(RoundedCornerShape(Radius.sm))
                .clickable { openPlaced(item, callbacks, onDevice) }
                .padding(start = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    placedTitle(item),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (task?.isCompleted == true) scheme.onSurfaceVariant else scheme.onSurface,
                )
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2)
                }
            }
            if (task != null) CheckButton(task, callbacks)
        }
    }
}

@Composable
private fun CheckButton(task: Task, callbacks: CalendarCallbacks) {
    val done = task.isCompleted
    IconButton(onClick = { callbacks.onToggleTask(task.id, !done) }) {
        Icon(
            if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = stringResource(if (done) R.string.calendar_mark_not_done else R.string.calendar_mark_done, task.title),
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun GapRow(gap: TimelineEntry.Gap) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = gapHeight(gap.minutes))
            .rail(scheme.outlineVariant, rtl, dashed = true),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(TimeColumn + RailWidth))
        Text(
            stringResource(R.string.calendar_free_gap, PlannerLocals.formatter.duration(gap.minutes)),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier
                .background(scheme.surfaceContainer, RoundedCornerShape(Radius.pill))
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

/** Longer gaps get a little more room, up to a limit, so the day's rhythm is visible. */
private fun gapHeight(minutes: Int): Dp = (36 + (minutes / 30) * 6).coerceAtMost(84).dp

@Composable
private fun NowRow(minute: Int) {
    val color = MaterialTheme.colorScheme.error
    val time = PlannerLocals.formatter.time(TimeBlocks.timeOf(minute))
    val label = stringResource(R.string.calendar_now)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(TimeColumn), contentAlignment = Alignment.Center) {
            Text(time, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
        }
        Box(Modifier.width(RailWidth), contentAlignment = Alignment.Center) {
            Box(Modifier.size(12.dp).background(color, CircleShape))
        }
        Box(Modifier.weight(1f).height(2.dp).background(color))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(start = Spacing.sm),
        )
        Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = color, modifier = Modifier.padding(start = Spacing.xs).size(IconSize.xs))
    }
}
