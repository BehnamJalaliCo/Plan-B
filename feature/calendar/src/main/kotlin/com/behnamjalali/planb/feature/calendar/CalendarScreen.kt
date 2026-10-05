package com.behnamjalali.planb.feature.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerEmptyState
import com.behnamjalali.planb.core.designsystem.component.PlannerErrorState
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerSegmentedControl
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.datetime.MonthGrid
import com.behnamjalali.planb.core.model.CalendarView
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.ui.MonthGridView
import com.behnamjalali.planb.core.ui.PlannerEventCard
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.PlannerTaskCard
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.core.ui.ProBadge
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProGate
import com.behnamjalali.planb.core.ui.ProTeaser
import com.behnamjalali.planb.core.ui.metaSeparator
import com.behnamjalali.planb.core.calendarsync.DeviceCalendarItem
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.MinTouchTarget
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.model.CalendarSystem
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import java.time.DayOfWeek
import java.time.LocalDate

data class CalendarCallbacks(
    val onModeChange: (CalendarMode) -> Unit = {},
    val onSelect: (LocalDate) -> Unit = {},
    val onPage: (Int) -> Unit = {},
    val onToday: () -> Unit = {},
    val onOpenEvent: (EntityId) -> Unit = {},
    val onOpenTask: (EntityId) -> Unit = {},
    val onToggleTask: (EntityId, Boolean) -> Unit = { _, _ -> },
    val onNewEvent: (LocalDate) -> Unit = {},
    /** Plan-B Pro #6: time-block a task ([startMinute] of [date], [minutes] long). */
    val onScheduleTask: (id: EntityId, date: LocalDate, startMinute: Int, minutes: Int) -> Unit = { _, _, _, _ -> },
    val onUnscheduleTask: (EntityId) -> Unit = {},
    /** Plan-B Pro #3: copy a device calendar event into Plan-B. */
    val onImportDevice: (DeviceCalendarItem) -> Unit = {},
)

@Composable
private fun modeLabel(mode: CalendarMode): String = stringResource(
    when (mode) {
        CalendarMode.DAY -> R.string.calendar_view_day
        CalendarMode.WEEK -> R.string.calendar_view_week
        CalendarMode.MONTH -> R.string.calendar_view_month
        CalendarMode.TIMELINE -> R.string.calendar_view_timeline
        CalendarMode.AGENDA -> R.string.calendar_view_agenda
    },
)

@Composable
fun CalendarScreen(
    state: CalendarUiState,
    callbacks: CalendarCallbacks,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    /** Minute of the day now (for the "now" line), or -1 to hide it. */
    nowMinute: Int = -1,
) {
    val pro = LocalProAccess.current.isPro
    val shown = shownDecorations(state)
    Column(modifier.fillMaxSize()) {
        PlannerTopBar(
            title = stringResource(R.string.calendar_title),
            actions = {
                PlannerIconButton(Icons.Rounded.Today, stringResource(R.string.calendar_today), callbacks.onToday)
                PlannerIconButton(Icons.Rounded.Add, stringResource(R.string.calendar_new_event), { callbacks.onNewEvent(state.selected) })
            },
        )
        PlannerSegmentedControl(
            options = CalendarMode.entries,
            selected = state.mode,
            onSelect = callbacks.onModeChange,
            label = { modeLabel(it) },
            modifier = Modifier.padding(horizontal = Spacing.screen).fillMaxWidth(),
        )
        when {
            state.error -> PlannerErrorState(stringResource(R.string.calendar_error))
            state.loading -> PlannerLoadingState()
            else -> {
                PeriodHeader(state, callbacks)
                val motion = PlanBTheme.motion
                AnimatedContent(
                    targetState = state.mode,
                    transitionSpec = {
                        if (motion.enabled) fadeIn(tween(180)) togetherWith fadeOut(tween(120)) else fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    },
                    label = "calendarView",
                ) { mode ->
                    when (mode) {
                        CalendarMode.MONTH -> MonthView(state, callbacks, contentPadding, shown)
                        // Plan-B Pro #6: hour grids with time blocking; the free views stay as they are.
                        CalendarMode.WEEK -> if (pro) {
                            TimeGridView(state, (0L until 7L).map { state.rangeStart.plusDays(it) }, callbacks, contentPadding, shown, nowMinute)
                        } else {
                            WeekView(state, callbacks, contentPadding)
                        }
                        CalendarMode.DAY -> if (pro) {
                            TimeGridView(state, listOf(state.selected), callbacks, contentPadding, shown, nowMinute)
                        } else {
                            DayView(state, callbacks, contentPadding)
                        }
                        CalendarMode.TIMELINE -> ProGate(
                            ProFeature.DAY_TIMELINE,
                            teaser = { ProTeaser(ProFeature.DAY_TIMELINE, Modifier.padding(Spacing.screen)) },
                        ) {
                            DayTimelineView(state, state.selected, callbacks, contentPadding, shown, nowMinute)
                        }
                        CalendarMode.AGENDA -> AgendaView(state, callbacks, contentPadding, shown)
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodHeader(state: CalendarUiState, callbacks: CalendarCallbacks) {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    val title = when (state.view) {
        CalendarView.MONTH -> formatter.monthYear(state.month)
        CalendarView.WEEK, CalendarView.AGENDA ->
            stringResource(R.string.calendar_week_range, formatter.shortDate(state.rangeStart, today), formatter.shortDate(state.rangeEnd, today))
        CalendarView.DAY -> formatter.fullDate(state.selected)
    }
    val shown = shownDecorations(state)
    val off = state.view == CalendarView.DAY && state.offDay(state.selected, shown)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Spacing.screen, end = Spacing.sm, top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = if (off) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        PlannerIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.calendar_previous), { callbacks.onPage(-1) })
        PlannerIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.calendar_next), { callbacks.onPage(1) })
    }
}

/** Horizontal swipe pages the calendar; direction follows the reading direction. */
@Composable
private fun Modifier.swipeToPage(onPage: (Int) -> Unit): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var total by remember { mutableFloatStateOf(0f) }
    return pointerInput(rtl) {
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                val threshold = 80.dp.toPx()
                if (total > threshold) onPage(if (rtl) 1 else -1)
                if (total < -threshold) onPage(if (rtl) -1 else 1)
            },
        ) { _, amount -> total += amount }
    }
}

