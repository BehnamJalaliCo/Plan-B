package com.behnamjalali.planb.wear

/** Paths and keys of the phone ↔ watch protocol; must match the phone app's `WearProtocol`. */
object WearKeys {
    const val PATH_TODAY = "/planb/today"
    const val PATH_COMPLETE_TASK = "/planb/complete-task"
    const val PATH_CHECK_HABIT = "/planb/check-habit"
    const val PATH_REFRESH = "/planb/refresh"

    const val PRO = "pro"
    const val RTL = "rtl"
    const val TASKS = "tasks"
    const val HABITS = "habits"
    const val ID = "id"
    const val TITLE = "title"
    const val DONE = "done"
    const val LABEL_TASKS = "label_tasks"
    const val LABEL_HABITS = "label_habits"
    const val LABEL_EMPTY = "label_empty"
    const val LABEL_LOCKED = "label_locked"
}

data class WearItem(val id: Long, val title: String, val done: Boolean = false)

/** What the watch shows; labels come from the phone so they follow the app's language. */
sealed interface WearState {
    /** Nothing received from the phone yet (or no phone app / Play services). */
    data object Waiting : WearState

    data class Locked(val message: String, val rtl: Boolean) : WearState

    data class Ready(
        val tasks: List<WearItem>,
        val habits: List<WearItem>,
        val tasksLabel: String,
        val habitsLabel: String,
        val emptyLabel: String,
        val rtl: Boolean,
    ) : WearState {
        /** Optimistic update while the phone applies a completion: the task disappears at once. */
        fun completeTask(id: Long): Ready = copy(tasks = tasks.filterNot { it.id == id })

        /** Optimistic habit toggle (the phone's answer replaces it moments later). */
        fun toggleHabit(id: Long): Ready = copy(habits = habits.map { if (it.id == id) it.copy(done = !it.done) else it })
    }
}
