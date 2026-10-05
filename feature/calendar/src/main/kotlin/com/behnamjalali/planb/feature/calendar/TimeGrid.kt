package com.behnamjalali.planb.feature.calendar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.core.designsystem.component.rememberPlannerHaptics
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.MinTouchTarget
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.ui.PlannerLocals
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.floor
import kotlin.math.roundToInt

/** A dialog the time grid opens (accessible alternatives to dragging). */
internal sealed interface GridDialog {
    data class ScheduleAt(val task: Task, val date: LocalDate) : GridDialog
    data class Move(val task: Task, val date: LocalDate, val startMinute: Int, val minutes: Int) : GridDialog
    data class Resize(val task: Task, val date: LocalDate, val startMinute: Int, val minutes: Int) : GridDialog
}

/** Drag of a task from the tray: where the finger is, in root coordinates. */
private class TrayDrag(val task: Task, val pointer: Offset)

/** Drop target in the grid: day index and snapped start minute. */
private data class DropSlot(val dayIndex: Int, val startMinute: Int)

/**
 * The Day and Week time grids of Plan-B Pro #6. Tasks are dragged from the "Unscheduled" tray
 * onto an hour (long press, then drag), task blocks are moved by long-press dragging and resized
 * by dragging their top or bottom edge; everything snaps to 15 minutes, with haptic ticks.
 * Overlapping items sit side by side. Every drag has a TalkBack alternative (custom actions
 * "Schedule at…", "Move…", "Change length…", "Remove from schedule").
 */
