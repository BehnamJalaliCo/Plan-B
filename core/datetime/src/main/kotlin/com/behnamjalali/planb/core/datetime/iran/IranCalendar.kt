package com.behnamjalali.planb.core.datetime.iran

import com.behnamjalali.planb.core.datetime.JalaliEngine
import com.behnamjalali.planb.core.datetime.R
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Iran's official holidays and well-known occasions (Plan-B Pro #2). Solar (Jalali) days are a
 * fixed table; lunar (Hijri) days fall where [HijriCalendar] puts that Hijri date, so they move
 * about 11 days earlier every solar year. [holiday] marks the 26 official public holidays.
 * Ids are stable; names come from resources (Persian and English).
 */
enum class Occasion(
    val lunar: Boolean,
    val month: Int,
    val day: Int,
    val holiday: Boolean,
    /** String resource of the name. */
    val title: Int,
) {
    // Official holidays, solar (10 days).
    NOWRUZ_1(false, 1, 1, true, R.string.occasion_nowruz),
    NOWRUZ_2(false, 1, 2, true, R.string.occasion_nowruz),
    NOWRUZ_3(false, 1, 3, true, R.string.occasion_nowruz),
    NOWRUZ_4(false, 1, 4, true, R.string.occasion_nowruz),
    REPUBLIC_DAY(false, 1, 12, true, R.string.occasion_republic_day),
    NATURE_DAY(false, 1, 13, true, R.string.occasion_nature_day),
    KHOMEINI_DEMISE(false, 3, 14, true, R.string.occasion_khomeini_demise),
    KHORDAD_15(false, 3, 15, true, R.string.occasion_khordad_15),
    REVOLUTION_DAY(false, 11, 22, true, R.string.occasion_revolution_day),
    OIL_NATIONALIZATION(false, 12, 29, true, R.string.occasion_oil_nationalization),

    // Official holidays, lunar (16 days).
    TASUA(true, 1, 9, true, R.string.occasion_tasua),
    ASHURA(true, 1, 10, true, R.string.occasion_ashura),
    ARBAEEN(true, 2, 20, true, R.string.occasion_arbaeen),
    PROPHET_DEMISE(true, 2, 28, true, R.string.occasion_prophet_demise),

    /** The last day of Safar (29 or 30), which is how the official calendar places it. */
    IMAM_REZA_MARTYRDOM(true, 2, 30, true, R.string.occasion_imam_reza),
    IMAM_HASAN_ASKARI(true, 3, 8, true, R.string.occasion_imam_hasan_askari),
    PROPHET_BIRTH(true, 3, 17, true, R.string.occasion_prophet_birth),
    FATIMA_MARTYRDOM(true, 6, 3, true, R.string.occasion_fatima_martyrdom),
    IMAM_ALI_BIRTH(true, 7, 13, true, R.string.occasion_imam_ali_birth),
    MABATH(true, 7, 27, true, R.string.occasion_mabath),
    MAHDI_BIRTH(true, 8, 15, true, R.string.occasion_mahdi_birth),
    IMAM_ALI_MARTYRDOM(true, 9, 21, true, R.string.occasion_imam_ali_martyrdom),
    EID_FITR(true, 10, 1, true, R.string.occasion_eid_fitr),
    EID_FITR_2(true, 10, 2, true, R.string.occasion_eid_fitr_2),
    IMAM_SADIQ_MARTYRDOM(true, 10, 25, true, R.string.occasion_imam_sadiq),
    EID_ADHA(true, 12, 10, true, R.string.occasion_eid_adha),
    EID_GHADIR(true, 12, 18, true, R.string.occasion_eid_ghadir),

    // Occasions (not public holidays), solar.
    SAADI_DAY(false, 2, 1, false, R.string.occasion_saadi),
    TEACHERS_DAY(false, 2, 12, false, R.string.occasion_teachers),
    FERDOWSI_DAY(false, 2, 25, false, R.string.occasion_ferdowsi),
    TIRGAN(false, 4, 13, false, R.string.occasion_tirgan),
    CONSTITUTION_DAY(false, 5, 14, false, R.string.occasion_constitution),
    AVICENNA_DAY(false, 6, 1, false, R.string.occasion_avicenna),
    SHAHRIAR_DAY(false, 6, 27, false, R.string.occasion_shahriar),
    RUMI_DAY(false, 7, 8, false, R.string.occasion_rumi),
    HAFEZ_DAY(false, 7, 20, false, R.string.occasion_hafez),
    MEHREGAN(false, 7, 10, false, R.string.occasion_mehregan),
    STUDENTS_DAY(false, 9, 16, false, R.string.occasion_students),
    YALDA(false, 9, 30, false, R.string.occasion_yalda),
    SEPANDARMAZGAN(false, 11, 29, false, R.string.occasion_sepandarmazgan),
    ENGINEERS_DAY(false, 12, 5, false, R.string.occasion_engineers),
    TREE_PLANTING_DAY(false, 12, 15, false, R.string.occasion_tree_planting),

    /** The evening before the last Wednesday of the year; [month]/[day] are unused. */
    CHAHARSHANBE_SURI(false, 12, 0, false, R.string.occasion_chaharshanbe_suri),

    // Occasions, lunar.
    ISLAMIC_NEW_YEAR(true, 1, 1, false, R.string.occasion_islamic_new_year),
    MOTHERS_DAY(true, 6, 20, false, R.string.occasion_mothers_day),
    IMAM_HUSSEIN_BIRTH(true, 8, 3, false, R.string.occasion_imam_hussein_birth),
    RAMADAN_START(true, 9, 1, false, R.string.occasion_ramadan_start),

    /** The nights of Qadr begin on the evening before the 19th and the 23rd (as in the official calendar). */
    QADR_NIGHT_19(true, 9, 18, false, R.string.occasion_qadr_night),
    QADR_NIGHT_23(true, 9, 22, false, R.string.occasion_qadr_night),
    GIRLS_DAY(true, 11, 1, false, R.string.occasion_girls_day),
    IMAM_REZA_BIRTH(true, 11, 11, false, R.string.occasion_imam_reza_birth),
    ARAFAH(true, 12, 9, false, R.string.occasion_arafah),
    ;

    val id: String get() = name.lowercase()
}

