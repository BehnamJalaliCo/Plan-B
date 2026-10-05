package com.behnamjalali.planb.feature.today

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.data.repository.OfflineDayPlanRepository
import com.behnamjalali.planb.core.data.repository.OfflineRitualJournalRepository
import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.RitualState
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.feature.today.plan.DayPlanEvent
import com.behnamjalali.planb.feature.today.plan.DayPlanViewModel
import com.behnamjalali.planb.feature.today.plan.PlanMode
import com.behnamjalali.planb.feature.today.ritual.JournalTexts
import com.behnamjalali.planb.feature.today.ritual.RitualEvent
import com.behnamjalali.planb.feature.today.ritual.RitualStep
import com.behnamjalali.planb.feature.today.ritual.RitualViewModel
import com.behnamjalali.planb.feature.today.ritual.TaskMove
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalTime
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** "Plan my day" / "Replan" (Pro #5) and the rituals (Pro #8) on real repositories. */
@RunWith(RobolectricTestRunner::class)
class SmartDayViewModelsTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph
    private lateinit var plans: OfflineDayPlanRepository
    private lateinit var journal: OfflineRitualJournalRepository
    private val today get() = graph.time.today()

    @Before
    fun setUp() {
        graph = TestDataGraph()
        plans = OfflineDayPlanRepository(graph.db, graph.db.taskDao(), graph.time)
        journal = OfflineRitualJournalRepository(graph.db, graph.notes, graph.time)
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

    private fun localTime(instant: java.time.Instant?) = instant?.atZone(graph.time.zone())?.toLocalTime()

    private suspend fun <T> await(timeoutMs: Long = 10_000, read: suspend () -> T?): T = withTimeout(timeoutMs) {
        var value = read()
        while (value == null) {
            delay(20)
            value = read()
        }
        value
    }

    @Test
    fun planMyDay_previewsAndWritesOnlyAcceptedBlocks_thenReplanMovesMissedOnes() = runBlocking<Unit> {
        // FakeTimeProvider: 12:00 in Tehran. Working hours 09:00–18:00, 10-minute buffers.
        graph.events.save(CalendarEvent(title = "Lunch", date = today, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(14, 0), allDay = false))
        val long = graph.tasks.save(Task(title = "Report", dueDate = today, priority = Priority.HIGH, estimatedMinutes = 60))
        val short = graph.tasks.save(Task(title = "Email", dueDate = today))
        val skipped = graph.tasks.save(Task(title = "Later", dueDate = today))
        val blocked = graph.tasks.save(Task(title = "Waits", dueDate = today))
        graph.planning.setDependencies(blocked, listOf(long))
        graph.tasks.save(Task(title = "Fixed", dueDate = today, dueTime = LocalTime.of(16, 0)))

        val vm = main.track(DayPlanViewModel(graph.tasks, graph.events, plans, graph.settings, graph.time))
        vm.load(PlanMode.PLAN)
        val state = await { vm.state.value.takeIf { !it.loading } }
        val slots = state.proposals.associate { it.task.title to (it.start to it.end) }
        assertThat(slots["Report"]).isEqualTo(LocalTime.of(14, 10) to LocalTime.of(15, 10))
        assertThat(slots["Email"]).isEqualTo(LocalTime.of(12, 0) to LocalTime.of(12, 30))
        assertThat(slots).doesNotContainKey("Fixed")
        assertThat(state.blocked.map { it.title }).containsExactly("Waits")
        // Nothing is written by the preview.
        assertThat(graph.tasks.getTask(long)!!.scheduledStart).isNull()

        vm.toggle(skipped)
        val applied = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(10_000) { vm.events.first() } }
        vm.accept()
        assertThat(applied.await()).isEqualTo(DayPlanEvent.Applied(2))
        assertThat(localTime(graph.tasks.getTask(short)!!.scheduledStart)).isEqualTo(LocalTime.of(12, 0))
        assertThat(graph.tasks.getTask(skipped)!!.scheduledStart).isNull()

        // At 16:00 the 12:00 block is past: Replan moves it, the others stay.
        graph.time.advance(Duration.ofHours(4))
        vm.load(PlanMode.REPLAN)
        val replan = await { vm.state.value.takeIf { !it.loading } }
        assertThat(replan.proposals.map { it.task.title }).containsExactly("Email")
        val moved = replan.proposals.single()
        assertThat(moved.moved).isTrue()
        assertThat(moved.start).isEqualTo(LocalTime.of(16, 40))
    }

    @Test
    fun planMyDay_withNothingToPlan_saysSo() = runBlocking<Unit> {
        val vm = main.track(DayPlanViewModel(graph.tasks, graph.events, plans, graph.settings, graph.time))
        vm.load(PlanMode.PLAN)
        val state = await { vm.state.value.takeIf { !it.loading } }
        assertThat(state.nothingToDo).isTrue()
        assertThat(state.proposals).isEmpty()
    }

    private fun ritual(kind: RitualKind) = main.track(
        RitualViewModel(SavedStateHandle(mapOf("kind" to kind.key)), graph.tasks, graph.events, graph.settings, journal, graph.time),
    )

    @Test
    fun morningRitual_reviewTop3IntentionAndFinish() = runBlocking<Unit> {
        val old = graph.tasks.save(Task(title = "Old", dueDate = today.minusDays(1)))
        val dropped = graph.tasks.save(Task(title = "Drop me", dueDate = today.minusDays(2)))
        val ids = (1..4).map { graph.tasks.save(Task(title = "T$it", dueDate = today)) }
        val vm = ritual(RitualKind.MORNING)
        // The screen's subscription keeps the data flowing.
        val collector = launch { vm.data.collect {} }
        assertThat(vm.steps.first()).isEqualTo(RitualStep.REVIEW)
        val data = await { vm.data.value?.takeIf { it.unfinished.size == 2 } }
        vm.move(data.unfinished.first { it.id == old }, TaskMove.TODAY)
        vm.move(data.unfinished.first { it.id == dropped }, TaskMove.DROP)
        await { vm.data.value?.takeIf { it.unfinished.isEmpty() } }
        assertThat(graph.tasks.getTask(old)!!.dueDate).isEqualTo(today)
        assertThat(graph.tasks.getTask(dropped)!!.archived).isTrue()

        // At most three in the top 3.
        ids.forEach { id ->
            vm.toggleFocus(id)
            delay(100)
        }
        val focus = await { graph.settings.current().rituals.focusFor(today).takeIf { it.size == 3 } }
        assertThat(focus).containsExactly(ids[0], ids[1], ids[2]).inOrder()

        repeat(4) { vm.next() }
        assertThat(vm.steps[vm.step.value]).isEqualTo(RitualStep.INTENTION)
        vm.setText("Calm and focused")
        val finished = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(10_000) { vm.events.first() } }
        vm.finish(JournalTexts("Intention", "Journal", "4 Oct"))
        assertThat(finished.await()).isEqualTo(RitualEvent.Finished)
        assertThat(graph.settings.current().rituals.morningDoneOn).isEqualTo(today)
        val page = graph.notes.getNote(journal.pageOf(today)!!)!!
        assertThat(page.document.plainText()).contains("Calm and focused")
        collector.cancel()
    }

    @Test
    fun eveningRitual_leftoversMoveAndTomorrowsTop3() = runBlocking<Unit> {
        val done = graph.tasks.save(Task(title = "Done", dueDate = today))
        graph.tasks.setCompleted(done, true)
        val left = graph.tasks.save(Task(title = "Left", dueDate = today))
        val nextWeek = graph.tasks.save(Task(title = "Next week", dueDate = today))
        val vm = ritual(RitualKind.EVENING)
        val collector = launch { vm.data.collect {} }
        val data = await { vm.data.value?.takeIf { it.leftovers.size == 2 } }
        assertThat(data.completedToday.map { it.title }).containsExactly("Done")
        vm.move(data.leftovers.first { it.id == left }, TaskMove.TOMORROW)
        vm.move(data.leftovers.first { it.id == nextWeek }, TaskMove.NEXT_WEEK)
        await { vm.data.value?.takeIf { it.leftovers.isEmpty() && it.tomorrow.any { t -> t.id == left } } }
        assertThat(graph.tasks.getTask(left)!!.dueDate).isEqualTo(today.plusDays(1))
        // FakeTimeProvider's today is Sunday 4 Oct 2026; Persian weeks start on Saturday.
        assertThat(graph.tasks.getTask(nextWeek)!!.dueDate).isEqualTo(java.time.LocalDate.of(2026, 10, 10))

        vm.toggleFocus(left)
        await { graph.settings.current().rituals.takeIf { it.focusFor(today.plusDays(1)) == listOf(left) } }
        // Finishing without a reflection writes nothing to the journal.
        val finished = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(10_000) { vm.events.first() } }
        vm.finish(JournalTexts("Reflection", "Journal", "4 Oct"))
        assertThat(finished.await()).isEqualTo(RitualEvent.Finished)
        assertThat(journal.pageOf(today)).isNull()
        assertThat(graph.settings.current().rituals).isEqualTo(
            RitualState(focusDate = today.plusDays(1), focusTaskIds = listOf(left), eveningDoneOn = today),
        )
        collector.cancel()
    }
}
