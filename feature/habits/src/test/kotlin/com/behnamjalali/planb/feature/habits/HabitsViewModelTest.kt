package com.behnamjalali.planb.feature.habits

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.HabitSchedule
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HabitsViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph
    private val today get() = graph.time.today()

    @Before
    fun setUp() {
        graph = TestDataGraph()
    }

    @After
    fun tearDown() = graph.close()

    private fun habitsViewModel() = HabitsViewModel(SavedStateHandle(), graph.habits, graph.settings, graph.time)

    private fun detailViewModel(id: Long) =
        HabitDetailViewModel(SavedStateHandle(mapOf("habitId" to id)), graph.habits, graph.settings, graph.time)

    @Test
    fun list_showsActiveHabitsWithTodayAmountAndStreak() = runBlocking<Unit> {
        val water = graph.habits.save(Habit(title = "Water", startDate = today.minusDays(10)))
        graph.habits.checkIn(water, today.minusDays(2))
        graph.habits.checkIn(water, today.minusDays(1))
        graph.habits.save(Habit(title = "Old", startDate = today.minusDays(10), archived = true))

        val state = habitsViewModel().uiState.awaitItem { !it.loading && it.habits.isNotEmpty() }
        val row = state.habits.single()
        assertThat(row.habit.title).isEqualTo("Water")
        assertThat(row.today).isEqualTo(today)
        assertThat(row.todayAmount).isEqualTo(0)
        assertThat(row.scheduledToday).isTrue()
        // Today is still open, so the streak counts the two previous days.
        assertThat(row.streak.count).isEqualTo(2)
    }

    @Test
    fun checkIn_incrementsTodayAmountAndStreak() = runBlocking<Unit> {
        val id = graph.habits.save(Habit(title = "Pushups", target = 2, startDate = today.minusDays(10)))
        graph.habits.checkIn(id, today.minusDays(1), 2)
        val vm = habitsViewModel()
        main.keepCollecting(vm.uiState)

        val initial = vm.uiState.awaitItem { it.habits.size == 1 }.habits.single()
        assertThat(initial.streak.count).isEqualTo(1)

        vm.checkIn(initial)
        val partial = vm.uiState.awaitItem { it.habits.single().todayAmount == 1 }.habits.single()
        assertThat(partial.streak.count).isEqualTo(1)

        vm.checkIn(partial)
        val done = vm.uiState.awaitItem { it.habits.single().todayAmount == 2 }.habits.single()
        assertThat(done.streak.count).isEqualTo(2)
        assertThat(graph.habits.amountOn(id, today)).isEqualTo(2)
    }

    @Test
    fun checkIn_onCompletedHabit_undoesTodaysCheckIns() = runBlocking<Unit> {
        val id = graph.habits.save(Habit(title = "Water", target = 3, startDate = today.minusDays(1)))
        graph.habits.checkIn(id, today, 3)
        val vm = habitsViewModel()
        main.keepCollecting(vm.uiState)

        val row = vm.uiState.awaitItem { it.habits.singleOrNull()?.todayAmount == 3 }.habits.single()
        vm.checkIn(row)
        val after = vm.uiState.awaitItem { it.habits.single().todayAmount == 0 }.habits.single()
        assertThat(after.streak.count).isEqualTo(0)
        assertThat(graph.habits.amountOn(id, today)).isEqualTo(0)
    }

    @Test
    fun toggleArchived_switchesToArchivedHabits() = runBlocking<Unit> {
        graph.habits.save(Habit(title = "Active", startDate = today))
        graph.habits.save(Habit(title = "Archived", startDate = today, archived = true))
        val vm = habitsViewModel()
        main.keepCollecting(vm.uiState)
        vm.uiState.awaitItem { it.habits.map { h -> h.habit.title } == listOf("Active") }

        vm.toggleArchived()
        val archived = vm.uiState.awaitItem { it.showArchived && !it.loading }
        assertThat(archived.habits.map { it.habit.title }).containsExactly("Archived")
    }

    @Test
    fun detail_adjust_updatesAmountStreakAndBestStreak() = runBlocking<Unit> {
        val id = graph.habits.save(
            Habit(title = "Run", schedule = HabitSchedule.Daily, startDate = today.minusDays(5)),
        )
        graph.habits.checkIn(id, today.minusDays(2))
        graph.habits.checkIn(id, today.minusDays(1))
        val vm = detailViewModel(id)
        assertThat(vm.habitId).isEqualTo(id)
        main.keepCollecting(vm.uiState)

        val before = vm.uiState.awaitItem { !it.loading }
        assertThat(before.missing).isFalse()
        assertThat(before.today).isEqualTo(today)
        assertThat(before.streak.count).isEqualTo(2)

        vm.adjust(1)
        val after = vm.uiState.awaitItem { it.item?.amounts?.get(today) == 1 }
        assertThat(after.streak.count).isEqualTo(3)
        assertThat(after.bestStreak).isEqualTo(3)

        vm.adjust(-1)
        val undone = vm.uiState.awaitItem { it.item != null && it.item!!.amounts[today] == null }
        assertThat(undone.streak.count).isEqualTo(2)
    }

    @Test
    fun detail_delete_emitsDeletedAndReportsMissing() = runBlocking<Unit> {
        val id = graph.habits.save(Habit(title = "Temp", startDate = today))
        val vm = detailViewModel(id)
        main.keepCollecting(vm.uiState)
        vm.uiState.awaitItem { it.item != null }

        val deleted = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { vm.events.first() } }
        vm.delete()
        assertThat(deleted.await()).isEqualTo(HabitEvent.Deleted)
        assertThat(vm.uiState.awaitItem { it.missing }.item).isNull()
    }
}
