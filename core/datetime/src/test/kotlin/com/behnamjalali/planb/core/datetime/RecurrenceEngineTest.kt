package com.behnamjalali.planb.core.datetime

import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.RecurrenceBasis
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
import java.time.temporal.TemporalAdjusters
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

    // region Plan-B Pro #4: advanced recurrence

    @Test
    fun secondMonday_gregorian_matchesJavaTime() {
        val rule = RecurrenceRule(MONTHLY, weekdays = setOf(DayOfWeek.MONDAY), setPosition = 2)
        val list = RecurrenceEngine.sequence(rule, d(2026, 10, 1)).take(15).toList()
        assertThat(list.first()).isEqualTo(d(2026, 10, 12))
        assertThat(list[1]).isEqualTo(d(2026, 11, 9))
        assertThat(list[2]).isEqualTo(d(2026, 12, 14))
        list.forEach { date ->
            assertThat(date).isEqualTo(date.with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.MONDAY)))
        }
        // One per month, in order.
        assertThat(list.map { it.withDayOfMonth(1) }.distinct()).hasSize(15)
    }

    @Test
    fun lastFriday_gregorian_includingFebruary() {
        val rule = RecurrenceRule(MONTHLY, weekdays = setOf(DayOfWeek.FRIDAY), setPosition = -1)
        val list = RecurrenceEngine.occurrencesBetween(rule, d(2028, 1, 1), d(2028, 1, 1), d(2028, 12, 31))
        assertThat(list).hasSize(12)
        list.forEach { assertThat(it).isEqualTo(it.with(TemporalAdjusters.lastInMonth(DayOfWeek.FRIDAY))) }
        assertThat(list[1]).isEqualTo(d(2028, 2, 25))
    }

    @Test
    fun anchorAfterThisMonthsOccurrence_startsNextMonth() {
        val rule = RecurrenceRule(MONTHLY, weekdays = setOf(DayOfWeek.MONDAY), setPosition = 2)
        assertThat(RecurrenceEngine.firstOnOrAfter(rule, d(2026, 10, 13), d(2026, 10, 13))).isEqualTo(d(2026, 11, 9))
    }

    @Test
    fun fifthWeekday_missingMonthIsSkipped() {
        val rule = RecurrenceRule(MONTHLY, weekdays = setOf(DayOfWeek.FRIDAY), setPosition = 5)
        val list = RecurrenceEngine.occurrencesBetween(rule, d(2026, 1, 1), d(2026, 1, 1), d(2026, 12, 31))
        // Never moved to the fourth or last Friday: only months that have a fifth one.
        list.forEach { assertThat(it).isEqualTo(it.with(TemporalAdjusters.dayOfWeekInMonth(5, DayOfWeek.FRIDAY))) }
        list.forEach { assertThat(it.dayOfMonth).isAtLeast(29) }
        assertThat(list.first()).isEqualTo(d(2026, 1, 30))
        assertThat(list[1]).isEqualTo(d(2026, 5, 29))
        assertThat(list.size).isLessThan(12)
    }

    @Test
    fun nthWeekday_jalaliMonths() {
        val engine = JalaliEngine
        val mehr1 = engine.toLocalDate(1405, 7, 1)
        assertThat(mehr1).isEqualTo(d(2026, 9, 23))
        val second = RecurrenceRule(MONTHLY, weekdays = setOf(DayOfWeek.SATURDAY), setPosition = 2, calendarSystem = CalendarSystem.JALALI)
        assertThat(RecurrenceEngine.firstOnOrAfter(second, mehr1, mehr1)).isEqualTo(engine.toLocalDate(1405, 7, 11))
        val last = RecurrenceRule(MONTHLY, weekdays = setOf(DayOfWeek.FRIDAY), setPosition = -1, calendarSystem = CalendarSystem.JALALI)
        val months = RecurrenceEngine.sequence(last, mehr1).take(12).toList()
        assertThat(engine.toCalendarDate(months.first())).isEqualTo(CalendarDate(1405, 7, 24))
        months.forEachIndexed { i, date ->
            val c = engine.toCalendarDate(date)
            // Each one in the next Jalali month, a Friday, and no Friday later in that month.
            assertThat(CalendarMonth(c.year, c.month)).isEqualTo(CalendarMonth(1405, 7).plus(i))
            assertThat(date.dayOfWeek).isEqualTo(DayOfWeek.FRIDAY)
            assertThat(engine.monthOf(date.plusDays(7))).isNotEqualTo(CalendarMonth(c.year, c.month))
        }
    }

    @Test
    fun esfand_fifthWeekdayOnlyWhenItExists() {
        val engine = JalaliEngine
        assertThat(engine.monthLength(1404, 12)).isEqualTo(29)
        val esfand1 = engine.toLocalDate(1404, 12, 1)
        val bahman1 = engine.toLocalDate(1404, 11, 1)
        // A 29-day Esfand has exactly one weekday five times: the weekday of its first day.
        val present = RecurrenceRule(MONTHLY, weekdays = setOf(esfand1.dayOfWeek), setPosition = 5, calendarSystem = CalendarSystem.JALALI)
        val absent = RecurrenceRule(MONTHLY, weekdays = setOf(esfand1.dayOfWeek.plus(1)), setPosition = 5, calendarSystem = CalendarSystem.JALALI)
        val esfandEnd = engine.toLocalDate(1404, 12, 29)
        assertThat(RecurrenceEngine.occurrencesBetween(present, bahman1, esfand1, esfandEnd)).containsExactly(esfandEnd)
        assertThat(RecurrenceEngine.occurrencesBetween(absent, bahman1, esfand1, esfandEnd)).isEmpty()
        // The leap Esfand 1403 (30 days) has two weekdays five times.
        val leapEsfand = engine.toLocalDate(1403, 12, 1)
        val fifths = DayOfWeek.entries.count { day ->
            val rule = RecurrenceRule(MONTHLY, weekdays = setOf(day), setPosition = 5, calendarSystem = CalendarSystem.JALALI)
            RecurrenceEngine.occurrencesBetween(rule, leapEsfand, leapEsfand, engine.toLocalDate(1403, 12, 30)).isNotEmpty()
        }
        assertThat(fifths).isEqualTo(2)
    }

    @Test
    fun weekdayOfMonth_withoutPosition_isEveryMatchingDay() {
        val rule = RecurrenceRule(MONTHLY, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        val list = RecurrenceEngine.occurrencesBetween(rule, d(2026, 10, 1), d(2026, 10, 1), d(2026, 10, 31))
        assertThat(list).containsExactly(
            d(2026, 10, 1), d(2026, 10, 5), d(2026, 10, 8), d(2026, 10, 12), d(2026, 10, 15),
            d(2026, 10, 19), d(2026, 10, 22), d(2026, 10, 26), d(2026, 10, 29),
        ).inOrder()
    }

    @Test
    fun ordinalWeekday_everyTwoMonths_countAndUntil() {
        val counted = RecurrenceRule(MONTHLY, interval = 2, weekdays = setOf(DayOfWeek.MONDAY), setPosition = 1, count = 3)
        assertThat(RecurrenceEngine.sequence(counted, d(2026, 10, 1)).toList())
            .containsExactly(d(2026, 10, 5), d(2026, 12, 7), d(2027, 2, 1)).inOrder()
        val until = counted.copy(count = null, until = d(2026, 12, 7))
        assertThat(RecurrenceEngine.sequence(until, d(2026, 10, 1)).toList())
            .containsExactly(d(2026, 10, 5), d(2026, 12, 7)).inOrder()
        assertThat(RecurrenceEngine.nextOccurrence(until, d(2026, 10, 1), d(2026, 12, 7))).isNull()
    }

    @Test
    fun everyTwoWeeks_respectsWeekStart() {
        val days = setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY)
        // Weeks starting on Sunday: the anchor's week holds Sunday 4 and Wednesday 7 October.
        val sunday = RecurrenceRule(WEEKLY, interval = 2, weekdays = days, weekStart = DayOfWeek.SUNDAY)
        assertThat(RecurrenceEngine.occurrencesBetween(sunday, d(2026, 10, 4), d(2026, 10, 1), d(2026, 10, 25)))
            .containsExactly(d(2026, 10, 4), d(2026, 10, 7), d(2026, 10, 18), d(2026, 10, 21)).inOrder()
        // Weeks starting on Monday: Sunday 4 October ends its week, so Wednesday 7 is skipped.
        val monday = sunday.copy(weekStart = null)
        assertThat(RecurrenceEngine.occurrencesBetween(monday, d(2026, 10, 4), d(2026, 10, 1), d(2026, 10, 25)))
            .containsExactly(d(2026, 10, 4), d(2026, 10, 14), d(2026, 10, 18)).inOrder()
    }

    @Test
    fun afterCompletion_countsFromTheCompletionDay() {
        val rule = RecurrenceRule(DAILY, interval = 3, basis = RecurrenceBasis.COMPLETION)
        assertThat(RecurrenceEngine.nextAfterCompletion(rule, d(2026, 10, 9))).isEqualTo(d(2026, 10, 12))
        val weeks = rule.copy(frequency = WEEKLY, interval = 2)
        assertThat(RecurrenceEngine.nextAfterCompletion(weeks, d(2026, 10, 9))).isEqualTo(d(2026, 10, 23))
        val engine = JalaliEngine
        val monthly = RecurrenceRule(MONTHLY, basis = RecurrenceBasis.COMPLETION, calendarSystem = CalendarSystem.JALALI)
        val next = RecurrenceEngine.nextAfterCompletion(monthly, engine.toLocalDate(1405, 6, 31))!!
        assertThat(engine.toCalendarDate(next)).isEqualTo(CalendarDate(1405, 7, 30))
        val feb = RecurrenceRule(MONTHLY, basis = RecurrenceBasis.COMPLETION)
        assertThat(RecurrenceEngine.nextAfterCompletion(feb, d(2026, 1, 31))).isEqualTo(d(2026, 2, 28))
        val ending = rule.copy(until = d(2026, 10, 11))
        assertThat(RecurrenceEngine.nextAfterCompletion(ending, d(2026, 10, 9))).isNull()
    }

    @Test
    fun advancedRules_encodeDecodeRoundTrip() {
        val rules = listOf(
            RecurrenceRule(MONTHLY, weekdays = setOf(DayOfWeek.MONDAY), setPosition = 2, calendarSystem = CalendarSystem.JALALI),
            RecurrenceRule(MONTHLY, interval = 3, weekdays = setOf(DayOfWeek.FRIDAY), setPosition = -1, count = 4),
            RecurrenceRule(DAILY, interval = 3, basis = RecurrenceBasis.COMPLETION, until = d(2027, 1, 1)),
            RecurrenceRule(WEEKLY, interval = 2, weekdays = setOf(DayOfWeek.SUNDAY), weekStart = DayOfWeek.SUNDAY),
        )
        rules.forEach { assertThat(RecurrenceRule.decode(it.encode())).isEqualTo(it) }
        assertThat(rules[0].encode()).isEqualTo("FREQ=MONTHLY;INTERVAL=1;BYDAY=MO;BYSETPOS=2;CAL=JALALI")
        assertThat(rules[2].encode()).contains("BASIS=COMPLETION")
        assertThat(rules.take(3).all { it.isAdvanced }).isTrue()
        // Plain rules keep exactly the text they were stored with before.
        assertThat(RecurrenceRule(WEEKLY, weekdays = setOf(DayOfWeek.MONDAY)).encode()).isEqualTo("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO;CAL=GREGORIAN")
        // Invalid or unknown values degrade instead of failing.
        assertThat(RecurrenceRule.decode("FREQ=MONTHLY;INTERVAL=1;BYDAY=MO;BYSETPOS=9;CAL=GREGORIAN;BASIS=LATER"))
            .isEqualTo(RecurrenceRule(MONTHLY, weekdays = setOf(DayOfWeek.MONDAY)))
    }

    // endregion

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
