package com.behnamjalali.planb.feature.today

import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TodayViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph
    private lateinit var viewModel: TodayViewModel
    private val today get() = graph.time.today()

    @Before
    fun setUp() {
        graph = TestDataGraph()
        viewModel = main.track(
            TodayViewModel(graph.tasks, graph.events, graph.habits, graph.focus, graph.projects, graph.notes, graph.settings, graph.time),
        )
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

    private suspend fun awaitData(predicate: (TodayData) -> Boolean = { true }): TodayData =
        (viewModel.uiState.awaitItem { it is TodayUiState.Success && predicate(it.data) } as TodayUiState.Success).data

    @Test
    fun emptyDatabase_producesEmptySuccessState() = runBlocking<Unit> {
        val data = awaitData()
        assertThat(data.date).isEqualTo(today)
        assertThat(data.todayTasks).isEmpty()
        assertThat(data.upcoming).isEmpty()
        assertThat(data.events).isEmpty()
        assertThat(data.habits).isEmpty()
        assertThat(data.activeFocus).isNull()
        assertThat(data.progress).isEqualTo(0f)
        // FakeTimeProvider defaults to 12:00 Tehran time.
        assertThat(data.greeting).isEqualTo(Greeting.AFTERNOON)
    }

    @Test
    fun seededData_isGroupedIntoTodayUpcomingEventsAndHabits() = runBlocking<Unit> {
        val tasks = graph.tasks
        tasks.save(Task(title = "Overdue", dueDate = today.minusDays(2)))
        tasks.save(Task(title = "Today timed", dueDate = today, dueTime = LocalTime.of(9, 0)))
        tasks.save(Task(title = "Today untimed", dueDate = today))
        tasks.save(Task(title = "Upcoming", dueDate = today.plusDays(2)))
        tasks.save(Task(title = "Far future", dueDate = today.plusDays(10)))
        tasks.save(Task(title = "Inbox no date"))
        val done = tasks.save(Task(title = "Done today", dueDate = today))
        tasks.setCompleted(done, true)

        graph.events.save(CalendarEvent(title = "Standup", date = today, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(10, 15)))
        graph.events.save(CalendarEvent(title = "Tomorrow meeting", date = today.plusDays(1), startTime = LocalTime.of(10, 0)))

        val water = graph.habits.save(Habit(title = "Water", startDate = today.minusDays(5)))
        graph.habits.checkIn(water, today.minusDays(1))
        graph.habits.checkIn(water, today)
        graph.habits.save(Habit(title = "Read", target = 2, startDate = today.minusDays(5)))

        val data = awaitData { it.habits.size == 2 && it.completedToday == 1 && it.events.isNotEmpty() && it.upcoming.isNotEmpty() }

        assertThat(data.todayTasks.map { it.title }).containsExactly("Overdue", "Today timed", "Today untimed").inOrder()
        assertThat(data.overdueCount).isEqualTo(1)
        assertThat(data.dueTodayCount).isEqualTo(2)
        assertThat(data.completedToday).isEqualTo(1)
        assertThat(data.totalForProgress).isEqualTo(4)
        assertThat(data.progress).isEqualTo(0.25f)
        assertThat(data.upcoming.map { it.title }).containsExactly("Upcoming")
        assertThat(data.events.map { it.event.title }).containsExactly("Standup")

        val waterRow = data.habits.single { it.habit.habit.title == "Water" }
        assertThat(waterRow.amount).isEqualTo(1)
        assertThat(waterRow.streak.count).isEqualTo(2)
        assertThat(data.habits.single { it.habit.habit.title == "Read" }.amount).isEqualTo(0)
        assertThat(data.habitsDone).isEqualTo(1)

        val timeline = data.timeline
        assertThat(timeline).hasSize(2)
        assertThat((timeline[0] as TimelineItem.TaskItem).task.title).isEqualTo("Today timed")
        assertThat((timeline[1] as TimelineItem.EventItem).occurrence.event.title).isEqualTo("Standup")
    }

    @Test
    fun completingTask_removesItFromTodayAndRaisesProgress() = runBlocking<Unit> {
        val a = graph.tasks.save(Task(title = "A", dueDate = today))
        graph.tasks.save(Task(title = "B", dueDate = today))
        main.keepCollecting(viewModel.uiState)
        awaitData { it.todayTasks.size == 2 }

        viewModel.setTaskCompleted(a, true)
        // Counts and lists come from separate flows; wait until both reflect the change.
        val after = awaitData { it.completedToday == 1 && it.todayTasks.size == 1 }
        assertThat(after.todayTasks.map { it.title }).containsExactly("B")
        assertThat(after.progress).isEqualTo(0.5f)
        assertThat(graph.tasks.getTask(a)!!.isCompleted).isTrue()

        viewModel.setTaskCompleted(a, false)
        val reopened = awaitData { it.completedToday == 0 && it.todayTasks.size == 2 }
        assertThat(reopened.todayTasks.map { it.title }).containsExactly("A", "B")
    }

    @Test
    fun checkInHabit_togglesTodayAmount() = runBlocking<Unit> {
        val id = graph.habits.save(Habit(title = "Walk", startDate = today.minusDays(3)))
        main.keepCollecting(viewModel.uiState)
        awaitData { it.habits.size == 1 }

        viewModel.checkInHabit(id, done = false)
        val checked = awaitData { it.habits.single().amount == 1 }
        assertThat(checked.habitsDone).isEqualTo(1)
        assertThat(checked.habits.single().streak.count).isEqualTo(1)

        viewModel.checkInHabit(id, done = true)
        val undone = awaitData { it.habits.single().amount == 0 }
        assertThat(undone.habitsDone).isEqualTo(0)
    }

    @Test
    fun activeFocusSession_isExposed() = runBlocking<Unit> {
        graph.focus.start(25 * 60_000L, null)
        val data = awaitData { it.activeFocus != null }
        assertThat(data.activeFocus!!.plannedDurationMillis).isEqualTo(25 * 60_000L)
    }
}
