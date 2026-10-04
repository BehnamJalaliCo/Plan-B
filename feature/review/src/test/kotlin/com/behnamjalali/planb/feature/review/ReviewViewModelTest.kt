package com.behnamjalali.planb.feature.review

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.data.repository.ReviewRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.model.WeeklyReview
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReviewViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph

    /** Lists the open overdue tasks as "missed"; the rest of the review is empty. */
    private inner class MissedTasksReview : ReviewRepository {
        override suspend fun weeklyReview(weekStart: LocalDate): WeeklyReview {
            val today = graph.time.today()
            val open = graph.tasks.observeTasks(TaskFilter(view = TaskView.TODAY, today = today)).first()
            return WeeklyReview(
                weekStart = weekStart, weekEnd = weekStart.plusDays(6), completedPerDay = List(7) { 0 },
                missedTasks = open.filter { it.isOverdue(today) }, habits = emptyList(), focusMinutes = 0, projects = emptyList(),
                notesCreated = 0, goals = emptyList(), nextWeekPriorities = emptyList(),
            )
        }
    }

    @Before
    fun setUp() {
        graph = TestDataGraph()
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

    @Test
    fun checkingAMissedTask_completesIt_andUpdatesTheReview() = runBlocking<Unit> {
        val id = graph.tasks.save(Task(title = "Late", dueDate = graph.time.today().minusDays(2)))
        val vm = main.track(ReviewViewModel(SavedStateHandle(), MissedTasksReview(), graph.tasks, graph.settings, graph.time))
        vm.refresh()
        vm.state.awaitItem { it is ReviewUiState.Ready && it.review.missedTasks.map { t -> t.id } == listOf(id) }

        vm.setTaskCompleted(id, true)
        vm.state.awaitItem { it is ReviewUiState.Ready && it.review.missedTasks.isEmpty() }
        assertThat(graph.tasks.getTask(id)!!.isCompleted).isTrue()
    }
}
