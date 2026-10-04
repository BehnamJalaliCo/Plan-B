package com.behnamjalali.planb.core.datetime

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JalaliEngineTest {
    private val engine = JalaliEngine

    @Test
    fun nowruz_farvardin1_matchesKnownGregorianDates() {
        assertThat(engine.toLocalDate(1399, 1, 1)).isEqualTo(LocalDate.of(2020, 3, 20))
        assertThat(engine.toLocalDate(1400, 1, 1)).isEqualTo(LocalDate.of(2021, 3, 21))
        assertThat(engine.toLocalDate(1403, 1, 1)).isEqualTo(LocalDate.of(2024, 3, 20))
        assertThat(engine.toLocalDate(1404, 1, 1)).isEqualTo(LocalDate.of(2025, 3, 21))
        assertThat(engine.toLocalDate(1405, 1, 1)).isEqualTo(LocalDate.of(2026, 3, 21))
    }

    @Test
    fun gregorianToJalali_knownDates() {
        assertThat(engine.toCalendarDate(LocalDate.of(2026, 10, 4))).isEqualTo(CalendarDate(1405, 7, 12))
        assertThat(engine.toCalendarDate(LocalDate.of(1979, 2, 11))).isEqualTo(CalendarDate(1357, 11, 22))
        assertThat(engine.toCalendarDate(LocalDate.of(2000, 1, 1))).isEqualTo(CalendarDate(1378, 10, 11))
    }

    @Test
    fun leapYears_esfandHas30Days() {
        assertThat(engine.isLeapYear(1399)).isTrue()
        assertThat(engine.isLeapYear(1403)).isTrue()
        assertThat(engine.isLeapYear(1402)).isFalse()
        assertThat(engine.isLeapYear(1404)).isFalse()
        assertThat(engine.monthLength(1403, 12)).isEqualTo(30)
        assertThat(engine.monthLength(1404, 12)).isEqualTo(29)
        assertThat(engine.toLocalDate(1403, 12, 30)).isEqualTo(LocalDate.of(2025, 3, 20))
    }

    @Test
    fun monthLengths_followJalaliStructure() {
        (1..6).forEach { assertThat(engine.monthLength(1405, it)).isEqualTo(31) }
        (7..11).forEach { assertThat(engine.monthLength(1405, it)).isEqualTo(30) }
    }

    @Test
    fun yearBoundary_lastDayOfEsfandToFarvardin() {
        val lastDay = engine.toLocalDate(1404, 12, 29)
        assertThat(engine.toCalendarDate(lastDay)).isEqualTo(CalendarDate(1404, 12, 29))
        assertThat(engine.toCalendarDate(lastDay.plusDays(1))).isEqualTo(CalendarDate(1405, 1, 1))
    }

    @Test
    fun roundTrip_everyDayForTwentyYears() {
        var date = LocalDate.of(2010, 1, 1)
        val end = LocalDate.of(2030, 12, 31)
        var previous = engine.toCalendarDate(date.minusDays(1))
        while (date <= end) {
            val c = engine.toCalendarDate(date)
            assertThat(engine.toLocalDate(c.year, c.month, c.day)).isEqualTo(date)
            // consecutive days must advance by exactly one calendar day
            if (c.day == 1) {
                assertThat(previous.day).isEqualTo(engine.monthLength(previous.year, previous.month))
            } else {
                assertThat(c.day).isEqualTo(previous.day + 1)
            }
            previous = c
            date = date.plusDays(1)
        }
    }

    @Test
    fun dayIsClampedToMonthLength() {
        assertThat(engine.toLocalDate(1405, 7, 31)).isEqualTo(engine.toLocalDate(1405, 7, 30))
    }

    @Test
    fun plusMonths_clampsAtMonthEnd() {
        val shahrivar31 = engine.toLocalDate(1405, 6, 31)
        assertThat(engine.toCalendarDate(engine.plusMonths(shahrivar31, 1))).isEqualTo(CalendarDate(1405, 7, 30))
        val esfand = engine.toLocalDate(1404, 12, 15)
        assertThat(engine.toCalendarDate(engine.plusMonths(esfand, 1))).isEqualTo(CalendarDate(1405, 1, 15))
    }

    @Test
    fun monthGrid_jalaliMehr1405_startsSaturday() {
        val grid = MonthGrid.build(engine, CalendarMonth(1405, 7), DayOfWeek.SATURDAY)
        val cells = grid.flatten()
        assertThat(cells.size % 7).isEqualTo(0)
        assertThat(cells.first().date.dayOfWeek).isEqualTo(DayOfWeek.SATURDAY)
        val inMonth = cells.filter { it.inMonth }
        assertThat(inMonth).hasSize(30)
        assertThat(engine.toCalendarDate(inMonth.first().date)).isEqualTo(CalendarDate(1405, 7, 1))
        // 1 Mehr 1405 = 23 Sep 2026 (Wednesday) → 4 leading days from Shahrivar
        assertThat(cells.takeWhile { !it.inMonth }).hasSize(4)
    }

    @Test
    fun monthGrid_gregorian_correctAdjacentDays() {
        val grid = MonthGrid.build(GregorianEngine, CalendarMonth(2026, 2), DayOfWeek.MONDAY)
        val cells = grid.flatten()
        assertThat(cells.first().date).isEqualTo(LocalDate.of(2026, 1, 26))
        assertThat(cells.count { it.inMonth }).isEqualTo(28)
        assertThat(cells.last().date.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
    }

    @Test
    fun monthGrid_fourWeekMonth_stillHasFiveRows() {
        // February 2021 starts on Monday and has 28 days: exactly four weeks.
        val grid = MonthGrid.build(GregorianEngine, CalendarMonth(2021, 2), DayOfWeek.MONDAY)
        assertThat(grid).hasSize(5)
        assertThat(grid.first().first().date).isEqualTo(LocalDate.of(2021, 2, 1))
        assertThat(grid.last().none { it.inMonth }).isTrue()
        assertThat(grid.last().last().date).isEqualTo(LocalDate.of(2021, 3, 7))
    }
}
