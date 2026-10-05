package com.behnamjalali.planb.feature.calendar

/** One row of the vertical day timeline (Plan-B Pro #7). */
sealed interface TimelineEntry {
    val key: String

    data class Item(val placed: Placed) : TimelineEntry {
        override val key: String get() = placed.key
    }

    /** Free time between two items, in minutes. */
    data class Gap(val startMinute: Int, val endMinute: Int) : TimelineEntry {
        val minutes: Int get() = endMinute - startMinute
        override val key: String get() = "gap_$startMinute"
    }

    /** The current time (only on today's timeline). */
    data class Now(val minute: Int) : TimelineEntry {
        override val key: String get() = "now"
    }
}

object TimelineBuilder {
    /** Gaps shorter than this are not worth a row. */
    const val MIN_GAP_MINUTES = 15

    /**
     * Orders timed [items] by start, inserts the free gaps between them (overlaps leave no gap)
     * and the "now" marker at [nowMinute] when given. A gap that contains now is split around it
     * so the marker sits at the right place.
     */
    fun build(items: List<Placed>, nowMinute: Int?): List<TimelineEntry> {
        val sorted = items.sortedWith(compareBy<Placed>({ it.startMinute }, { it.endMinute }, { it.key }))
        val result = ArrayList<TimelineEntry>()
        var busyUntil: Int? = null
        var nowPlaced = nowMinute == null
        fun placeNow(before: Int) {
            if (!nowPlaced && nowMinute!! < before) {
                result += TimelineEntry.Now(nowMinute)
                nowPlaced = true
            }
        }
        fun addGap(from: Int, to: Int) {
            if (!nowPlaced && nowMinute!! in from until to) {
                if (nowMinute - from >= MIN_GAP_MINUTES) result += TimelineEntry.Gap(from, nowMinute)
                result += TimelineEntry.Now(nowMinute)
                nowPlaced = true
                if (to - nowMinute >= MIN_GAP_MINUTES) result += TimelineEntry.Gap(nowMinute, to)
            } else if (to - from >= MIN_GAP_MINUTES) {
                result += TimelineEntry.Gap(from, to)
            }
        }
        for (item in sorted) {
            val until = busyUntil
            if (until != null && item.startMinute > until) addGap(until, item.startMinute)
            placeNow(item.startMinute)
            result += TimelineEntry.Item(item)
            busyUntil = maxOf(until ?: item.endMinute, item.endMinute)
        }
        if (!nowPlaced) result += TimelineEntry.Now(nowMinute!!)
        return result
    }
}
