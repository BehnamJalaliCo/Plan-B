package com.behnamjalali.planb.core.model

import java.time.Instant
import java.time.LocalDate

/** Stored in `challenges.status`; unknown values read as [ACTIVE]. */
enum class ChallengeStatus {
    ACTIVE,
    COMPLETED,
    FAILED,
    ABANDONED,
    ;

    companion object {
        fun fromKey(key: String?): ChallengeStatus = entries.firstOrNull { it.name == key } ?: ACTIVE
    }
}

/** A challenge (Plan-B Pro #29): do a habit on every due day for [targetDays] days from [startDate]. */
data class Challenge(
    val id: EntityId = NEW_ID,
    val kind: String = ChallengeRules.KIND_HABIT,
    val title: String,
    val targetDays: Int,
    val startDate: LocalDate,
    val habitId: EntityId? = null,
    val status: ChallengeStatus = ChallengeStatus.ACTIVE,
    val completedAt: Instant? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
) {
    /** The challenge's last day. */
    val endDate: LocalDate get() = startDate.plusDays(targetDays.toLong() - 1)
}

enum class ChallengeDayState {
    DONE,

    /** A due day that passed without reaching the target. */
    MISSED,

    /** Today, not done yet. */
    TODAY,
    FUTURE,

    /** Not due (by the habit's schedule), or not done in a "times per week" habit. */
    REST,
}

data class ChallengeProgress(
    val challenge: Challenge,
    /** The status the data gives (an abandoned challenge stays abandoned). */
    val status: ChallengeStatus,
    val days: List<Pair<LocalDate, ChallengeDayState>>,
    /** Done check-ins that count, and how many are needed in the whole challenge. */
    val done: Int,
    val required: Int,
    val completedOn: LocalDate?,
    val failedOn: LocalDate?,
) {
    val fraction: Float get() = if (required == 0) 0f else (done.toFloat() / required).coerceIn(0f, 1f)
}

/**
 * Challenge rules (Plan-B Pro #29), deterministic from the habit's check-ins:
 *
 * - Day schedules (every day, selected weekdays, every N days): every due day from the start to
 *   the end must reach the habit's target. A due day before today that was not done fails the
 *   challenge (from that day); today is never a failure while it lasts. It is completed on its
 *   last due day once every due day is done.
 * - "N times per week": the challenge is cut into 7-day blocks from its start; each needs N done
 *   days (a shorter last block proportionally fewer, rounded up). A block that ended short fails
 *   it; it is completed on the day the last block reached its count.
 * - Checking in a missed day afterwards brings the challenge back (statuses follow the data); an
 *   abandoned challenge or one whose habit was deleted stays abandoned.
 */
object ChallengeRules {
    const val KIND_HABIT = "HABIT_STREAK"
    val LENGTHS: List<Int> = listOf(7, 21, 30, 66)

    fun evaluate(challenge: Challenge, habit: Habit?, amounts: Map<LocalDate, Int>, today: LocalDate): ChallengeProgress {
        val dates = (0 until challenge.targetDays.coerceAtLeast(1)).map { challenge.startDate.plusDays(it.toLong()) }
        fun done(d: LocalDate) = habit != null && d <= today && HabitStats.isDone(habit, amounts, d)
        if (habit == null || challenge.status == ChallengeStatus.ABANDONED) {
            val days = dates.map { it to if (done(it)) ChallengeDayState.DONE else if (it > today) ChallengeDayState.FUTURE else ChallengeDayState.REST }
            return ChallengeProgress(challenge, ChallengeStatus.ABANDONED, days, days.count { it.second == ChallengeDayState.DONE }, dates.size, null, null)
        }
        val weekly = habit.schedule as? HabitSchedule.TimesPerWeek
        return if (weekly == null) daily(challenge, habit, dates, today, ::done) else weekly(challenge, weekly.times, dates, today, ::done)
    }

    private fun daily(challenge: Challenge, habit: Habit, dates: List<LocalDate>, today: LocalDate, done: (LocalDate) -> Boolean): ChallengeProgress {
        val days = dates.map { d ->
            d to when {
                !HabitStats.isScheduled(habit, d) -> if (done(d)) ChallengeDayState.DONE else ChallengeDayState.REST
                done(d) -> ChallengeDayState.DONE
                d < today -> ChallengeDayState.MISSED
                d == today -> ChallengeDayState.TODAY
                else -> ChallengeDayState.FUTURE
            }
        }
        val due = dates.filter { HabitStats.isScheduled(habit, it) }
        val doneDue = due.count(done)
        val failedOn = due.firstOrNull { it < today && !done(it) }
        val completedOn = if (due.isNotEmpty() && failedOn == null && doneDue == due.size) due.last() else null
        val status = when {
            failedOn != null -> ChallengeStatus.FAILED
            completedOn != null -> ChallengeStatus.COMPLETED
            else -> ChallengeStatus.ACTIVE
        }
        return ChallengeProgress(challenge, status, days, doneDue, due.size, completedOn, failedOn)
    }

