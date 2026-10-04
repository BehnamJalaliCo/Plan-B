package com.behnamjalali.planb.feature.calendar

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.datetime.CalendarEngines
import com.behnamjalali.planb.core.model.CalendarView
import com.behnamjalali.planb.core.model.RecurrenceFrequency
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.behnamjalali.planb.core.model.Task
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
class CalendarViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph

    @Before
    fun setUp() {
        graph = TestDataGraph()
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

    private fun viewModel(): CalendarViewModel =
        main.track(
            CalendarViewModel(
                SavedStateHandle(mapOf("calendar_view" to CalendarView.MONTH.name)),
                graph.events, graph.tasks, graph.settings, graph.time,
            ),
        ).also { main.keepCollecting(it.uiState) }

    @Test
    fun twoQuickNextTaps_moveTwoMonths() = runBlocking<Unit> {
        val vm = viewModel()
        val initial = vm.uiState.awaitItem { !it.loading }
        val engine = CalendarEngines.of(initial.calendarSystem)
        val expected = engine.firstDayOfMonth(engine.monthOf(initial.selected).plus(2))

        vm.page(1)
        vm.page(1)
        vm.uiState.awaitItem { !it.loading && it.selected == expected }
    }

    @Test
    fun completingATask_offersUndo_thatRestoresTheSeries() = runBlocking<Unit> {
        val today = graph.time.today()
        val id = graph.tasks.save(Task(title = "Water plants", dueDate = today, recurrence = RecurrenceRule(RecurrenceFrequency.DAILY)))
        val vm = viewModel()
        vm.uiState.awaitItem { !it.loading && it.itemsOn(today).tasks.any { t -> t.id == id } }

        val message = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { vm.messages.first() } }
        vm.setTaskCompleted(id, true)
        val completed = message.await() as CalendarMessage.Completed
        assertThat(completed.taskId).isEqualTo(id)
        assertThat(completed.nextOccurrenceId).isNotNull()

        vm.undoComplete(completed.taskId)
        withTimeout(20_000) { graph.tasks.observeTask(completed.nextOccurrenceId!!).first { it == null } }
        val restored = withTimeout(20_000) { graph.tasks.observeTask(id).first { it != null && !it.isCompleted && it.recurrence != null } }
        assertThat(restored!!.recurrence).isEqualTo(RecurrenceRule(RecurrenceFrequency.DAILY))
    }
}
