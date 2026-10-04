package com.behnamjalali.planb.widget

/**
 * Everything a widget shows, already localized (language, calendar and digits follow the app
 * settings). Widget composables only render these, which keeps them easy to test.
 */
sealed interface WidgetUi<out T> {
    /** Plan-B Pro is needed: the widget may stay on the home screen but shows this instead. */
    data class Locked(val title: String, val message: String, val action: String) : WidgetUi<Nothing>

    data class Ready<T>(val data: T) : WidgetUi<T>
}

data class WidgetTask(val id: Long, val title: String, val meta: String?)

data class TodayWidgetState(
    val title: String,
    val date: String,
    val progress: Float,
    val progressLabel: String,
    val tasks: List<WidgetTask>,
    val empty: String,
    val completeLabel: String,
)

data class WidgetHabit(val id: Long, val title: String, val done: Boolean, val detail: String)

data class HabitsWidgetState(val title: String, val date: String, val habits: List<WidgetHabit>, val empty: String, val checkInLabel: String)

data class FocusWidgetState(
    val title: String,
    val status: String,
    /** Remaining time as text (also what a paused or idle timer shows). */
    val remaining: String,
    val remainingMillis: Long,
    val running: Boolean,
    val action: String,
)

data class CalendarCell(val day: String, val inMonth: Boolean, val today: Boolean, val busy: Boolean, val description: String)

data class CalendarWidgetState(val title: String, val weekdays: List<String>, val weeks: List<List<CalendarCell>>)

data class QuickAddWidgetState(val title: String, val hint: String)
