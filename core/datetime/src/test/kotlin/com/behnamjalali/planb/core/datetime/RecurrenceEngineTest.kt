package com.behnamjalali.planb.core.datetime

import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.RecurrenceFrequency.DAILY
import com.behnamjalali.planb.core.model.RecurrenceFrequency.MONTHLY
import com.behnamjalali.planb.core.model.RecurrenceFrequency.WEEKLY
import com.behnamjalali.planb.core.model.RecurrenceFrequency.YEARLY
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecurrenceEngineTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun daily_everyDay() {
        val rule = RecurrenceRule(DAILY)
        val result = RecurrenceEngine.occurrencesBetween(rule, d(2026, 1, 30), d(2026, 1, 30), d(2026, 2, 2))
        assertThat(result).containsExactly(d(2026, 1, 30), d(2026, 1, 31), d(2026, 2, 1), d(2026, 2, 2)).inOrder()
    }

    @Test
    fun everyNDays() {
        val rule = RecurrenceRule(DAILY, interval = 3)
        assertThat(RecurrenceEngine.nextOccurrence(rule, d(2026, 2, 26), d(2026, 2, 26))).isEqualTo(d(2026, 3, 1))
    }

    @Test
    fun weekdays_mondayToFriday() {
        val rule = RecurrenceRule(
            WEEKLY,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        )
        val result = RecurrenceEngine.occurrencesBetween(rule, d(2026, 10, 1), d(2026, 10, 1), d(2026, 10, 7))
        assertThat(result).containsExactly(d(2026, 10, 1), d(2026, 10, 2), d(2026, 10, 5), d(2026, 10, 6), d(2026, 10, 7))
    }

    @Test
    fun weekly_defaultsToAnchorWeekday() {
        val rule = RecurrenceRule(WEEKLY)
        assertThat(RecurrenceEngine.nextOccurrence(rule, d(2026, 10, 4), d(2026, 10, 4))).isEqualTo(d(2026, 10, 11))
    }

    @Test
    fun everyTwoWeeks_selectedDays_persianWeek() {
        val rule = RecurrenceRule(
            WEEKLY,
            interval = 2,
            weekdays = setOf(DayOfWeek.SATURDAY, DayOfWeek.WEDNESDAY),
            calendarSystem = CalendarSystem.JALALI,
        )
        // Anchor Saturday 3 Oct 2026; Persian week Sat..Fri
        val result = RecurrenceEngine.occurrencesBetween(rule, d(2026, 10, 3), d(2026, 10, 1), d(2026, 10, 31))
        assertThat(result).containsExactly(d(2026, 10, 3), d(2026, 10, 7), d(2026, 10, 17), d(2026, 10, 21), d(2026, 10, 31))
            .inOrder()
    }

    @Test
    fun monthly_gregorian_clampsFebruaryAndSpringsBack() {
        val rule = RecurrenceRule(MONTHLY)
        val result = RecurrenceEngine.occurrencesBetween(rule, d(2026, 1, 31), d(2026, 1, 1), d(2026, 4, 30))
        assertThat(result).containsExactly(d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 31), d(2026, 4, 30)).inOrder()
    }

    @Test
    fun monthly_leapFebruary() {
        val rule = RecurrenceRule(MONTHLY)
        assertThat(RecurrenceEngine.nextOccurrence(rule, d(2028, 1, 30), d(2028, 1, 30))).isEqualTo(d(2028, 2, 29))
    }

    @Test
    fun monthly_jalali_followsJalaliMonths() {
        val engine = JalaliEngine
        val anchor = engine.toLocalDate(1405, 6, 31) // 31 Shahrivar
        val rule = RecurrenceRule(MONTHLY, calendarSystem = CalendarSystem.JALALI)
        val next3 = RecurrenceEngine.sequence(rule, anchor).take(3).map { engine.toCalendarDate(it) }.toList()
        assertThat(next3).containsExactly(CalendarDate(1405, 6, 31), CalendarDate(1405, 7, 30), CalendarDate(1405, 8, 30))
            .inOrder()
    }

    @Test
    fun yearly_jalali_esfand30_inNonLeapYearClamps() {
        val engine = JalaliEngine
        val anchor = engine.toLocalDate(1403, 12, 30)
        val rule = RecurrenceRule(YEARLY, calendarSystem = CalendarSystem.JALALI)
        val next = RecurrenceEngine.nextOccurrence(rule, anchor, anchor)!!
        assertThat(engine.toCalendarDate(next)).isEqualTo(CalendarDate(1404, 12, 29))
    }

    @Test
    fun yearly_nowruz() {
        val engine = JalaliEngine
        val rule = RecurrenceRule(YEARLY, calendarSystem = CalendarSystem.JALALI)
        val anchor = engine.toLocalDate(1403, 1, 1)
        val list = RecurrenceEngine.sequence(rule, anchor).take(3).toList()
        assertThat(list).containsExactly(d(2024, 3, 20), d(2025, 3, 21), d(2026, 3, 21)).inOrder()
    }

    @Test
    fun yearly_gregorianLeapDay() {
        val rule = RecurrenceRule(YEARLY)
        val list = RecurrenceEngine.sequence(rule, d(2028, 2, 29)).take(3).toList()
        assertThat(list).containsExactly(d(2028, 2, 29), d(2029, 2, 28), d(2030, 2, 28)).inOrder()
    }

    @Test
    fun count_limitsTotalOccurrences() {
        val rule = RecurrenceRule(DAILY, count = 3)
        val anchor = d(2026, 1, 1)
        assertThat(RecurrenceEngine.sequence(rule, anchor).toList()).hasSize(3)
        assertThat(RecurrenceEngine.nextOccurrence(rule, anchor, d(2026, 1, 3))).isNull()
    }

    @Test
    fun until_isInclusive() {
        val rule = RecurrenceRule(WEEKLY, until = d(2026, 1, 15))
        val list = RecurrenceEngine.sequence(rule, d(2026, 1, 1)).toList()
        assertThat(list).containsExactly(d(2026, 1, 1), d(2026, 1, 8), d(2026, 1, 15)).inOrder()
    }

    @Test
    fun encodeDecode_roundTrip() {
        val rule = RecurrenceRule(
            WEEKLY,
            interval = 2,
            weekdays = setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY),
            calendarSystem = CalendarSystem.JALALI,
            until = d(2027, 1, 1),
            count = 12,
        )
        assertThat(RecurrenceRule.decode(rule.encode())).isEqualTo(rule)
        assertThat(RecurrenceRule.decode("garbage")).isNull()
        assertThat(RecurrenceRule.decode(null)).isNull()
    }

    @Test
    fun largeRange_isFastAndBounded() {
        val rule = RecurrenceRule(DAILY)
        val list = RecurrenceEngine.occurrencesBetween(rule, d(2000, 1, 1), d(2030, 1, 1), d(2030, 1, 31))
        assertThat(list).hasSize(31)
    }

    @Test
    fun reminder_dstGapResolvesForward() {
        val zone = ZoneId.of("America/New_York")
        // 2026-03-08 02:30 does not exist in New York (clocks jump 02:00 → 03:00)
        val trigger = ReminderTime.triggerAt(d(2026, 3, 8), LocalTime.of(2, 30), 0, zone)
        assertThat(trigger.atZone(zone).toLocalTime()).isEqualTo(LocalTime.of(3, 30))
    }

    @Test
    fun reminder_offsetAcrossMidnight_andDateOnlyDefault() {
        val zone = ZoneId.of("Asia/Tehran")
        val trigger = ReminderTime.triggerAt(d(2026, 10, 4), LocalTime.of(0, 10), 30, zone)
        assertThat(trigger.atZone(zone).toLocalDate()).isEqualTo(d(2026, 10, 3))
        val dateOnly = ReminderTime.triggerAt(d(2026, 10, 4), null, 0, zone)
        assertThat(dateOnly.atZone(zone).toLocalTime()).isEqualTo(LocalTime.of(9, 0))
    }
}
