package com.behnamjalali.planb.core.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * Distraction-free writing (Plan-B Pro #24): the daily word goal, its streak and the words
 * written today in writing mode. Part of the exported preferences.
 */
data class WritingSettings(
    /** Words to write per day; 0 = no goal. */
    val dailyGoal: Int = DEFAULT_GOAL,
    /** Keep the line being written in the middle of the screen. */
    val typewriter: Boolean = false,
    /** The day [wordsToday] belongs to. */
    val progressDate: LocalDate? = null,
    val wordsToday: Int = 0,
    /** Days in a row the goal was reached, up to [goalReachedOn]. */
    val streak: Int = 0,
    val goalReachedOn: LocalDate? = null,
) {
    fun wordsOn(date: LocalDate): Int = if (progressDate == date) wordsToday else 0

    /** The streak as it stands on [date]: it is broken once a whole day passed without the goal. */
    fun streakOn(date: LocalDate): Int {
        val last = goalReachedOn ?: return 0
        return if (last == date || last == date.minusDays(1)) streak else 0
    }

    /** Adds [words] written on [date]; reaching the goal extends (or starts) the streak once per day. */
    fun addWords(date: LocalDate, words: Int): WritingSettings {
        if (words <= 0) return this
        val total = (wordsOn(date).toLong() + words).coerceAtMost(MAX_WORDS_PER_DAY.toLong()).toInt()
        var next = copy(progressDate = date, wordsToday = total)
        if (dailyGoal > 0 && total >= dailyGoal && goalReachedOn != date) {
            next = next.copy(streak = streakOn(date) + 1, goalReachedOn = date)
        }
        return next
    }

    companion object {
        const val DEFAULT_GOAL = 300
        const val MAX_GOAL = 20_000
        const val MAX_WORDS_PER_DAY = 1_000_000
        val GOAL_CHOICES: List<Int> = listOf(0, 100, 250, 300, 500, 750, 1_000, 2_000)
    }
}

/** The daily journal (Plan-B Pro #25): its reminder and the user's own prompts. Exported with the preferences. */
data class JournalSettings(
    val reminder: Boolean = false,
    val reminderTime: LocalTime = DEFAULT_REMINDER,
    val customPrompts: List<String> = emptyList(),
) {
    companion object {
        val DEFAULT_REMINDER: LocalTime = LocalTime.of(21, 30)
        const val MAX_CUSTOM_PROMPTS = 100
        const val MAX_PROMPT_LENGTH = 300
    }
}
