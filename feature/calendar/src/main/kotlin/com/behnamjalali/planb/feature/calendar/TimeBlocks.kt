package com.behnamjalali.planb.feature.calendar

import com.behnamjalali.planb.core.calendarsync.DeviceCalendarItem
import com.behnamjalali.planb.core.model.EventOccurrence
import com.behnamjalali.planb.core.model.Task
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Something that occupies time on one day of the time grid or the timeline. */
sealed interface Placed {
    val key: String
    val date: LocalDate

    /** Minutes from midnight, [startMinute] < [endMinute] ≤ 1440. */
    val startMinute: Int
    val endMinute: Int

    data class Event(val occurrence: EventOccurrence, override val startMinute: Int, override val endMinute: Int) : Placed {
        override val key get() = "ev_${occurrence.event.id}_${occurrence.date}"
        override val date: LocalDate get() = occurrence.date
    }

    /** A task with a time block ([block] = true) or only a due time (shown 30 minutes long). */
    data class TaskBlock(val task: Task, override val date: LocalDate, override val startMinute: Int, override val endMinute: Int, val block: Boolean) : Placed {
        override val key get() = "tb_${task.id}"
    }

    data class Device(val item: DeviceCalendarItem, override val startMinute: Int, override val endMinute: Int) : Placed {
        override val key get() = "dv_${item.calendarId}_${item.eventId}_${item.date}"
        override val date: LocalDate get() = item.date
    }
}

/** Pure helpers of time blocking (Plan-B Pro #6): snapping, durations and side-by-side layout. */
object TimeBlocks {
    const val SNAP_MINUTES = 15
    const val DEFAULT_MINUTES = 30
    const val MIN_MINUTES = 15
    const val DAY_MINUTES = 24 * 60

    /** Rounds to the nearest quarter hour inside the day. */
    fun snap(minute: Float): Int = (Math.round(minute / SNAP_MINUTES) * SNAP_MINUTES).coerceIn(0, DAY_MINUTES)

    /** Length of a new block for [task]: its estimate, or 30 minutes. */
    fun defaultDuration(task: Task): Int = (task.estimatedMinutes ?: DEFAULT_MINUTES).coerceIn(MIN_MINUTES, DAY_MINUTES)

    /** Start minute moved so a block of [duration] stays within the day. */
    fun clampStart(start: Int, duration: Int): Int = start.coerceIn(0, DAY_MINUTES - duration.coerceAtMost(DAY_MINUTES))

    fun minuteOf(time: LocalTime): Int = time.hour * 60 + time.minute

    fun timeOf(minute: Int): LocalTime = if (minute >= DAY_MINUTES) LocalTime.of(23, 59) else LocalTime.of(minute / 60, minute % 60)

    /** The block of a time-blocked task on [date], clipped to that day; null when it is elsewhere. */
    fun blockOn(task: Task, date: LocalDate, zone: ZoneId): Pair<Int, Int>? {
        val start = task.scheduledStart ?: return null
        val end = task.scheduledEnd?.takeIf { it.isAfter(start) } ?: start.plusSeconds(DEFAULT_MINUTES * 60L)
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        if (!start.isBefore(dayEnd) || !end.isAfter(dayStart)) return null
        val from = maxOf(start, dayStart)
        val to = minOf(end, dayEnd)
        val startMinute = ((from.epochSecond - dayStart.epochSecond) / 60).toInt()
        val endMinute = ((to.epochSecond - dayStart.epochSecond) / 60).toInt().coerceAtLeast(startMinute + 1)
        return startMinute to endMinute
    }

    /** The instant of [minute] on [date] in [zone]. */
    fun instantOf(date: LocalDate, minute: Int, zone: ZoneId): Instant = date.atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant()

    /** Column assignment of overlapping blocks (side by side): index and number of columns. */
    data class Slot(val column: Int, val columns: Int)

    /**
     * Lays out one day's blocks: blocks that overlap (directly or through a chain) form a
     * cluster; inside it each block takes the first column free at its start, and every block
     * of the cluster shares the cluster's column count.
     */
    fun <T : Placed> layout(items: List<T>): Map<T, Slot> {
        val sorted = items.sortedWith(compareBy<T>({ it.startMinute }, { -it.endMinute }, { it.key }))
        val result = LinkedHashMap<T, Slot>()
        var cluster = ArrayList<Pair<T, Int>>()
        var columnEnds = ArrayList<Int>()
        var clusterEnd = -1
        fun flush() {
            val count = columnEnds.size
            cluster.forEach { (item, column) -> result[item] = Slot(column, count) }
            cluster = ArrayList()
            columnEnds = ArrayList()
        }
        for (item in sorted) {
            if (item.startMinute >= clusterEnd && cluster.isNotEmpty()) flush()
            var column = columnEnds.indexOfFirst { it <= item.startMinute }
            if (column < 0) {
                column = columnEnds.size
                columnEnds += item.endMinute
            } else {
                columnEnds[column] = item.endMinute
            }
            cluster += item to column
            clusterEnd = maxOf(if (cluster.size == 1) item.endMinute else clusterEnd, item.endMinute)
        }
        if (cluster.isNotEmpty()) flush()
        return result
    }
}
