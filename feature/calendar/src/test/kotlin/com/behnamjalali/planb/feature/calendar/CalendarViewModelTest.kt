package com.behnamjalali.planb.feature.calendar

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.calendarsync.DeviceCalendarItem
import com.behnamjalali.planb.core.calendarsync.DeviceCalendarSource
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

    private fun viewModel(
        view: String = CalendarView.MONTH.name,
        device: DeviceCalendarSource = DeviceCalendarSource.None,
    ): CalendarViewModel =
        main.track(
            CalendarViewModel(
                SavedStateHandle(mapOf("calendar_view" to view)),
                graph.events, graph.tasks, graph.settings, graph.time, device,
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

    @Test
    fun timeBlocking_schedulesMovesAndClearsABlock() = runBlocking<Unit> {
        val today = graph.time.today()
        val zone = graph.time.zone()
        val undated = graph.tasks.save(Task(title = "Write the report", estimatedMinutes = 45))
        val vm = viewModel(view = CalendarMode.DAY.name)
        vm.uiState.awaitItem { !it.loading }

        // A task without a date is planned for the day it is dropped on, for its estimate.
        vm.scheduleTask(undated, today, 9 * 60 + 15, TimeBlocks.defaultDuration(graph.tasks.getTask(undated)!!))
        val state = vm.uiState.awaitItem { s -> s.itemsOn(today).tasks.any { it.id == undated && it.scheduledStart != null } }
        val task = state.itemsOn(today).tasks.single { it.id == undated }
        assertThat(task.dueDate).isEqualTo(today)
        assertThat(task.scheduledStart).isEqualTo(today.atTime(9, 15).atZone(zone).toInstant())
        assertThat(task.scheduledEnd).isEqualTo(today.atTime(10, 0).atZone(zone).toInstant())

        // Moved to tomorrow (week view): it shows on the day of its block, keeping its planned date.
        vm.setMode(CalendarMode.WEEK)
        vm.scheduleTask(undated, today.plusDays(1), 23 * 60 + 45, 45)
        val moved = vm.uiState.awaitItem { s -> s.itemsOn(today.plusDays(1)).tasks.any { it.id == undated } }
        val block = moved.itemsOn(today.plusDays(1)).tasks.single { it.id == undated }
        // Clamped so the block ends within the day.
        assertThat(block.scheduledStart).isEqualTo(today.plusDays(1).atTime(23, 15).atZone(zone).toInstant())
        assertThat(block.dueDate).isEqualTo(today)

        vm.unscheduleTask(undated)
        withTimeout(20_000) { graph.tasks.observeTask(undated).first { it != null && it.scheduledStart == null && it.scheduledEnd == null } }
    }

    @Test
    fun deviceEvents_andIranDecorations_reachTheState() = runBlocking<Unit> {
        val today = graph.time.today()
        val item = DeviceCalendarItem(7, 1, "Standup", today, java.time.LocalTime.of(9, 0), java.time.LocalTime.of(9, 15), false, 0, "Work")
        val device = object : DeviceCalendarSource {
            override fun observeItems(from: java.time.LocalDate, to: java.time.LocalDate) = kotlinx.coroutines.flow.flowOf(listOf(item).filter { it.date in from..to })
            override suspend fun import(item: DeviceCalendarItem): Long? = null
        }
        val vm = viewModel(device = device)
        val state = vm.uiState.awaitItem { !it.loading }
        assertThat(state.itemsOn(today).device).containsExactly(item)
        // 12 Mehr 1405 = 22 Rabi' al-Thani 1448.
        assertThat(state.hijri).isEqualTo(com.behnamjalali.planb.core.datetime.iran.HijriDate(1448, 4, 22))
        // The month grid of Mehr 1405 has Fridays off; Aban's 22nd (Fatima) is a holiday.
        assertThat(state.isOffDay(java.time.LocalDate.of(2026, 10, 9))).isTrue()
        assertThat(state.isOffDay(today)).isFalse()
        vm.page(1)
        val aban = vm.uiState.awaitItem { !it.loading && it.month.month == 8 }
        assertThat(aban.isOffDay(com.behnamjalali.planb.core.datetime.JalaliEngine.toLocalDate(1405, 8, 22))).isTrue()
        assertThat(aban.occasionsOn(com.behnamjalali.planb.core.datetime.JalaliEngine.toLocalDate(1405, 8, 22)).map { it.occasion })
            .contains(com.behnamjalali.planb.core.datetime.iran.Occasion.FATIMA_MARTYRDOM)
        // Turning holidays off in Settings removes the red days.
        graph.settings.update { it.copy(calendarDecorations = it.calendarDecorations.copy(holidays = false)) }
        val off = vm.uiState.awaitItem { !it.decorations.holidays }
        assertThat(off.isOffDay(com.behnamjalali.planb.core.datetime.JalaliEngine.toLocalDate(1405, 8, 22))).isFalse()
    }
}
