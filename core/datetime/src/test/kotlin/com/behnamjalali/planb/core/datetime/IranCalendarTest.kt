package com.behnamjalali.planb.core.datetime

import com.behnamjalali.planb.core.datetime.iran.HijriCalendar
import com.behnamjalali.planb.core.datetime.iran.HijriDate
import com.behnamjalali.planb.core.datetime.iran.HijriMonth
import com.behnamjalali.planb.core.datetime.iran.IranCalendar
import com.behnamjalali.planb.core.datetime.iran.Occasion
import com.behnamjalali.planb.core.datetime.iran.PublishedHijriMonths
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Iran's official holidays, occasions and Hijri dates (Plan-B Pro #2). Reference: the calendar
 * published for 1405 and 1406 (see core/datetime/src/main/resources/iran_calendar/).
 */
@RunWith(RobolectricTestRunner::class)
class IranCalendarTest {
    private val iran = IranCalendar.Default
    private fun j(year: Int, month: Int, day: Int): LocalDate = JalaliEngine.toLocalDate(year, month, day)

    private fun holidaysOf(calendar: IranCalendar, year: Int): Set<LocalDate> =
        calendar.occasionsBetween(j(year, 1, 1), j(year + 1, 1, 1).minusDays(1)).filter { it.holiday }.map { it.date }.toSet()

    @Test
    fun solarHolidays_knownDates() {
        val holidays = holidaysOf(iran, 1405)
        // Nowruz 1–4 Farvardin 1405 = 21–24 March 2026.
        (1..4).forEach { assertThat(holidays).contains(j(1405, 1, it)) }
        assertThat(j(1405, 1, 1)).isEqualTo(LocalDate.of(2026, 3, 21))
        assertThat(holidays).contains(j(1405, 1, 12)) // Islamic Republic Day
        assertThat(holidays).contains(j(1405, 1, 13)) // Nature Day
        assertThat(holidays).contains(j(1405, 3, 14)) // Imam Khomeini
        assertThat(holidays).contains(j(1405, 3, 15)) // 15 Khordad uprising
        assertThat(holidays).doesNotContain(j(1405, 3, 12))
        assertThat(holidays).contains(j(1405, 11, 22)) // Revolution
        assertThat(holidays).contains(j(1405, 12, 29)) // Oil nationalization
        assertThat(iran.dayInfo(LocalDate.of(2027, 2, 11)).occasions.map { it.occasion }).contains(Occasion.REVOLUTION_DAY)
        // The same days in a leap year (1403 has 30 Esfand).
        assertThat(holidaysOf(iran, 1403)).containsAtLeast(j(1403, 1, 1), j(1403, 1, 13), j(1403, 3, 14), j(1403, 11, 22), j(1403, 12, 29))
        assertThat(holidaysOf(iran, 1403)).doesNotContain(j(1403, 12, 30))
    }

    @Test
    fun officialHolidays1405_matchThePublishedCalendar() {
        val published = listOf(
            1 to 1, 1 to 2, 1 to 3, 1 to 4, 1 to 12, 1 to 13, 1 to 25, 3 to 6, 3 to 14, 3 to 15, 4 to 3, 4 to 4,
            5 to 13, 5 to 21, 5 to 22, 5 to 30, 6 to 8, 8 to 22, 10 to 2, 10 to 16, 11 to 4, 11 to 22, 12 to 9,
            12 to 19, 12 to 20, 12 to 29,
        ).map { (m, d) -> j(1405, m, d) }.toSet()
        assertThat(holidaysOf(iran, 1405)).isEqualTo(published)
    }

    @Test
    fun officialHolidays1406_matchThePublishedCalendar() {
        val published = listOf(
            1 to 1, 1 to 2, 1 to 3, 1 to 4, 1 to 12, 1 to 13, 1 to 14, 2 to 27, 3 to 4, 3 to 14, 3 to 15, 3 to 25,
            3 to 26, 5 to 3, 5 to 11, 5 to 12, 5 to 20, 5 to 29, 8 to 12, 9 to 21, 10 to 5, 10 to 23, 11 to 22,
            11 to 29, 12 to 8, 12 to 9, 12 to 29,
        ).map { (m, d) -> j(1406, m, d) }.toSet()
        assertThat(holidaysOf(iran, 1406)).isEqualTo(published)
    }