@Composable
private fun MonthView(state: CalendarUiState, callbacks: CalendarCallbacks, contentPadding: PaddingValues, shown: ShownDecorations) {
    val resources = LocalResources.current
    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "grid", contentType = "grid") {
            PlannerCard(Modifier.fillMaxWidth().swipeToPage(callbacks.onPage), contentPadding = PaddingValues(Spacing.sm)) {
                MonthGridView(
                    month = state.month,
                    selected = state.selected,
                    onSelect = callbacks.onSelect,
                    markers = { state.itemsOn(it).count },
                    offDay = { state.offDay(it, shown) },
                    extraDescription = { date ->
                        state.occasionsOn(date).filter { it.holiday && shown.holidays }
                            .joinToString { resources.getString(it.occasion.title) }.takeIf { it.isNotEmpty() }
                    },
                    weekendDay = { shown.holidays && state.calendarSystem == CalendarSystem.JALALI && it == DayOfWeek.FRIDAY },
                )
            }
        }
        dayItems(state.selected, state.itemsOn(state.selected), callbacks, showHeader = true, state = state, shown = shown)
    }
}

@Composable
private fun WeekView(state: CalendarUiState, callbacks: CalendarCallbacks, contentPadding: PaddingValues) {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    val start = MonthGrid.weekStart(state.selected, state.firstDayOfWeek)
    LazyColumn(
        modifier = Modifier.swipeToPage(callbacks.onPage),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        (0L until 7L).map { start.plusDays(it) }.forEach { date ->
            val items = state.itemsOn(date)
            item(key = "wh_$date", contentType = "weekDay") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { callbacks.onSelect(date) }
                        .padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isToday = date == today
                    Text(
                        formatter.weekdayName(date.dayOfWeek),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(formatter.dayMonth(date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    if (items.count > 0) {
                        Text(
                            pluralStringResource(R.plurals.calendar_items_count, items.count, formatter.numbers.format(items.count)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            dayItems(date, items, callbacks, showHeader = false, showEmpty = false)
        }
    }
}

@Composable
private fun DayView(state: CalendarUiState, callbacks: CalendarCallbacks, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.swipeToPage(callbacks.onPage),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        dayItems(state.selected, state.itemsOn(state.selected), callbacks, showHeader = false, timeline = true)
        item(key = "pro_hint", contentType = "proHint") { TimeBlockingHint() }
    }
}

/** For free users: one quiet line under the day that tells about time blocking (Plan-B Pro #6). */
@Composable
private fun TimeBlockingHint() {
    val access = LocalProAccess.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md)
            .clip(RoundedCornerShape(Radius.sm))
            .clickable { access.openPaywall(ProFeature.TIME_BLOCKING) }
            .heightIn(min = MinTouchTarget)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ProFeature.TIME_BLOCKING.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.sm))
        Spacer(Modifier.width(Spacing.sm))
        Text(
            stringResource(R.string.calendar_time_blocking_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        ProBadge()
    }
}

@Composable
private fun AgendaView(state: CalendarUiState, callbacks: CalendarCallbacks, contentPadding: PaddingValues, shown: ShownDecorations) {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    // Official holidays get a row even without items (Plan-B Pro #2).
    val holidayDates = if (shown.holidays) state.occasions.filterValues { list -> list.any { it.holiday } }.keys else emptySet()
    val dates = (state.items.keys + holidayDates).filter { it >= state.rangeStart && it <= state.rangeEnd }.distinct().sorted()
    if (dates.isEmpty()) {
        PlannerEmptyState(
            icon = Icons.Rounded.EventBusy,
            title = stringResource(R.string.calendar_nothing_agenda_title),
            message = stringResource(R.string.calendar_nothing_agenda),
            actionLabel = stringResource(R.string.calendar_new_event),
            onAction = { callbacks.onNewEvent(state.selected) },
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        dates.forEach { date ->
            item(key = "ah_$date", contentType = "agendaHeader") {
                Column(Modifier.padding(top = Spacing.md)) {
                    Text(
                        formatter.weekdayDate(date, today),
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            date == today -> MaterialTheme.colorScheme.primary
                            state.offDay(date, shown) -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.semantics { heading() },
                    )
                    OccasionLines(state, date, shown)
                }
            }
            dayItems(date, state.itemsOn(date), callbacks, showHeader = false, showEmpty = false)
        }
    }
}

private fun LazyListScope.dayItems(
    date: LocalDate,
    items: DayItems,
    callbacks: CalendarCallbacks,
    showHeader: Boolean,
    showEmpty: Boolean = true,
    timeline: Boolean = false,
    state: CalendarUiState? = null,
    shown: ShownDecorations = ShownDecorations.None,
) {
    if (showHeader) {
        item(key = "dh_$date", contentType = "dayHeader") {
            val formatter = PlannerLocals.formatter
            Column(Modifier.padding(top = Spacing.sm)) {
                Text(
                    formatter.weekdayDate(date, PlannerLocals.today),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state?.offDay(date, shown) == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                if (state != null) IranDayDetails(state, date, shown, Modifier.padding(top = Spacing.xs))
            }
        }
    }
    if (items.count == 0 && showEmpty) {
        item(key = "empty_$date", contentType = "empty") {
            Text(
                stringResource(R.string.calendar_nothing_day),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.md),
            )
        }
        return
    }
    val events = if (timeline) items.events.sortedWith(compareBy({ !it.event.allDay }, { it.event.startTime })) else items.events
    items(events, key = { "ev_${it.event.id}_${it.date}" }, contentType = { "event" }) { occurrence ->
        if (timeline) {
            val formatter = PlannerLocals.formatter
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    occurrence.event.startTime?.let { formatter.time(it) } ?: stringResource(R.string.calendar_all_day),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp).padding(top = Spacing.md),
                )
                PlannerEventCard(occurrence.event, Modifier.weight(1f), onClick = { callbacks.onOpenEvent(occurrence.event.id) })
            }
        } else {
            PlannerEventCard(occurrence.event, onClick = { callbacks.onOpenEvent(occurrence.event.id) })
        }
    }
    items(items.device, key = { "dv_${it.calendarId}_${it.eventId}_$date" }, contentType = { "device" }) { item ->
        DeviceItemCard(item, onImport = callbacks.onImportDevice)
    }
    items(items.tasks, key = { "dt_${it.id}_$date" }, contentType = { "task" }) { task ->
        PlannerTaskCard(
            task = task,
            onToggleComplete = { callbacks.onToggleTask(task.id, it) },
            onClick = { callbacks.onOpenTask(task.id) },
            showDate = false,
        )
    }
}

/** The occasion names of a day in the agenda (holidays in the error color). */
@Composable
private fun OccasionLines(state: CalendarUiState, date: LocalDate, shown: ShownDecorations) {
    occasionNames(state, date, shown).forEach { (name, holiday) ->
        Text(
            if (holiday) stringResource(R.string.calendar_holiday_named, name) else name,
            style = MaterialTheme.typography.bodySmall,
            color = if (holiday) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A device calendar event in list views: outlined in its calendar's color, read only. */
@Composable
private fun DeviceItemCard(item: DeviceCalendarItem, onImport: (DeviceCalendarItem) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val formatter = PlannerLocals.formatter
    val color = Color(item.color)
    val time = if (item.allDay || item.startTime == null) stringResource(R.string.calendar_all_day) else formatter.timeRange(item.startTime!!, item.endTime)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(Radius.md))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(Radius.md))
            .clickable { open = true }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.EventAvailable, contentDescription = null, tint = color, modifier = Modifier.size(IconSize.sm))
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(item.title.ifBlank { stringResource(R.string.calendar_untitled) }, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(
                time + metaSeparator() + stringResource(R.string.calendar_device_event, item.calendarName),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (open) DeviceEventDialog(item, onDismiss = { open = false }, onImport = { onImport(item); open = false })
}
