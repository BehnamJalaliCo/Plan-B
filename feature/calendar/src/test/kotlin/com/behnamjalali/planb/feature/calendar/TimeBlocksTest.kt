package com.behnamjalali.planb.feature.calendar

import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.EventOccurrence
import com.behnamjalali.planb.core.model.Task
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

/** Time blocking (Plan-B Pro #6) and the day timeline (#7): the pure parts. */
class TimeBlocksTest {
    private val date = LocalDate.of(2026, 10, 4)
    private val zone = ZoneId.of("Asia/Tehran")

    private fun task(id: Long, start: Int, end: Int) = Placed.TaskBlock(Task(id = id, title = "T$id"), date, start, end, block = true)
    private fun event(id: Long, start: Int, end: Int) = Placed.Event(
        EventOccurrence(CalendarEvent(id = id, title = "E$id", date = date, startTime = LocalTime.of(start / 60, start % 60), allDay = false), date),
        start, end,
    )

    @Test
    fun snapping_toQuarterHours() {
        assertThat(TimeBlocks.snap(7f)).isEqualTo(0)
        assertThat(TimeBlocks.snap(8f)).isEqualTo(15)
        assertThat(TimeBlocks.snap(9 * 60 + 22f)).isEqualTo(9 * 60 + 15)
        assertThat(TimeBlocks.snap(9 * 60 + 23f)).isEqualTo(9 * 60 + 30)
        assertThat(TimeBlocks.snap(-40f)).isEqualTo(0)
        assertThat(TimeBlocks.snap(2000f)).isEqualTo(24 * 60)
        assertThat(TimeBlocks.clampStart(23 * 60 + 45, 60)).isEqualTo(23 * 60)
    }

    @Test
    fun defaultDuration_isTheEstimateOrHalfAnHour() {
        assertThat(TimeBlocks.defaultDuration(Task(title = "a"))).isEqualTo(30)
        assertThat(TimeBlocks.defaultDuration(Task(title = "a", estimatedMinutes = 90))).isEqualTo(90)
        assertThat(TimeBlocks.defaultDuration(Task(title = "a", estimatedMinutes = 5))).isEqualTo(15)
    }

    @Test
    fun blocks_areClippedToTheDay() {
        val start = date.atTime(23, 0).atZone(zone).toInstant()
        val t = Task(title = "late", scheduledStart = start, scheduledEnd = start.plusSeconds(2 * 3600))
        assertThat(TimeBlocks.blockOn(t, date, zone)).isEqualTo(23 * 60 to 24 * 60)
        assertThat(TimeBlocks.blockOn(t, date.plusDays(1), zone)).isEqualTo(0 to 60)
        assertThat(TimeBlocks.blockOn(t, date.plusDays(2), zone)).isNull()
        // Without an end, a block is half an hour.
        assertThat(TimeBlocks.blockOn(t.copy(scheduledEnd = null), date, zone)).isEqualTo(23 * 60 to 23 * 60 + 30)
        assertThat(TimeBlocks.instantOf(date, 9 * 60 + 15, zone)).isEqualTo(date.atTime(9, 15).atZone(zone).toInstant())
    }

    @Test
    fun overlappingBlocks_sitSideBySide() {
        val a = task(1, 9 * 60, 10 * 60)
        val b = event(2, 9 * 60 + 30, 11 * 60)
        val c = task(3, 10 * 60, 10 * 60 + 30)
        val d = task(4, 12 * 60, 13 * 60)
        val layout = TimeBlocks.layout(listOf(d, c, b, a))
        // a, b, c form one cluster (a–b and b–c overlap): two columns; c reuses a's column.
        assertThat(layout[a]).isEqualTo(TimeBlocks.Slot(0, 2))
        assertThat(layout[b]).isEqualTo(TimeBlocks.Slot(1, 2))
        assertThat(layout[c]).isEqualTo(TimeBlocks.Slot(0, 2))
        assertThat(layout[d]).isEqualTo(TimeBlocks.Slot(0, 1))
        // Three at once.
        val three = TimeBlocks.layout(listOf(task(5, 600, 660), task(6, 600, 660), task(7, 610, 640)))
        assertThat(three.values.map { it.columns }.toSet()).containsExactly(3)
        assertThat(three.values.map { it.column }).containsExactly(0, 1, 2)
    }

    @Test
    fun timeline_ordersItems_andShowsFreeGapsAndNow() {
        val standup = event(1, 9 * 60, 9 * 60 + 15)
        val focus = task(2, 10 * 60, 11 * 60 + 30)
        val lunch = event(3, 13 * 60, 14 * 60)
        val entries = TimelineBuilder.build(listOf(lunch, focus, standup), nowMinute = 12 * 60)
        assertThat(entries).containsExactly(
            TimelineEntry.Item(standup),
            TimelineEntry.Gap(9 * 60 + 15, 10 * 60),
            TimelineEntry.Item(focus),
            TimelineEntry.Gap(11 * 60 + 30, 12 * 60),
            TimelineEntry.Now(12 * 60),
            TimelineEntry.Gap(12 * 60, 13 * 60),
            TimelineEntry.Item(lunch),
        ).inOrder()
        assertThat((entries[1] as TimelineEntry.Gap).minutes).isEqualTo(45)
    }

    @Test
    fun timeline_skipsTinyGaps_overlaps_andNowElsewhere() {
        val a = task(1, 9 * 60, 10 * 60)
        val b = task(2, 9 * 60 + 30, 10 * 60 + 30)
        val c = task(3, 10 * 60 + 40, 11 * 60)
        // No "now" on other days; overlap leaves no gap; 10 minutes is too short for a gap row.
        assertThat(TimelineBuilder.build(listOf(a, b, c), nowMinute = null))
            .containsExactly(TimelineEntry.Item(a), TimelineEntry.Item(b), TimelineEntry.Item(c)).inOrder()
        // Now before everything, and after everything.
        assertThat(TimelineBuilder.build(listOf(a), nowMinute = 8 * 60).first()).isEqualTo(TimelineEntry.Now(8 * 60))
        assertThat(TimelineBuilder.build(listOf(a), nowMinute = 22 * 60).last()).isEqualTo(TimelineEntry.Now(22 * 60))
        assertThat(TimelineBuilder.build(emptyList(), nowMinute = 600)).containsExactly(TimelineEntry.Now(600))
    }
}