    @Test
    fun lunarHolidays1405_onTheirPublishedDays() {
        fun dayOf(occasion: Occasion, from: LocalDate = j(1405, 1, 1), to: LocalDate = j(1405, 12, 29)) =
            iran.occasionsBetween(from, to).filter { it.occasion == occasion }.map { it.date }
        // Eid al-Fitr 1447 fell on Nowruz; Eid al-Fitr 1448 on 19 Esfand 1405.
        assertThat(dayOf(Occasion.EID_FITR)).containsExactly(j(1405, 1, 1), j(1405, 12, 19)).inOrder()
        assertThat(dayOf(Occasion.TASUA)).containsExactly(j(1405, 4, 3))
        assertThat(dayOf(Occasion.ASHURA)).containsExactly(j(1405, 4, 4))
        assertThat(dayOf(Occasion.ARBAEEN)).containsExactly(j(1405, 5, 13))
        // The last day of a 29-day Safar.
        assertThat(dayOf(Occasion.IMAM_REZA_MARTYRDOM)).containsExactly(j(1405, 5, 22))
        assertThat(dayOf(Occasion.FATIMA_MARTYRDOM)).containsExactly(j(1405, 8, 22))
        assertThat(dayOf(Occasion.IMAM_ALI_BIRTH)).containsExactly(j(1405, 10, 2))
        assertThat(dayOf(Occasion.MAHDI_BIRTH)).containsExactly(j(1405, 11, 4))
        assertThat(dayOf(Occasion.IMAM_ALI_MARTYRDOM)).containsExactly(j(1405, 12, 9))
        assertThat(dayOf(Occasion.EID_ADHA)).containsExactly(j(1405, 3, 6))
        assertThat(dayOf(Occasion.EID_GHADIR)).containsExactly(j(1405, 3, 14))
    }

    @Test
    fun computedLunarDates_withoutThePublishedCalendar_areAtMostOneDayOff() {
        val computed = IranCalendar(HijriCalendar())
        val published = iran.occasionsBetween(j(1405, 1, 1), j(1406, 12, 29)).filter { it.lunar && it.holiday }
        val calculated = computed.occasionsBetween(j(1404, 12, 20), j(1407, 1, 10)).filter { it.lunar && it.holiday }
        published.forEach { day ->
            val nearest = calculated.filter { it.occasion == day.occasion }.minOf { abs(it.date.toEpochDay() - day.date.toEpochDay()) }
            assertThat(nearest).isAtMost(1L)
        }
    }

    @Test
    fun hijriDates() {
        // 12 Mehr 1405 (4 October 2026) = 22 Rabi' al-Thani 1448 in Iran's calendar.
        assertThat(iran.hijri.toHijri(LocalDate.of(2026, 10, 4))).isEqualTo(HijriDate(1448, 4, 22))
        assertThat(iran.hijri.toHijri(j(1405, 1, 1))).isEqualTo(HijriDate(1447, 10, 1))
        assertThat(iran.hijri.toHijri(LocalDate.of(2026, 6, 16))).isEqualTo(HijriDate(1448, 1, 1))
        // Round trip and continuity over the published years and beyond.
        var date = LocalDate.of(2025, 1, 1)
        var previous = iran.hijri.toHijri(date.minusDays(1))
        while (date <= LocalDate.of(2029, 12, 31)) {
            val h = iran.hijri.toHijri(date)
            assertThat(iran.hijri.toLocalDate(h.year, h.month, h.day)).isEqualTo(date)
            if (h.day == 1) {
                assertThat(previous.day).isIn(29..30)
            } else {
                assertThat(h.day).isEqualTo(previous.day + 1)
            }
            previous = h
            date = date.plusDays(1)
        }
    }

    @Test
    fun publishedMonths_areBundledAndParsedTolerantly() {
        val bundled = PublishedHijriMonths.bundled()
        assertThat(bundled).hasSize(25)
        assertThat(bundled[HijriMonth(1448, 1)]).isEqualTo(LocalDate.of(2026, 6, 16))
        assertThat(PublishedHijriMonths.parse("""{"monthStarts":{"1448-01":"2026-06-16","1448-13":"2026-07-01","x":"y","1448-02":"bad"}}"""))
            .containsExactly(HijriMonth(1448, 1), LocalDate.of(2026, 6, 16))
        assertThat(PublishedHijriMonths.parse("not json")).isEmpty()
        // An implausible override (far from the calculation) is ignored.
        val odd = HijriCalendar(mapOf(HijriMonth(1448, 1) to LocalDate.of(2026, 7, 1)))
        assertThat(odd.publishedMonths).isEmpty()
    }

    @Test
    fun occasions_andTheWeekend() {
        val yalda = iran.dayInfo(j(1405, 9, 30))
        assertThat(yalda.occasions.map { it.occasion }).contains(Occasion.YALDA)
        assertThat(yalda.isHoliday).isFalse()
        // Chaharshanbe Suri: the Tuesday before the last Wednesday of 1405.
        val suri = iran.occasionsBetween(j(1405, 12, 1), j(1405, 12, 29)).single { it.occasion == Occasion.CHAHARSHANBE_SURI }
        assertThat(suri.date).isEqualTo(LocalDate.of(2027, 3, 16))
        assertThat(suri.date.dayOfWeek).isEqualTo(DayOfWeek.TUESDAY)
        assertThat(IranCalendar.isWeekend(LocalDate.of(2026, 10, 9))).isTrue()
        assertThat(IranCalendar.isWeekend(LocalDate.of(2026, 10, 8))).isFalse()
        // Holidays come first within a day.
        val ghadir = iran.dayInfo(j(1405, 3, 14)).occasions
        assertThat(ghadir.map { it.occasion }).containsExactly(Occasion.KHOMEINI_DEMISE, Occasion.EID_GHADIR)
    }
}
