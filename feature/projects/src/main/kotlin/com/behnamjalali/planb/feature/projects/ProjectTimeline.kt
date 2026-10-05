package com.behnamjalali.planb.feature.projects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.StackedBarChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.core.designsystem.component.PlannerEmptyState
import com.behnamjalali.planb.core.designsystem.component.PlannerSegmentedControl
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.ui.PlannerLocals
import kotlin.math.max
import kotlin.math.min

/** Zoom of the timeline: a column per day, or narrow days grouped in weeks. */
enum class TimelineScale(val dayWidthDp: Int) { DAY(36), WEEK(12) }

private val RowHeight = 40.dp
private val HeaderHeight = 44.dp
private val BarHeight = 22.dp
private const val MAX_A11Y_ACTIONS = 60

/**
 * The project timeline (Plan-B Pro #9): task bars from start to planned date or deadline,
 * milestones as diamonds, a line for today and connectors for dependencies (#14), on one Canvas
 * that scrolls both ways. Only what is on screen is drawn, so hundreds of tasks stay smooth.
 * Time flows from the reading start: right-to-left in Persian. Tapping a bar opens its task.
 */
@Composable
fun ProjectTimeline(
    layout: TimelineLayout,
    dependencies: Map<EntityId, List<EntityId>>,
    accent: AccentColor,
    onOpenTask: (EntityId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by rememberSaveable { mutableStateOf(TimelineScale.DAY) }
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    Column(modifier.fillMaxSize()) {
        PlannerSegmentedControl(
            options = TimelineScale.entries,
            selected = scale,
            onSelect = { scale = it },
            label = { stringResource(if (it == TimelineScale.DAY) R.string.project_timeline_days else R.string.project_timeline_weeks) },
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm).fillMaxWidth(),
        )
        if (layout.undatedTasks > 0) {
            Text(
                pluralStringResource(R.plurals.project_timeline_undated, layout.undatedTasks, PlannerLocals.numbers.format(layout.undatedTasks)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screen),
            )
        }
        if (layout.bars.isEmpty()) {
            PlannerEmptyState(Icons.Rounded.StackedBarChart, stringResource(R.string.project_tab_timeline), stringResource(R.string.project_timeline_empty))
            return@Column
        }
        val summary = stringResource(
            R.string.project_timeline_cd,
            PlannerLocals.numbers.format(layout.bars.size),
            formatter.mediumDate(layout.bars.minOf { it.start }),
            formatter.mediumDate(layout.bars.maxOf { it.end }),
        )
        val openLabels = layout.bars.take(MAX_A11Y_ACTIONS).filter { it.taskId != null }.map { it.taskId!! to stringResource(R.string.project_timeline_open, it.title) }
        TimelineCanvas(layout, dependencies, accent, scale, today, summary, openLabels, onOpenTask)
    }
}