    private fun weekly(challenge: Challenge, times: Int, dates: List<LocalDate>, today: LocalDate, done: (LocalDate) -> Boolean): ChallengeProgress {
        val days = dates.map { d ->
            d to when {
                done(d) -> ChallengeDayState.DONE
                d == today -> ChallengeDayState.TODAY
                d > today -> ChallengeDayState.FUTURE
                else -> ChallengeDayState.REST
            }
        }
        var counted = 0
        var required = 0
        var failedOn: LocalDate? = null
        var completedOn: LocalDate? = null
        var allMet = true
        dates.chunked(7).forEach { block ->
            val need = ((times.coerceIn(1, 7) * block.size + 6) / 7).coerceAtLeast(1)
            val doneDays = block.filter(done)
            required += need
            counted += minOf(doneDays.size, need)
            if (doneDays.size >= need) {
                val reached = doneDays[need - 1]
                completedOn = if (completedOn == null || reached > completedOn) reached else completedOn
            } else {
                allMet = false
                if (block.last() < today && failedOn == null) failedOn = block.last()
            }
        }
        val status = when {
            failedOn != null -> ChallengeStatus.FAILED
            allMet -> ChallengeStatus.COMPLETED
            else -> ChallengeStatus.ACTIVE
        }
        return ChallengeProgress(challenge, status, days, counted, required, completedOn.takeIf { allMet }, failedOn)
    }
}

enum class BadgeCategory { STREAK, FOCUS, TASKS, JOURNAL, MOOD, CHALLENGE }

/**
 * The badges (Plan-B Pro #29). [key] is stored in `badges.key` (never rename); names and
 * descriptions are string resources. [threshold] is in days (streaks, journal, challenge
 * length), hours (focus) or a count (tasks, mood check-ins).
 */
enum class BadgeDefinition(val key: String, val category: BadgeCategory, val threshold: Int) {
    STREAK_7("streak_7", BadgeCategory.STREAK, 7),
    STREAK_21("streak_21", BadgeCategory.STREAK, 21),
    STREAK_30("streak_30", BadgeCategory.STREAK, 30),
    STREAK_66("streak_66", BadgeCategory.STREAK, 66),
    STREAK_100("streak_100", BadgeCategory.STREAK, 100),
    STREAK_365("streak_365", BadgeCategory.STREAK, 365),
    FOCUS_1("focus_1h", BadgeCategory.FOCUS, 1),
    FOCUS_10("focus_10h", BadgeCategory.FOCUS, 10),
    FOCUS_50("focus_50h", BadgeCategory.FOCUS, 50),
    FOCUS_100("focus_100h", BadgeCategory.FOCUS, 100),
    FOCUS_500("focus_500h", BadgeCategory.FOCUS, 500),
    TASKS_10("tasks_10", BadgeCategory.TASKS, 10),
    TASKS_100("tasks_100", BadgeCategory.TASKS, 100),
    TASKS_500("tasks_500", BadgeCategory.TASKS, 500),
    TASKS_1000("tasks_1000", BadgeCategory.TASKS, 1_000),
    JOURNAL_7("journal_7", BadgeCategory.JOURNAL, 7),
    JOURNAL_30("journal_30", BadgeCategory.JOURNAL, 30),
    JOURNAL_100("journal_100", BadgeCategory.JOURNAL, 100),
    MOOD_10("mood_10", BadgeCategory.MOOD, 10),
    MOOD_100("mood_100", BadgeCategory.MOOD, 100),
    CHALLENGE_7("challenge_7", BadgeCategory.CHALLENGE, 7),
    CHALLENGE_21("challenge_21", BadgeCategory.CHALLENGE, 21),
    CHALLENGE_30("challenge_30", BadgeCategory.CHALLENGE, 30),
    CHALLENGE_66("challenge_66", BadgeCategory.CHALLENGE, 66),
    ;

    companion object {
        fun fromKey(key: String?): BadgeDefinition? = entries.firstOrNull { it.key == key }
    }
}

