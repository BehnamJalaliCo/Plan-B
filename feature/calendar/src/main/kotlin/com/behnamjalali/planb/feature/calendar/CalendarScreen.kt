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
import java.time.LocalDate

data class CalendarCallbacks(
    val onViewChange: (CalendarView) -> Unit = {},
    val onSelect: (LocalDate) -> Unit = {},
    val onPage: (Int) -> Unit = {},
    val onToday: () -> Unit = {},
    val onOpenEvent: (EntityId) -> Unit = {},
    val onOpenTask: (EntityId) -> Unit = {},
    val onToggleTask: (EntityId, Boolean) -> Unit = { _, _ -> },
    val onNewEvent: (LocalDate) -> Unit = {},
)

@Composable
private fun viewLabel(view: CalendarView): String = stringResource(
    when (view) {
        CalendarView.DAY -> R.string.calendar_view_day
        CalendarView.WEEK -> R.string.calendar_view_week
        CalendarView.MONTH -> R.string.calendar_view_month
        CalendarView.AGENDA -> R.string.calendar_view_agenda
    },
)

@Composable
fun CalendarScreen(
    state: CalendarUiState,
    callbacks: CalendarCallbacks,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(modifier.fillMaxSize()) {
        PlannerTopBar(
            title = stringResource(R.string.calendar_title),
            actions = {
                PlannerIconButton(Icons.Rounded.Today, stringResource(R.string.calendar_today), callbacks.onToday)
                PlannerIconButton(Icons.Rounded.Add, stringResource(R.string.calendar_new_event), { callbacks.onNewEvent(state.selected) })
            },
        )
        PlannerSegmentedControl(
            options = CalendarView.entries,
            selected = state.view,
            onSelect = callbacks.onViewChange,
            label = { viewLabel(it) },
            modifier = Modifier.padding(horizontal = Spacing.screen).fillMaxWidth(),
        )
        when {
            state.error -> PlannerErrorState(stringResource(R.string.calendar_error))
            state.loading -> PlannerLoadingState()
            else -> {
                PeriodHeader(state, callbacks)
                val motion = PlanBTheme.motion
                AnimatedContent(
                    targetState = state.view,
                    transitionSpec = {
                        if (motion.enabled) fadeIn(tween(180)) togetherWith fadeOut(tween(120)) else fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    },
                    label = "calendarView",
                ) { view ->
                    when (view) {
                        CalendarView.MONTH -> MonthView(state, callbacks, contentPadding)
                        CalendarView.WEEK -> WeekView(state, callbacks, contentPadding)
                        CalendarView.DAY -> DayView(state, callbacks, contentPadding)
                        CalendarView.AGENDA -> AgendaView(state, callbacks, contentPadding)
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
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Spacing.screen, end = Spacing.sm, top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
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
private fun MonthView(state: CalendarUiState, callbacks: CalendarCallbacks, contentPadding: PaddingValues) {
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
                )
            }
        }
        dayItems(state.selected, state.itemsOn(state.selected), callbacks, showHeader = true)
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
    }
}

@Composable
private fun AgendaView(state: CalendarUiState, callbacks: CalendarCallbacks, contentPadding: PaddingValues) {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    val dates = state.items.keys.filter { it >= state.rangeStart && it <= state.rangeEnd }.sorted()
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
                Text(
                    formatter.weekdayDate(date, today),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Spacing.md).semantics { heading() },
                )
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
) {
    if (showHeader) {
        item(key = "dh_$date", contentType = "dayHeader") {
            val formatter = PlannerLocals.formatter
            Text(
                formatter.weekdayDate(date, PlannerLocals.today),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
            )
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
    items(items.tasks, key = { "dt_${it.id}_$date" }, contentType = { "task" }) { task ->
        PlannerTaskCard(
            task = task,
            onToggleComplete = { callbacks.onToggleTask(task.id, it) },
            onClick = { callbacks.onOpenTask(task.id) },
            showDate = false,
        )
    }
}