@Composable
internal fun TimeGridView(
    state: CalendarUiState,
    days: List<LocalDate>,
    callbacks: CalendarCallbacks,
    contentPadding: PaddingValues,
    shown: ShownDecorations,
    nowMinute: Int,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val fontScale = density.fontScale.coerceIn(1f, 1.5f)
    val hourHeight = 60.dp * fontScale
    val gutter = 52.dp * fontScale
    val pxPerMinute = with(density) { hourHeight.toPx() } / 60f
    val haptics = rememberPlannerHaptics()
    val today = PlannerLocals.today

    val placedByDay = remember(state.items, state.zone, days) { days.map { placeDay(state, it) } }
    val unscheduled = remember(state.items, days) {
        days.flatMap { state.itemsOn(it).tasks }.filter { it.scheduledStart == null && it.dueTime == null && !it.isCompleted }.distinctBy { it.id }
    }
    val firstMinute = placedByDay.flatten().minOfOrNull { it.startMinute } ?: DEFAULT_FIRST_MINUTE
    val scroll = remember(days.first()) {
        ScrollState(((minOf(firstMinute, DEFAULT_FIRST_MINUTE) - 30).coerceAtLeast(0) * pxPerMinute).roundToInt())
    }

    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var viewportCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var trayDrag by remember { mutableStateOf<TrayDrag?>(null) }
    var dialog by remember { mutableStateOf<GridDialog?>(null) }
    var deviceDialog by remember { mutableStateOf<Placed.Device?>(null) }

    fun slotAt(rootPoint: Offset, duration: Int): DropSlot? {
        val root = rootCoords ?: return null
        val grid = gridCoords ?: return null
        if (!grid.isAttached || !root.isAttached) return null
        val local = grid.localPositionOf(root, rootPoint)
        val width = grid.size.width.toFloat()
        if (local.x < 0 || local.x > width || local.y < 0 || local.y > grid.size.height) return null
        val logicalX = if (rtl) width - local.x else local.x
        val index = floor(logicalX / (width / days.size)).toInt().coerceIn(0, days.lastIndex)
        val start = TimeBlocks.clampStart(TimeBlocks.snap(local.y / pxPerMinute - duration / 2f), duration)
        return DropSlot(index, start)
    }

    val dropTarget = trayDrag?.let { drag -> slotAt(drag.pointer, TimeBlocks.defaultDuration(drag.task)) }
    LaunchedEffect(dropTarget?.startMinute, dropTarget?.dayIndex) { if (dropTarget != null) haptics.tick() }

    // Scrolls while a tray task is held near the top or bottom edge of the hours.
    val autoScroll by rememberUpdatedState(trayDrag)
    LaunchedEffect(trayDrag != null) {
        while (autoScroll != null) {
            val drag = autoScroll ?: break
            val root = rootCoords
            val viewport = viewportCoords
            if (root != null && viewport != null && root.isAttached && viewport.isAttached) {
                val y = viewport.localPositionOf(root, drag.pointer).y
                val edge = with(density) { 48.dp.toPx() }
                when {
                    y in 0f..edge -> scroll.scrollBy(-12f)
                    y in (viewport.size.height - edge)..viewport.size.height.toFloat() -> scroll.scrollBy(12f)
                }
            }
            kotlinx.coroutines.delay(16)
        }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { rootCoords = it }) {
        Column(Modifier.fillMaxSize()) {
            if (days.size == 1) {
                IranDayDetails(state, days.first(), shown, Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs))
            }
            UnscheduledTray(
                tasks = unscheduled,
                onOpen = callbacks.onOpenTask,
                onScheduleAt = { task -> dialog = GridDialog.ScheduleAt(task, task.dueDate?.takeIf { it in days } ?: state.selected) },
                onDragStart = { task, point ->
                    haptics.longPress()
                    trayDrag = TrayDrag(task, point)
                },
                onDrag = { delta -> trayDrag = trayDrag?.let { TrayDrag(it.task, it.pointer + delta) } },
                onDragEnd = {
                    val drag = trayDrag
                    trayDrag = null
                    if (drag != null) {
                        val duration = TimeBlocks.defaultDuration(drag.task)
                        slotAt(drag.pointer, duration)?.let { slot ->
                            haptics.success()
                            callbacks.onScheduleTask(drag.task.id, days[slot.dayIndex], slot.startMinute, duration)
                        }
                    }
                },
                rootCoords = { rootCoords },
            )
            if (days.size > 1) WeekHeader(state, days, gutter, shown, callbacks)
            AllDayRow(placedByDay, days, gutter, callbacks) { deviceDialog = it }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { viewportCoords = it }
                    .verticalScroll(scroll),
            ) {
                Row(Modifier.padding(bottom = contentPadding.calculateBottomPadding() + 24.dp)) {
                    HourGutter(gutter, hourHeight)
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .height(hourHeight * 24)
                            .padding(end = Spacing.sm)
                            .onGloballyPositioned { gridCoords = it },
                    ) {
                        val columnWidth = maxWidth / days.size
                        HourLines(days.size, hourHeight, state, days, shown)
                        days.forEachIndexed { index, date ->
                            val placed = placedByDay[index].filterNot { it.isAllDay() }
                            val layout = TimeBlocks.layout(placed)
                            placed.forEach { item ->
                                androidx.compose.runtime.key(item.key) {
                                    TimeBlock(
                                        item = item,
                                        slot = layout.getValue(item),
                                        dayIndex = index,
                                        days = days,
                                        columnWidth = columnWidth,
                                        pxPerMinute = pxPerMinute,
                                        rtl = rtl,
                                        callbacks = callbacks,
                                        onDialog = { dialog = it },
                                        onDevice = { deviceDialog = it },
                                    )
                                }
                            }
                            if (date == today) NowLine(index, columnWidth, nowMinute, pxPerMinute)
                        }
                        dropTarget?.let { slot ->
                            val duration = TimeBlocks.defaultDuration(trayDrag!!.task)
                            DropPreview(slot, duration, columnWidth, pxPerMinute)
                        }
                    }
                }
            }
        }
        trayDrag?.let { drag ->
            val root = rootCoords
            if (root != null) {
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .offset { IntOffset(drag.pointer.x.roundToInt() - 40, drag.pointer.y.roundToInt() - 60) }
                        .widthIn(max = 220.dp),
                ) {
                    Text(
                        drag.task.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    )
                }
            }
        }
    }

    GridDialogs(dialog, onDismiss = { dialog = null }, callbacks = callbacks)
    deviceDialog?.let { item -> DeviceEventDialog(item.item, onDismiss = { deviceDialog = null }, onImport = { callbacks.onImportDevice(item.item); deviceDialog = null }) }
}

