package com.behnamjalali.planb.core.model

/**
 * Ambient sounds of Focus Pro (#26). They are generated on the device (no audio files), so they
 * add nothing to the download. [id] is stored in `focus_sessions.sound_id` and in the
 * preferences; never rename it. Unknown ids read as no sound.
 */
enum class AmbientSound(val id: String) {
    RAIN("rain"),
    OCEAN("ocean"),
    BROWN("brown"),
    PINK("pink"),
    WHITE("white"),
    ;

    companion object {
        fun fromId(id: String?): AmbientSound? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Focus Pro preferences (#26), part of the exported preferences. The chosen [sound] and [strict]
 * are copied onto each session when it starts (`focus_sessions.sound_id`, `strict`), so a
 * running session keeps them even if the preferences change.
 */
data class FocusProSettings(
    /** Default ambient sound for new sessions; null = silence. */
    val sound: AmbientSound? = null,
    /** Playback volume, 0..100. */
    val volume: Int = DEFAULT_VOLUME,
    /** Turn on Do Not Disturb while a session runs (needs the user's permission). */
    val strict: Boolean = false,
    /** Focus minutes to reach per day; 0 = no goal. */
    val dailyGoalMinutes: Int = 0,
    /** A long break after every [longBreakEvery] completed sessions of a day. */
    val longBreakEvery: Int = DEFAULT_LONG_BREAK_EVERY,
    val longBreakMinutes: Int = DEFAULT_LONG_BREAK_MINUTES,
) {
    companion object {
        const val DEFAULT_VOLUME = 60
        const val DEFAULT_LONG_BREAK_EVERY = 4
        const val DEFAULT_LONG_BREAK_MINUTES = 15
        const val MAX_DAILY_GOAL = 12 * 60
        val GOAL_CHOICES: List<Int> = listOf(0, 30, 60, 90, 120, 180, 240, 300)
        val LONG_BREAK_EVERY_CHOICES: List<Int> = listOf(2, 3, 4, 5, 6)
        val LONG_BREAK_CHOICES: List<Int> = listOf(10, 15, 20, 25, 30)
    }
}

/** What to do after a completed session (Pomodoro cycle with long breaks). */
data class FocusCycle(
    /** Completed sessions of the day, counting the one just finished. */
    val completedToday: Int,
    /** 1-based position of the next session in the current cycle. */
    val positionInCycle: Int,
    val cycleLength: Int,
    /** The break that should follow the last completed session. */
    val longBreak: Boolean,
    val breakMinutes: Int,
) {
    companion object {
        /**
         * After [completedToday] completed sessions, the next break is long when that number is a
         * positive multiple of [every]; otherwise it is the short break.
         */
        fun after(completedToday: Int, every: Int, shortBreak: Int, longBreak: Int): FocusCycle {
            val cycle = every.coerceAtLeast(1)
            val long = completedToday > 0 && completedToday % cycle == 0
            return FocusCycle(
                completedToday = completedToday,
                positionInCycle = completedToday % cycle + 1,
                cycleLength = cycle,
                longBreak = long,
                breakMinutes = if (long) longBreak else shortBreak,
            )
        }
    }
}

/**
 * Strict mode's Do Not Disturb bookkeeping (#26). Plan-B only ever changes the system's
 * interruption filter while a strict session is running, remembers what was there before, and
 * puts it back when the session pauses or ends, also after the process was killed: [decide] is
 * called with the stored [StrictModeState] whenever the session changes and at app start.
 *
 * Filters use the platform's `NotificationManager.INTERRUPTION_FILTER_*` numbers.
 */
data class StrictModeState(
    /** The filter that was active before Plan-B changed it. */
    val previousFilter: Int,
    /** The filter Plan-B set. */
    val appliedFilter: Int,
)

object StrictMode {
    const val FILTER_ALL = 1
    const val FILTER_PRIORITY = 2

    sealed interface Action {
        /** Nothing to change. */
        data object None : Action

        /** Set [filter] and remember [state]. */
        data class Apply(val filter: Int, val state: StrictModeState) : Action

        /** Put [filter] back (null: leave the filter as it is) and forget the stored state. */
        data class Restore(val filter: Int?) : Action
    }

    /**
     * Decides what to do. [wanted]: a strict session is running and Plan-B may change Do Not
     * Disturb. [current]: the system filter now (null when unknown, e.g. no access).
     *
     * - Do Not Disturb that was already on is never touched (and so never "restored").
     * - On restore, a filter the user changed themselves in the meantime is left alone.
     */
    fun decide(wanted: Boolean, stored: StrictModeState?, current: Int?, hasAccess: Boolean): Action = when {
        wanted && stored == null && hasAccess && current == FILTER_ALL ->
            Action.Apply(FILTER_PRIORITY, StrictModeState(previousFilter = FILTER_ALL, appliedFilter = FILTER_PRIORITY))
        wanted -> Action.None
        stored == null -> Action.None
        hasAccess && current == stored.appliedFilter -> Action.Restore(stored.previousFilter)
        else -> Action.Restore(null)
    }
}
