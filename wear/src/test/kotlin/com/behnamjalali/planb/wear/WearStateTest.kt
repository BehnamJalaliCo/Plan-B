package com.behnamjalali.planb.wear

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WearStateTest {
    private val ready = WearState.Ready(
        tasks = listOf(WearItem(1, "Buy milk"), WearItem(2, "Call Sara")),
        habits = listOf(WearItem(5, "Read", done = false)),
        tasksLabel = "Today",
        habitsLabel = "Habits",
        emptyLabel = "Nothing left",
        rtl = false,
    )

    @Test
    fun completingATaskRemovesItOptimistically() {
        assertThat(ready.completeTask(1).tasks.map { it.id }).containsExactly(2L)
    }

    @Test
    fun habitToggleFlipsOnlyThatHabit() {
        val toggled = ready.toggleHabit(5)
        assertThat(toggled.habits.single().done).isTrue()
        assertThat(toggled.toggleHabit(5).habits.single().done).isFalse()
        assertThat(ready.toggleHabit(99)).isEqualTo(ready)
    }
}