private const val DEFAULT_FIRST_MINUTE = 8 * 60

/** Everything of [date] that has a time, as placed items (all-day ones span the whole day). */
internal fun placeDay(state: CalendarUiState, date: LocalDate): List<Placed> {
    val items = state.itemsOn(date)
    val result = ArrayList<Placed>()
    items.events.forEach { occurrence ->
        val e = occurrence.event
        if (e.allDay || e.startTime == null) {
            result += Placed.Event(occurrence, 0, TimeBlocks.DAY_MINUTES)
        } else {
            val start = TimeBlocks.minuteOf(e.startTime!!)
            val end = e.endTime?.let(TimeBlocks::minuteOf)?.takeIf { it > start } ?: (start + 60).coerceAtMost(TimeBlocks.DAY_MINUTES)
            result += Placed.Event(occurrence, start, maxOf(end, start + 1))
        }
    }
    items.tasks.forEach { task ->
        val block = TimeBlocks.blockOn(task, date, state.zone)
        when {
            block != null -> result += Placed.TaskBlock(task, date, block.first, block.second, block = true)
            task.scheduledStart == null && task.dueTime != null -> {
                val start = TimeBlocks.minuteOf(task.dueTime!!)
                result += Placed.TaskBlock(task, date, start, (start + TimeBlocks.DEFAULT_MINUTES).coerceAtMost(TimeBlocks.DAY_MINUTES), block = false)
            }
        }
    }
    items.device.forEach { item ->
        if (item.allDay || item.startTime == null) {
            result += Placed.Device(item, 0, TimeBlocks.DAY_MINUTES)
        } else {
            val start = TimeBlocks.minuteOf(item.startTime!!)
            val end = item.endTime?.let(TimeBlocks::minuteOf)?.takeIf { it > start } ?: (start + 30)
            result += Placed.Device(item, start, end.coerceAtMost(TimeBlocks.DAY_MINUTES))
        }
    }
    return result
}

internal fun Placed.isAllDay(): Boolean = when (this) {
    is Placed.Event -> occurrence.event.allDay || occurrence.event.startTime == null
    is Placed.Device -> item.allDay || item.startTime == null
    is Placed.TaskBlock -> false
}