@Composable
private fun TimelineCanvas(
    layout: TimelineLayout,
    dependencies: Map<EntityId, List<EntityId>>,
    accent: AccentColor,
    scale: TimelineScale,
    today: java.time.LocalDate,
    summary: String,
    openLabels: List<Pair<EntityId, String>>,
    onOpenTask: (EntityId) -> Unit,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val formatter = PlannerLocals.formatter
    val numbers = PlannerLocals.numbers
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val scheme = MaterialTheme.colorScheme
    val tones = PlanBTheme.colors.accent(accent)
    val errorColor = scheme.error
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val titleStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.onSurface)
    val todayLabel = stringResource(R.string.project_timeline_today)
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val dayWidth = with(density) { scale.dayWidthDp.dp.toPx() }
    val rowHeight = with(density) { RowHeight.toPx() }
    val headerHeight = with(density) { HeaderHeight.toPx() }
    val barHeight = with(density) { BarHeight.toPx() }
    val contentWidth = layout.days * dayWidth
    val contentHeight = headerHeight + layout.bars.size * rowHeight
    // Titles are measured once per layout (not per frame).
    val titles = remember(layout, titleStyle, measurer) {
        layout.bars.map { measurer.measure(it.title, titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = (dayWidth * 40).toInt().coerceAtLeast(1))) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportWidth = with(density) { maxWidth.toPx() }
        val viewportHeight = with(density) { maxHeight.toPx() }
        // Open on today, a quarter from the reading start.
        LaunchedEffect(layout.from, scale) {
            hScroll.scrollTo(max(0f, layout.dayIndex(today) * dayWidth - viewportWidth / 4).toInt())
        }
        Box(Modifier.fillMaxSize().verticalScroll(vScroll).horizontalScroll(hScroll)) {
            Canvas(
                Modifier
                    .size(with(density) { contentWidth.toDp() }, with(density) { contentHeight.toDp() })
                    .semantics {
                        contentDescription = summary
                        customActions = openLabels.map { (id, label) -> CustomAccessibilityAction(label) { onOpenTask(id); true } }
                    }
                    .pointerInput(layout) {
                        detectTapGestures { pos ->
                            val row = ((pos.y - headerHeight) / rowHeight).toInt()
                            if (pos.y > headerHeight) layout.bars.getOrNull(row)?.taskId?.let(onOpenTask)
                        }
                    },
            ) {
                // Visible window in canvas coordinates (RTL scrolls from the right end).
                val visibleLeft = if (rtl) contentWidth - hScroll.value - viewportWidth else hScroll.value.toFloat()
                val visibleRight = visibleLeft + viewportWidth
                val visibleTop = vScroll.value.toFloat()
                val firstDay = max(0, ((if (rtl) contentWidth - visibleRight else visibleLeft) / dayWidth).toInt() - 1)
                val lastDay = min(layout.days - 1, ((if (rtl) contentWidth - visibleLeft else visibleRight) / dayWidth).toInt() + 1)
                val firstRow = max(0, ((visibleTop - headerHeight) / rowHeight).toInt() - 1)
                val lastRow = min(layout.bars.lastIndex, ((visibleTop + viewportHeight) / rowHeight).toInt() + 1)
                fun left(index: Int) = TimelineLayout.dayLeft(index, dayWidth, contentWidth, rtl)

                // Week bands and grid lines.
                for (day in firstDay..lastDay) {
                    val date = layout.from.plusDays(day.toLong())
                    if (date.dayOfWeek == formatter.firstDayOfWeek) {
                        val x = if (rtl) left(day) + dayWidth else left(day)
                        drawLine(scheme.outlineVariant, Offset(x, visibleTop), Offset(x, visibleTop + viewportHeight), strokeWidth = 1f)
                    }
                }

                // Rows: bars, milestones and titles.
                for (row in firstRow..lastRow) {
                    val bar = layout.bars[row]
                    val top = headerHeight + row * rowHeight + (rowHeight - barHeight) / 2
                    val startIndex = layout.dayIndex(bar.start).coerceIn(0, layout.days - 1)
                    val endIndex = layout.dayIndex(bar.end).coerceIn(startIndex, layout.days - 1)
                    val x1 = min(left(startIndex), left(endIndex))
                    val x2 = max(left(startIndex), left(endIndex)) + dayWidth
                    val color = when {
                        bar.completed -> scheme.outline
                        bar.overdue -> errorColor
                        else -> tones.strong
                    }
                    if (bar.milestone) {
                        val cx = (x1 + x2) / 2
                        val cy = top + barHeight / 2
                        val r = barHeight / 2
                        val diamond = Path().apply {
                            moveTo(cx, cy - r)
                            lineTo(cx + r, cy)
                            lineTo(cx, cy + r)
                            lineTo(cx - r, cy)
                            close()
                        }
                        drawPath(diamond, if (bar.completed) scheme.outline else tones.onContainer)
                    } else {
                        drawRoundRect(
                            color = if (bar.completed) color.copy(alpha = 0.45f) else color,
                            topLeft = Offset(x1 + 2f, top),
                            size = Size(max(dayWidth - 4f, x2 - x1 - 4f), barHeight),
                            cornerRadius = CornerRadius(barHeight / 2),
                        )
                    }
                    val text = titles[row]
                    val gap = 6.dp.toPx()
                    // Beside the bar on the reading-end side, or on the other side near the canvas edge.
                    var textX = if (rtl) x1 - gap - text.size.width else x2 + gap
                    if (rtl && textX < 0f) textX = x2 + gap
                    if (!rtl && textX + text.size.width > contentWidth) textX = x1 - gap - text.size.width
                    drawText(text, topLeft = Offset(textX, top + (barHeight - text.size.height) / 2))
                }

                // Dependencies: from the blocker's end to the start of the task that waits for it.
                val connector = scheme.onSurfaceVariant
                dependencies.forEach { (taskId, blockers) ->
                    val toRow = layout.rowOfTask[taskId] ?: return@forEach
                    blockers.forEach { blockerId ->
                        val fromRow = layout.rowOfTask[blockerId] ?: return@forEach
                        if (max(fromRow, toRow) < firstRow || min(fromRow, toRow) > lastRow) return@forEach
                        val from = layout.bars[fromRow]
                        val to = layout.bars[toRow]
                        val fromEnd = layout.dayIndex(from.end).coerceIn(0, layout.days - 1)
                        val toStart = layout.dayIndex(to.start).coerceIn(0, layout.days - 1)
                        val sx = if (rtl) left(fromEnd) else left(fromEnd) + dayWidth
                        val ex = if (rtl) left(toStart) + dayWidth else left(toStart)
                        val sy = headerHeight + fromRow * rowHeight + rowHeight / 2
                        val ey = headerHeight + toRow * rowHeight + rowHeight / 2
                        val step = if (rtl) -8.dp.toPx() else 8.dp.toPx()
                        val path = Path().apply {
                            moveTo(sx, sy)
                            lineTo(sx + step, sy)
                            lineTo(sx + step, ey)
                            lineTo(ex, ey)
                        }
                        drawPath(path, connector, style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.cornerPathEffect(4.dp.toPx())))
                        val arrow = Path().apply {
                            val a = 5.dp.toPx() * if (rtl) -1 else 1
                            moveTo(ex, ey)
                            lineTo(ex - a, ey - a)
                            lineTo(ex - a, ey + a)
                            close()
                        }
                        drawPath(arrow, connector)
                    }
                }

                // Today.
                val todayIndex = layout.dayIndex(today)
                if (todayIndex in 0 until layout.days) {
                    val x = left(todayIndex) + dayWidth / 2
                    drawLine(scheme.primary, Offset(x, visibleTop + headerHeight), Offset(x, visibleTop + viewportHeight), strokeWidth = 2.dp.toPx())
                }

                // A header that stays at the top while scrolling down.
                drawRect(scheme.surfaceContainer, topLeft = Offset(visibleLeft, visibleTop), size = Size(viewportWidth, headerHeight))
                for (day in firstDay..lastDay) {
                    val date = layout.from.plusDays(day.toLong())
                    val calendarDate = formatter.engine.toCalendarDate(date)
                    val label = when {
                        scale == TimelineScale.DAY -> numbers.format(calendarDate.day)
                        date.dayOfWeek == formatter.firstDayOfWeek -> formatter.dayMonth(date)
                        else -> null
                    } ?: continue
                    val measured = measurer.measure(label, labelStyle)
                    val x = if (scale == TimelineScale.DAY) {
                        left(day) + (dayWidth - measured.size.width) / 2
                    } else if (rtl) {
                        left(day) + dayWidth - measured.size.width
                    } else {
                        left(day)
                    }
                    drawText(measured, topLeft = Offset(x, visibleTop + headerHeight - measured.size.height - 4.dp.toPx()))
                    if (scale == TimelineScale.DAY && (calendarDate.day == 1 || day == firstDay + 1)) {
                        val month = measurer.measure(formatter.monthName(calendarDate.month), labelStyle)
                        val mx = if (rtl) left(day) + dayWidth - month.size.width else left(day)
                        drawText(month, topLeft = Offset(mx, visibleTop + 4.dp.toPx()))
                    }
                }
                if (todayIndex in 0 until layout.days) {
                    val measured = measurer.measure(todayLabel, labelStyle.copy(color = scheme.primary))
                    val x = left(todayIndex) + (dayWidth - measured.size.width) / 2
                    if (scale == TimelineScale.WEEK) drawText(measured, topLeft = Offset(x, visibleTop + 4.dp.toPx()))
                }
            }
        }
    }
}
