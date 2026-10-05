package com.behnamjalali.planb.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** The journal page of one day (Plan-B Pro #25): its text lives in note [noteId]. */
data class JournalEntry(
    val id: EntityId = NEW_ID,
    val date: LocalDate,
    val noteId: EntityId,
    /** Key of the prompt shown that day (see [JournalPrompts]), if any. */
    val promptId: String? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
)

/** A mood and energy check-in (1..5 each, at least one set). */
data class MoodEntry(
    val id: EntityId = NEW_ID,
    val date: LocalDate,
    val time: LocalTime? = null,
    val mood: Int? = null,
    val energy: Int? = null,
    val tags: List<String> = emptyList(),
    val noteId: EntityId? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
) {
    companion object {
        val RANGE = 1..5
    }
}

/**
 * Daily writing prompts. Built-in prompts are keyed `p01`…`pNN` (their texts are string
 * resources in both languages); the user's own prompts are keyed `custom:<hash of the text>`,
 * so a page keeps pointing at its prompt when others are added or removed.
 *
 * Rotation: every prompt (built-in and the user's) comes once before any repeats, in an order
 * that is fixed for the whole list but mixed (a stride through the list that is coprime with
 * its size), and the prompt of a day depends only on the date: day d shows prompt
 * `order[(d + shift) mod size]`. "Another prompt" moves [shift] by one for that page.
 */
object JournalPrompts {
    const val BUILT_IN = 60
    const val CUSTOM_PREFIX = "custom:"

    val builtInKeys: List<String> = (1..BUILT_IN).map { "p" + it.toString().padStart(2, '0') }

    fun customKey(text: String): String = CUSTOM_PREFIX + text.trim().hashCode().toUInt().toString(16)

    /** Every key in rotation: the built-in prompts, then the user's own. */
    fun keys(custom: List<String>): List<String> =
        builtInKeys + custom.map { it.trim() }.filter { it.isNotEmpty() }.distinct().map(::customKey)

    /** The prompt of [date], [shift] steps further along the rotation. */
    fun forDate(date: LocalDate, custom: List<String>, shift: Int = 0): String {
        val keys = keys(custom)
        val size = keys.size
        val stride = stride(size)
        val step = Math.floorMod(date.toEpochDay() + shift, size.toLong()).toInt()
        return keys[(step.toLong() * stride % size).toInt()]
    }

    /** A stride coprime with [size] near 2/5 of it, so neighbours in the list are not shown on neighbouring days. */
    internal fun stride(size: Int): Int {
        if (size <= 2) return 1
        var s = (size * 2 / 5).coerceAtLeast(1)
        while (gcd(s, size) != 1) s++
        return s
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    /** The text of a custom key, or null for built-in or unknown keys. */
    fun customText(key: String?, custom: List<String>): String? =
        if (key?.startsWith(CUSTOM_PREFIX) == true) custom.firstOrNull { customKey(it) == key }?.trim() else null

    /** The 1-based number of a built-in key (`p07` → 7), or null. */
    fun builtInNumber(key: String?): Int? =
        key?.takeIf { it.length == 3 && it[0] == 'p' }?.substring(1)?.toIntOrNull()?.takeIf { it in 1..BUILT_IN }
}

/** One day of the mood calendar: the averages of that day's check-ins. */
data class MoodDay(val date: LocalDate, val mood: Float?, val energy: Float?, val entries: Int, val journaled: Boolean) {
    /** The mood shown as a color (1..5, rounded half up), or null. */
    val moodLevel: Int? get() = mood?.let { (it + 0.5f).toInt().coerceIn(1, 5) }
}

data class JournalInsights(
    /** Average mood per weekday, Monday first; null where there is no check-in. */
    val moodByWeekday: Map<DayOfWeek, Float?>,
    /** Days in a row with a journal page, ending today (or yesterday, if today has none yet). */
    val streak: Int,
    val longestStreak: Int,
    val averageMood: Float?,
    val averageEnergy: Float?,
    val pages: Int,
    val checkIns: Int,
)

object MoodCalendar {
    /** One [MoodDay] per date that has a check-in or a journal page, by date. */
    fun days(moods: List<MoodEntry>, journalDates: Set<LocalDate>): Map<LocalDate, MoodDay> {
        val byDate = moods.groupBy { it.date }
        return (byDate.keys + journalDates).associateWith { date ->
            val entries = byDate[date].orEmpty()
            MoodDay(
                date = date,
                mood = entries.mapNotNull { it.mood?.takeIf { m -> m in MoodEntry.RANGE } }.averageOrNull(),
                energy = entries.mapNotNull { it.energy?.takeIf { e -> e in MoodEntry.RANGE } }.averageOrNull(),
                entries = entries.size,
                journaled = date in journalDates,
            )
        }.toSortedMap()
    }

    fun insights(moods: List<MoodEntry>, journalDates: Set<LocalDate>, today: LocalDate): JournalInsights {
        val valid = moods.filter { entry -> entry.mood?.let { it in MoodEntry.RANGE } == true }
        val byWeekday = DayOfWeek.entries.associateWith { day ->
            valid.filter { it.date.dayOfWeek == day }.mapNotNull { it.mood }.averageOrNull()
        }
        return JournalInsights(
            moodByWeekday = byWeekday,
            streak = currentStreak(journalDates, today),
            longestStreak = longestStreak(journalDates),
            averageMood = valid.mapNotNull { it.mood }.averageOrNull(),
            averageEnergy = moods.mapNotNull { it.energy?.takeIf { e -> e in MoodEntry.RANGE } }.averageOrNull(),
            pages = journalDates.size,
            checkIns = moods.size,
        )
    }

    fun currentStreak(dates: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in dates) today else today.minusDays(1)
        var count = 0
        while (day in dates) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun longestStreak(dates: Set<LocalDate>): Int {
        var best = 0
        dates.forEach { date ->
            if (date.minusDays(1) !in dates) {
                var length = 0
                var day = date
                while (day in dates) {
                    length++
                    day = day.plusDays(1)
                }
                best = maxOf(best, length)
            }
        }
        return best
    }

    private fun List<Int>.averageOrNull(): Float? = if (isEmpty()) null else (sum().toFloat() / size)
}