@Composable
private fun UnscheduledTray(
    tasks: List<Task>,
    onOpen: (EntityId) -> Unit,
    onScheduleAt: (Task) -> Unit,
    onDragStart: (Task, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    rootCoords: () -> LayoutCoordinates?,
) {
    if (tasks.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val dragStart by rememberUpdatedState(onDragStart)
    val dragMove by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    Column(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
        Row(Modifier.padding(horizontal = Spacing.screen), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.calendar_unscheduled),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                stringResource(R.string.calendar_unscheduled_hint),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            tasks.forEach { task ->
                var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                val scheduleLabel = stringResource(R.string.calendar_schedule_at)
                Surface(
                    shape = RoundedCornerShape(Radius.pill),
                    color = scheme.surfaceContainerHigh,
                    modifier = Modifier
                        .heightIn(min = MinTouchTarget)
                        .widthIn(max = 240.dp)
                        .onGloballyPositioned { coords = it }
                        .semantics {
                            customActions = listOf(CustomAccessibilityAction(scheduleLabel) { onScheduleAt(task); true })
                        }
                        .pointerInput(task.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    val root = rootCoords()
                                    val own = coords
                                    if (root != null && own != null) dragStart(task, root.localPositionOf(own, offset))
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragMove(amount)
                                },
                                onDragEnd = { dragEnd() },
                                onDragCancel = { dragEnd() },
                            )
                        }
                        .clickable { onOpen(task.id) },
                ) {
                    Row(Modifier.padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DragIndicator, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(IconSize.sm))
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            task.title,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = Spacing.sm),
                        )
                        task.estimatedMinutes?.let { minutes ->
                            Spacer(Modifier.width(Spacing.xs))
                            Text(PlannerLocals.formatter.duration(minutes), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekHeader(state: CalendarUiState, days: List<LocalDate>, gutter: Dp, shown: ShownDecorations, callbacks: CalendarCallbacks) {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(end = Spacing.sm, top = Spacing.xs)) {
        Spacer(Modifier.width(gutter))
        days.forEach { date ->
            val off = state.offDay(date, shown)
            val isToday = date == today
            val holidayNames = occasionNames(state, date, shown).filter { it.second }.joinToString { it.first }
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = MinTouchTarget)
                    .clickable { callbacks.onSelect(date); callbacks.onModeChange(CalendarMode.DAY) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = listOf(formatter.fullDate(date), holidayNames).filter { it.isNotBlank() }.joinToString(", ")
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    formatter.weekdayNarrow(date.dayOfWeek),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (off) scheme.error else scheme.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .size(32.dp)
                        .background(if (isToday) scheme.primary else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        formatter.dayNumber(date),
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            isToday -> scheme.onPrimary
                            off -> scheme.error
                            else -> scheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AllDayRow(placedByDay: List<List<Placed>>, days: List<LocalDate>, gutter: Dp, callbacks: CalendarCallbacks, onDevice: (Placed.Device) -> Unit) {
    val allDay = placedByDay.map { day -> day.filter { it.isAllDay() } }
    if (allDay.all { it.isEmpty() }) return
    Row(Modifier.fillMaxWidth().padding(end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs)) {
        Text(
            stringResource(R.string.calendar_all_day),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(gutter).padding(top = Spacing.xs),
        )
        allDay.forEach { items ->
            Column(Modifier.weight(1f).padding(horizontal = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items.take(if (days.size == 1) 4 else 2).forEach { item ->
                    val colors = blockColors(item)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 28.dp)
                            .background(colors.container, RoundedCornerShape(Radius.xs))
                            .then(if (item is Placed.Device) Modifier.border(1.dp, colors.accent, RoundedCornerShape(Radius.xs)) else Modifier)
                            .clickable { openPlaced(item, callbacks, onDevice) }
                            .padding(horizontal = Spacing.xs),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(placedTitle(item), style = MaterialTheme.typography.labelSmall, color = colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val more = items.size - (if (days.size == 1) 4 else 2)
                if (more > 0) {
                    Text("+${PlannerLocals.numbers.format(more)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HourGutter(gutter: Dp, hourHeight: Dp) {
    val formatter = PlannerLocals.formatter
    Column(Modifier.width(gutter)) {
        (0 until 24).forEach { hour ->
            Box(Modifier.height(hourHeight).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                if (hour > 0) {
                    Text(
                        formatter.time(LocalTime.of(hour, 0)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.offset(y = (-8).dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HourLines(columns: Int, hourHeight: Dp, state: CalendarUiState, days: List<LocalDate>, shown: ShownDecorations) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val offTint = MaterialTheme.colorScheme.error.copy(alpha = 0.04f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val offDays = days.map { state.offDay(it, shown) }
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val hourPx = hourHeight.toPx()
                val columnPx = size.width / columns
                offDays.forEachIndexed { index, off ->
                    if (off) {
                        val left = if (rtl) size.width - (index + 1) * columnPx else index * columnPx
                        drawRect(offTint, topLeft = Offset(left, 0f), size = androidx.compose.ui.geometry.Size(columnPx, size.height))
                    }
                }
                for (hour in 0..24) {
                    drawLine(line, Offset(0f, hour * hourPx), Offset(size.width, hour * hourPx), strokeWidth = 1f)
                    if (hour < 24) {
                        drawLine(
                            line.copy(alpha = 0.5f),
                            Offset(0f, hour * hourPx + hourPx / 2),
                            Offset(size.width, hour * hourPx + hourPx / 2),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                        )
                    }
                }
                for (column in 1 until columns) {
                    drawLine(line, Offset(column * columnPx, 0f), Offset(column * columnPx, size.height), strokeWidth = 1f)
                }
            },
    )
}

@Composable
private fun NowLine(dayIndex: Int, columnWidth: Dp, nowMinute: Int, pxPerMinute: Float) {
    if (nowMinute < 0) return
    val color = MaterialTheme.colorScheme.error
    val y = with(LocalDensity.current) { (nowMinute * pxPerMinute).toDp() }
    Box(
        Modifier
            .offset(x = columnWidth * dayIndex, y = y - 4.dp)
            .width(columnWidth)
            .height(8.dp),
    ) {
        Box(Modifier.align(Alignment.CenterStart).size(8.dp).background(color, CircleShape))
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(2.dp).background(color))
    }
}

@Composable
private fun DropPreview(slot: DropSlot, duration: Int, columnWidth: Dp, pxPerMinute: Float) {
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .offset(x = columnWidth * slot.dayIndex, y = with(density) { (slot.startMinute * pxPerMinute).toDp() })
            .width(columnWidth)
            .height(with(density) { (duration * pxPerMinute).toDp() })
            .padding(1.dp)
            .background(scheme.primary.copy(alpha = 0.18f), RoundedCornerShape(Radius.xs))
            .border(1.5.dp, scheme.primary, RoundedCornerShape(Radius.xs)),
    ) {
        Text(
            PlannerLocals.formatter.time(TimeBlocks.timeOf(slot.startMinute)),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.primary,
            modifier = Modifier.padding(Spacing.xs),
        )
    }
}

/** Colors of a block: tinted container, its text, and a strong accent for edges. */
internal data class BlockColors(val container: Color, val content: Color, val accent: Color)

@Composable
internal fun blockColors(item: Placed): BlockColors {
    val scheme = MaterialTheme.colorScheme
    return when (item) {
        is Placed.Event -> PlanBTheme.colors.accent(item.occurrence.event.color).let { BlockColors(it.container, it.onContainer, it.strong) }
        is Placed.TaskBlock -> if (item.block) {
            BlockColors(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
        } else {
            BlockColors(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.secondary)
        }
        is Placed.Device -> {
            val color = Color(item.item.color)
            BlockColors(color.copy(alpha = 0.14f).compositeOverSurface(scheme.surface), scheme.onSurface, color)
        }
    }
}

private fun Color.compositeOverSurface(surface: Color): Color {
    val a = alpha
    return Color(red * a + surface.red * (1 - a), green * a + surface.green * (1 - a), blue * a + surface.blue * (1 - a), 1f)
}

internal fun placedTitle(item: Placed): String = when (item) {
    is Placed.Event -> item.occurrence.event.title
    is Placed.TaskBlock -> item.task.title
    is Placed.Device -> item.item.title
}

internal fun openPlaced(item: Placed, callbacks: CalendarCallbacks, onDevice: (Placed.Device) -> Unit) {
    when (item) {
        is Placed.Event -> callbacks.onOpenEvent(item.occurrence.event.id)
        is Placed.TaskBlock -> callbacks.onOpenTask(item.task.id)
        is Placed.Device -> onDevice(item)
    }
}

@Composable
private fun TimeBlock(
    item: Placed,
    slot: TimeBlocks.Slot,
    dayIndex: Int,
    days: List<LocalDate>,
    columnWidth: Dp,
    pxPerMinute: Float,
    rtl: Boolean,
    callbacks: CalendarCallbacks,
    onDialog: (GridDialog) -> Unit,
    onDevice: (Placed.Device) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = rememberPlannerHaptics()
    val formatter = PlannerLocals.formatter
    val colors = blockColors(item)
    val task = (item as? Placed.TaskBlock)?.task
    val draggable = task != null && !task.isCompleted

    // Live drag state (minutes and day offset), committed when the finger lifts.
    var moveMinutes by remember { mutableFloatStateOf(0f) }
    var moveDays by remember { mutableFloatStateOf(0f) }
    var moving by remember { mutableStateOf(false) }
    var topDelta by remember { mutableFloatStateOf(0f) }
    var bottomDelta by remember { mutableFloatStateOf(0f) }
    val duration = item.endMinute - item.startMinute
    val columnPx = with(density) { columnWidth.toPx() }
    // The gesture outlives recompositions (it is keyed by the item): read the latest values.
    val current by rememberUpdatedState(item)
    val currentDay by rememberUpdatedState(dayIndex)
    val currentDays by rememberUpdatedState(days)
    val currentCallbacks by rememberUpdatedState(callbacks)

    val previewStart: Int
    val previewEnd: Int
    val previewDay: Int
    when {
        moving -> {
            previewStart = TimeBlocks.clampStart(TimeBlocks.snap(item.startMinute + moveMinutes), duration)
            previewEnd = previewStart + duration
            previewDay = (dayIndex + (moveDays / columnPx).roundToInt()).coerceIn(0, days.lastIndex)
        }
        else -> {
            previewStart = TimeBlocks.snap(item.startMinute + topDelta).coerceIn(0, item.endMinute - TimeBlocks.MIN_MINUTES)
            previewEnd = TimeBlocks.snap(item.endMinute + bottomDelta).coerceIn(previewStart + TimeBlocks.MIN_MINUTES, TimeBlocks.DAY_MINUTES)
            previewDay = dayIndex
        }
    }
    LaunchedEffect(previewStart, previewEnd, previewDay) { if (moving || topDelta != 0f || bottomDelta != 0f) haptics.tick() }

    val x = columnWidth * previewDay + columnWidth * slot.column / slot.columns
    val y = with(density) { (previewStart * pxPerMinute).toDp() }
    val height = with(density) { ((previewEnd - previewStart) * pxPerMinute).toDp() }.coerceAtLeast(18.dp)
    val width = columnWidth / slot.columns
    // Lines of text need more room with a larger font.
    val textScale = density.fontScale.coerceIn(1f, 2f)
    val roomForTime = height > 40.dp * textScale
    val showGrips = height > 56.dp * textScale

    val timeText = formatter.timeRange(TimeBlocks.timeOf(previewStart), TimeBlocks.timeOf(previewEnd))
    val moveLabel = stringResource(R.string.calendar_move_block)
    val resizeLabel = stringResource(R.string.calendar_resize_block)
    val unscheduleLabel = stringResource(R.string.calendar_unschedule)
    val deviceLabel = (item as? Placed.Device)?.let { stringResource(R.string.calendar_device_event, it.item.calendarName) }
    val description = listOfNotNull(placedTitle(item), timeText, deviceLabel).joinToString(", ")

    Box(
        Modifier
            .offset(x = x, y = y)
            .width(width)
            .height(height)
            .padding(1.dp)
            .graphicsLayer { if (moving) { shadowElevation = 12f; alpha = 0.92f } }
            .background(colors.container, RoundedCornerShape(Radius.xs))
            .then(
                if (item is Placed.Device) {
                    Modifier.border(1.dp, colors.accent.copy(alpha = 0.7f), RoundedCornerShape(Radius.xs))
                } else {
                    Modifier
                },
            )
            .drawBehind {
                // A strong edge on the reading-start side, like the event cards.
                val edge = 3.dp.toPx()
                val left = if (rtl) size.width - edge else 0f
                drawRect(colors.accent, topLeft = Offset(left, 0f), size = androidx.compose.ui.geometry.Size(edge, size.height))
            }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                onClick { openPlaced(item, callbacks, onDevice); true }
                if (draggable && item is Placed.TaskBlock) {
                    customActions = buildList {
                        add(CustomAccessibilityAction(moveLabel) { onDialog(GridDialog.Move(task!!, days[dayIndex], item.startMinute, duration)); true })
                        add(CustomAccessibilityAction(resizeLabel) { onDialog(GridDialog.Resize(task!!, days[dayIndex], item.startMinute, duration)); true })
                        if (item.block) add(CustomAccessibilityAction(unscheduleLabel) { callbacks.onUnscheduleTask(task!!.id); true })
                    }
                }
            }
            .clickable { openPlaced(item, callbacks, onDevice) }
            .then(
                if (draggable) {
                    Modifier.pointerInput(item.key) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.longPress()
                                moving = true
                                moveMinutes = 0f
                                moveDays = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                moveMinutes += amount.y / pxPerMinute
                                moveDays += if (rtl) -amount.x else amount.x
                            },
                            onDragEnd = {
                                val block = current
                                val length = block.endMinute - block.startMinute
                                val start = TimeBlocks.clampStart(TimeBlocks.snap(block.startMinute + moveMinutes), length)
                                val day = (currentDay + (moveDays / columnPx).roundToInt()).coerceIn(0, currentDays.lastIndex)
                                moving = false
                                if (start != block.startMinute || day != currentDay) {
                                    currentCallbacks.onScheduleTask((block as Placed.TaskBlock).task.id, currentDays[day], start, length)
                                }
                                moveMinutes = 0f
                                moveDays = 0f
                            },
                            onDragCancel = { moving = false; moveMinutes = 0f; moveDays = 0f },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Column(Modifier.padding(start = Spacing.sm, end = Spacing.xs, top = 2.dp)) {
            Text(placedTitle(item), style = MaterialTheme.typography.labelMedium, color = colors.content, maxLines = if (height > 64.dp * textScale) 2 else 1, overflow = TextOverflow.Ellipsis)
            if (roomForTime) {
                Text(timeText, style = MaterialTheme.typography.labelSmall, color = colors.content.copy(alpha = 0.8f), maxLines = 1)
            }
        }
        if (draggable && !moving) {
            ResizeHandle(Modifier.align(Alignment.TopCenter), colors.accent, top = true, showGrip = showGrips) { delta, done ->
                val block = current
                if (done) {
                    val start = TimeBlocks.snap(block.startMinute + topDelta).coerceIn(0, block.endMinute - TimeBlocks.MIN_MINUTES)
                    topDelta = 0f
                    if (start != block.startMinute) {
                        currentCallbacks.onScheduleTask((block as Placed.TaskBlock).task.id, currentDays[currentDay], start, block.endMinute - start)
                    }
                } else {
                    topDelta += delta / pxPerMinute
                }
            }
            ResizeHandle(Modifier.align(Alignment.BottomCenter), colors.accent, top = false, showGrip = showGrips) { delta, done ->
                val block = current
                if (done) {
                    val end = TimeBlocks.snap(block.endMinute + bottomDelta).coerceIn(block.startMinute + TimeBlocks.MIN_MINUTES, TimeBlocks.DAY_MINUTES)
                    bottomDelta = 0f
                    if (end != block.endMinute) {
                        currentCallbacks.onScheduleTask((block as Placed.TaskBlock).task.id, currentDays[currentDay], block.startMinute, end - block.startMinute)
                    }
                } else {
                    bottomDelta += delta / pxPerMinute
                }
            }
        }
    }
}

/** A grip on a block's edge; dragging it changes the start or the end. */
@Composable
private fun ResizeHandle(modifier: Modifier, color: Color, top: Boolean, showGrip: Boolean, onDrag: (delta: Float, done: Boolean) -> Unit) {
    val drag by rememberUpdatedState(onDrag)
    Box(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .pointerInput(top) {
                detectVerticalDragGestures(
                    onDragEnd = { drag(0f, true) },
                    onDragCancel = { drag(0f, true) },
                ) { change, amount ->
                    change.consume()
                    drag(amount, false)
                }
            },
        contentAlignment = if (top) Alignment.TopCenter else Alignment.BottomCenter,
    ) {
        // Small blocks keep the grab area but not the grip, which would cover their title.
        if (showGrip) Box(
            Modifier
                .padding(vertical = 3.dp)
                .width(20.dp)
                .height(3.dp)
                .background(color.copy(alpha = 0.6f), RoundedCornerShape(Radius.pill)),
        )
    }
}

@Composable
private fun GridDialogs(dialog: GridDialog?, onDismiss: () -> Unit, callbacks: CalendarCallbacks) {
    when (dialog) {
        null -> Unit
        is GridDialog.ScheduleAt -> com.behnamjalali.planb.core.ui.PlannerTimePickerDialog(
            initial = LocalTime.of(9, 0),
            onDismiss = onDismiss,
            allowClear = false,
            onConfirm = { time ->
                onDismiss()
                if (time != null) {
                    val duration = TimeBlocks.defaultDuration(dialog.task)
                    callbacks.onScheduleTask(dialog.task.id, dialog.date, TimeBlocks.snap(TimeBlocks.minuteOf(time).toFloat()), duration)
                }
            },
        )
        is GridDialog.Move -> com.behnamjalali.planb.core.ui.PlannerTimePickerDialog(
            initial = TimeBlocks.timeOf(dialog.startMinute),
            onDismiss = onDismiss,
            allowClear = false,
            onConfirm = { time ->
                onDismiss()
                if (time != null) callbacks.onScheduleTask(dialog.task.id, dialog.date, TimeBlocks.snap(TimeBlocks.minuteOf(time).toFloat()), dialog.minutes)
            },
        )
        is GridDialog.Resize -> DurationDialog(
            current = dialog.minutes,
            onDismiss = onDismiss,
            onSelect = { minutes ->
                onDismiss()
                callbacks.onScheduleTask(dialog.task.id, dialog.date, dialog.startMinute, minutes)
            },
        )
    }
}

private val DURATIONS = listOf(15, 30, 45, 60, 90, 120, 180, 240)

@Composable
private fun DurationDialog(current: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val formatter = PlannerLocals.formatter
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_duration_title)) },
        text = {
            Column {
                (DURATIONS + current).distinct().sorted().forEach { minutes ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = MinTouchTarget)
                            .clickable { onSelect(minutes) }
                            .padding(horizontal = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = minutes == current, onClick = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(formatter.duration(minutes), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.calendar_close)) }
        },
    )
}

/** Details of a device calendar event, with "Copy into Plan-B". */
@Composable
internal fun DeviceEventDialog(item: com.behnamjalali.planb.core.calendarsync.DeviceCalendarItem, onDismiss: () -> Unit, onImport: () -> Unit) {
    val formatter = PlannerLocals.formatter
    val time = if (item.allDay || item.startTime == null) {
        stringResource(R.string.calendar_all_day)
    } else {
        formatter.timeRange(item.startTime!!, item.endTime)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.EventAvailable, contentDescription = null, tint = Color(item.color)) },
        title = { Text(item.title.ifBlank { stringResource(R.string.calendar_untitled) }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(formatter.fullDate(item.date) + com.behnamjalali.planb.core.ui.metaSeparator() + time, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.calendar_device_event, item.calendarName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.calendar_import_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onImport) { Text(stringResource(R.string.calendar_import)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.calendar_close)) }
        },
    )
}