/** One occasion on one day. */
data class OccasionDay(val occasion: Occasion, val date: LocalDate) {
    val holiday: Boolean get() = occasion.holiday
    val lunar: Boolean get() = occasion.lunar
}

/** Everything [IranCalendar] knows about one day. */
data class IranDayInfo(
    val date: LocalDate,
    val occasions: List<OccasionDay>,
    val hijri: HijriDate,
) {
    /** An official public holiday (Fridays are counted separately, see [IranCalendar.isWeekend]). */
    val isHoliday: Boolean get() = occasions.any { it.holiday }
}

class IranCalendar(val hijri: HijriCalendar) {
    /** Occasions from [from] to [to] inclusive, in date order (holidays first within a day). */
    fun occasionsBetween(from: LocalDate, to: LocalDate): List<OccasionDay> {
        if (to < from) return emptyList()
        val result = ArrayList<OccasionDay>()
        val solarYears = JalaliEngine.toCalendarDate(from).year..JalaliEngine.toCalendarDate(to).year
        for (year in solarYears) {
            Occasion.entries.filter { !it.lunar }.forEach { occasion ->
                val date = solarDate(occasion, year) ?: return@forEach
                if (date in from..to) result += OccasionDay(occasion, date)
            }
        }
        val hijriYears = hijri.toHijri(from).year..hijri.toHijri(to).year
        for (year in hijriYears) {
            Occasion.entries.filter { it.lunar }.forEach { occasion ->
                val date = hijri.toLocalDate(year, occasion.month, occasion.day)
                if (date in from..to) result += OccasionDay(occasion, date)
            }
        }
        return result.sortedWith(compareBy<OccasionDay>({ it.date }, { !it.holiday }, { it.occasion.ordinal }))
    }

    fun dayInfo(date: LocalDate): IranDayInfo = IranDayInfo(date, occasionsBetween(date, date), hijri.toHijri(date))

    private fun solarDate(occasion: Occasion, year: Int): LocalDate? {
        if (occasion == Occasion.CHAHARSHANBE_SURI) {
            // Tuesday evening before the last Wednesday ahead of Nowruz.
            val nowruz = JalaliEngine.toLocalDate(year + 1, 1, 1)
            var wednesday = nowruz.minusDays(1)
            while (wednesday.dayOfWeek != DayOfWeek.WEDNESDAY) wednesday = wednesday.minusDays(1)
            return wednesday.minusDays(1)
        }
        // A day beyond the month (never the case in this table) is not clamped onto another day.
        if (occasion.day > JalaliEngine.monthLength(year, occasion.month)) return null
        return JalaliEngine.toLocalDate(year, occasion.month, occasion.day)
    }

    companion object {
        /** Iran's weekly day off. */
        fun isWeekend(date: LocalDate): Boolean = date.dayOfWeek == DayOfWeek.FRIDAY

        /** The calendar with the bundled published Hijri month starts (see [PublishedHijriMonths]). */
        val Default: IranCalendar by lazy { IranCalendar(HijriCalendar(PublishedHijriMonths.bundled())) }
    }
}
