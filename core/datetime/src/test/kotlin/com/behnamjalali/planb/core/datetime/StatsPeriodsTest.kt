package com.behnamjalali.planb.core.datetime

import com.behnamjalali.planb.core.model.DateSpan
import com.behnamjalali.planb.core.model.StatsPeriod
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatsPeriodsTest {
    private val date = LocalDate.of(2026, 10, 7) // Wednesday, 15 Mehr 1405

    @Test
    fun weekStartsOnTheChosenFirstDay() {
        val span = StatsPeriods.spanOf(StatsPeriod.WEEK, date, JalaliEngine, DayOfWeek.SATURDAY)
        assertThat(span).isEqualTo(DateSpan(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 9)))
        assertThat(StatsPeriods.buckets(StatsPeriod.WEEK, span, JalaliEngine)).hasSize(7)
    }

    @Test
    fun jalaliMonthAndYear() {
        val month = StatsPeriods.spanOf(StatsPeriod.MONTH, date, JalaliEngine, DayOfWeek.SATURDAY)
        // Mehr has 30 days and starts on 23 September.
        assertThat(month).isEqualTo(DateSpan(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 10, 22)))
        val year = StatsPeriods.spanOf(StatsPeriod.YEAR, date, JalaliEngine, DayOfWeek.SATURDAY)
        assertThat(year.start).isEqualTo(LocalDate.of(2026, 3, 21))
        assertThat(year.end).isEqualTo(JalaliEngine.toLocalDate(1406, 1, 1).minusDays(1))
        val buckets = StatsPeriods.buckets(StatsPeriod.YEAR, year, JalaliEngine)
        assertThat(buckets).hasSize(12)
        assertThat(buckets.first().start).isEqualTo(year.start)
        assertThat(buckets.last().end).isEqualTo(year.end)
        assertThat(buckets.sumOf { it.days }).isEqualTo(year.days)
    }

    @Test
    fun gregorianYearAndShift() {
        val year = StatsPeriods.spanOf(StatsPeriod.YEAR, date, GregorianEngine, DayOfWeek.MONDAY)
        assertThat(year).isEqualTo(DateSpan(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
        val previous = StatsPeriods.shift(StatsPeriod.YEAR, year, -1, GregorianEngine, DayOfWeek.MONDAY)
        assertThat(previous.start).isEqualTo(LocalDate.of(2025, 1, 1))
        val month = StatsPeriods.spanOf(StatsPeriod.MONTH, date, GregorianEngine, DayOfWeek.MONDAY)
        assertThat(StatsPeriods.shift(StatsPeriod.MONTH, month, 1, GregorianEngine, DayOfWeek.MONDAY).start)
            .isEqualTo(LocalDate.of(2026, 11, 1))
        val week = StatsPeriods.spanOf(StatsPeriod.WEEK, date, GregorianEngine, DayOfWeek.MONDAY)
        assertThat(StatsPeriods.shift(StatsPeriod.WEEK, week, -1, GregorianEngine, DayOfWeek.MONDAY).start)
            .isEqualTo(LocalDate.of(2026, 9, 28))
    }
}