/** Everything the badge rules look at; lists of dates are in any order. */
data class AchievementInput(
    val today: LocalDate,
    val habits: List<Pair<Habit, Map<LocalDate, Int>>> = emptyList(),
    /** Completed focus sessions: their local start date and focused milliseconds, in time order. */
    val focusSessions: List<Pair<LocalDate, Long>> = emptyList(),
    /** The local completion date of every completed task. */
    val taskCompletions: List<LocalDate> = emptyList(),
    val journalDates: Set<LocalDate> = emptySet(),
    /** The date of every mood check-in. */
    val moodDates: List<LocalDate> = emptyList(),
    /** Completed challenges: their length and the day they were completed. */
    val completedChallenges: List<Pair<Int, LocalDate>> = emptyList(),
)

/** A badge with the user's progress towards it; [earnedOn] is the day it was first reached. */
data class BadgeProgress(val badge: BadgeDefinition, val current: Int, val earnedOn: LocalDate?) {
    val earned: Boolean get() = earnedOn != null
    val fraction: Float get() = (current.toFloat() / badge.threshold).coerceIn(0f, 1f)
}

/**
 * Badge rules (Plan-B Pro #29). Every badge and the day it was earned follow from the data
 * alone, so evaluating again (after a restore, on another device) gives the same result:
 *
 * - Streaks: the longest run of done due days of any habit with a day schedule ("times per
 *   week" habits count weeks and are not part of these badges).
 * - Focus: completed sessions' time, summed in time order; earned the day the sum reached it.
 * - Tasks and mood check-ins: the day of the N-th one.
 * - Journal: the first day a run of consecutive days with a page reached the length.
 * - Challenges: completing a challenge of at least that many days.
 */
object BadgeRules {
    fun evaluate(input: AchievementInput): List<BadgeProgress> {
        val streakLengths = BadgeDefinition.entries.filter { it.category == BadgeCategory.STREAK }.map { it.threshold }
        val milestones = input.habits.map { (habit, amounts) -> HabitAnalytics.streakMilestones(habit, amounts, input.today, streakLengths) }
        val bestStreak = input.habits.maxOfOrNull { (habit, amounts) ->
            if (habit.schedule is HabitSchedule.TimesPerWeek) 0 else HabitStats.bestStreakDays(habit, amounts, input.today)
        } ?: 0
        val focusMillis = input.focusSessions.sumOf { it.second }
        val tasks = input.taskCompletions.sorted()
        val moods = input.moodDates.sorted()
        val journalRuns = runMilestones(input.journalDates)
        return BadgeDefinition.entries.map { badge ->
            val n = badge.threshold
            when (badge.category) {
                BadgeCategory.STREAK -> BadgeProgress(badge, bestStreak, milestones.mapNotNull { it[n] }.minOrNull())
                BadgeCategory.FOCUS -> BadgeProgress(badge, (focusMillis / HOUR).toInt(), focusReached(input.focusSessions, n * HOUR))
                BadgeCategory.TASKS -> BadgeProgress(badge, tasks.size, tasks.getOrNull(n - 1))
                BadgeCategory.MOOD -> BadgeProgress(badge, moods.size, moods.getOrNull(n - 1))
                BadgeCategory.JOURNAL -> BadgeProgress(badge, journalRuns.longest, journalRuns.firstReaching(n))
                BadgeCategory.CHALLENGE -> {
                    val qualifying = input.completedChallenges.filter { it.first >= n }
                    BadgeProgress(badge, input.completedChallenges.maxOfOrNull { it.first } ?: 0, qualifying.minOfOrNull { it.second })
                }
            }
        }
    }

    private const val HOUR = 3_600_000L

    private fun focusReached(sessions: List<Pair<LocalDate, Long>>, target: Long): LocalDate? {
        var sum = 0L
        sessions.forEach { (date, millis) ->
            sum += millis
            if (sum >= target) return date
        }
        return null
    }

    private class Runs(val longest: Int, private val reached: Map<Int, LocalDate>) {
        fun firstReaching(n: Int): LocalDate? = reached[n]
    }

    /** For runs of consecutive dates: the longest, and for each run length the day it was reached. */
    private fun runMilestones(dates: Set<LocalDate>): Runs {
        val reached = mutableMapOf<Int, LocalDate>()
        var longest = 0
        dates.sorted().forEach { date ->
            if (date.minusDays(1) !in dates) {
                var length = 0
                var day = date
                while (day in dates) {
                    length++
                    if (reached[length]?.let { day < it } != false) reached[length] = day
                    day = day.plusDays(1)
                }
                longest = maxOf(longest, length)
            }
        }
        return Runs(longest, reached)
    }
}
