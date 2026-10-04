package com.behnamjalali.planb.feature.templates

import com.behnamjalali.planb.core.datetime.CalendarEngines
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.PlannerTemplate
import com.behnamjalali.planb.core.model.TemplatePayload
import com.behnamjalali.planb.core.model.TemplateType
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric: the Jalali engine uses Android's ICU calendar. */
@RunWith(RobolectricTestRunner::class)
class TemplateDateTest {
    private val sunday = LocalDate.of(2026, 10, 4)
    private fun builtIn(key: String?) = PlannerTemplate(builtInKey = key, title = key.orEmpty(), type = TemplateType.NOTE, payload = TemplatePayload())
    private val jalali = CalendarEngines.of(CalendarSystem.JALALI)
    private val gregorian = CalendarEngines.of(CalendarSystem.GREGORIAN)

    @Test
    fun weeklyPlanner_usesTheStartOfTheWeek() {
        assertThat(templateDate(builtIn("weekly_planner"), sunday, DayOfWeek.SATURDAY, jalali)).isEqualTo(LocalDate.of(2026, 10, 3))
        assertThat(templateDate(builtIn("weekly_planner"), sunday, DayOfWeek.MONDAY, gregorian)).isEqualTo(LocalDate.of(2026, 9, 28))
    }

    @Test
    fun monthlyPlanner_usesTheFirstDayOfTheMonthInTheUsersCalendar() {
        // 12 Mehr 1405 -> 1 Mehr 1405.
        assertThat(templateDate(builtIn("monthly_planner"), sunday, DayOfWeek.SATURDAY, jalali)).isEqualTo(LocalDate.of(2026, 9, 23))
        assertThat(templateDate(builtIn("monthly_planner"), sunday, DayOfWeek.MONDAY, gregorian)).isEqualTo(LocalDate.of(2026, 10, 1))
    }

    @Test
    fun otherTemplates_useToday() {
        assertThat(templateDate(builtIn("daily_planner"), sunday, DayOfWeek.SATURDAY, jalali)).isEqualTo(sunday)
        assertThat(templateDate(builtIn(null), sunday, DayOfWeek.SATURDAY, jalali)).isEqualTo(sunday)
    }
}
