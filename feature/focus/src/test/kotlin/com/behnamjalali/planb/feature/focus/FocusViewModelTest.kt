package com.behnamjalali.planb.feature.focus

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.model.FocusStatus
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.google.common.truth.Truth.assertThat
import java.time.Duration
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
class FocusViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph
    private lateinit var viewModel: FocusViewModel
    private val time get() = graph.time
    private val reminders get() = graph.reminders

    @Before
    fun setUp() {
        graph = TestDataGraph()
        viewModel = FocusViewModel(SavedStateHandle(), graph.focus, graph.tasks, graph.settings, graph.reminders, graph.time)
        main.keepCollecting(viewModel.uiState)
    }

    @After
    fun tearDown() = graph.close()

    private suspend fun startSession(minutes: Int) {
        viewModel.selectMinutes(minutes)
        viewModel.uiState.awaitItem { !it.loading && it.selectedMinutes == minutes }
        viewModel.start()
        viewModel.uiState.awaitItem { it.active?.status == FocusStatus.RUNNING }
    }

    @Test
    fun defaults_comeFromSettings() = runBlocking<Unit> {
        val state = viewModel.uiState.awaitItem { !it.loading }
        assertThat(state.pomodoroMinutes).isEqualTo(25)
        assertThat(state.selectedMinutes).isEqualTo(25)
        assertThat(state.active).isNull()
        assertThat(state.history).isEmpty()
    }

    @Test
    fun start_createsRunningSessionAndSchedulesEndAlarm() = runBlocking<Unit> {
        val startedAt = time.now()
        startSession(30)

        val active = viewModel.uiState.value.active!!
        assertThat(active.status).isEqualTo(FocusStatus.RUNNING)
        assertThat(active.plannedDurationMillis).isEqualTo(Duration.ofMinutes(30).toMillis())
        assertThat(active.startedAt).isEqualTo(startedAt)
        assertThat(active.remainingMillis(viewModel.now())).isEqualTo(Duration.ofMinutes(30).toMillis())
        assertThat(reminders.focusEnd).isEqualTo(startedAt.plus(Duration.ofMinutes(30)))
    }

    @Test
    fun advancingClock_reducesRemainingTime() = runBlocking<Unit> {
        startSession(25)
        time.advance(Duration.ofMinutes(10))
        val active = viewModel.uiState.value.active!!
        assertThat(active.remainingMillis(viewModel.now())).isEqualTo(Duration.ofMinutes(15).toMillis())
        assertThat(active.elapsedMillis(viewModel.now())).isEqualTo(Duration.ofMinutes(10).toMillis())
    }

    @Test
    fun pauseAndResume_freezeTimeAndRescheduleAlarm() = runBlocking<Unit> {
        startSession(25)
        time.advance(Duration.ofMinutes(10))

        viewModel.pause()
        val paused = viewModel.uiState.awaitItem { it.active?.status == FocusStatus.PAUSED }.active!!
        assertThat(reminders.focusEnd).isNull()
        assertThat(reminders.focusCalls.last()).isEqualTo("cancel")

        // Time spent paused does not count.
        time.advance(Duration.ofMinutes(7))
        assertThat(paused.remainingMillis(viewModel.now())).isEqualTo(Duration.ofMinutes(15).toMillis())

        val resumedAt = time.now()
        viewModel.resume()
        val resumed = viewModel.uiState.awaitItem { it.active?.status == FocusStatus.RUNNING }.active!!
        assertThat(resumed.remainingMillis(viewModel.now())).isEqualTo(Duration.ofMinutes(15).toMillis())
        assertThat(reminders.focusEnd).isEqualTo(resumedAt.plus(Duration.ofMinutes(15)))

        time.advance(Duration.ofMinutes(5))
        assertThat(resumed.remainingMillis(viewModel.now())).isEqualTo(Duration.ofMinutes(10).toMillis())
    }

    @Test
    fun finish_movesSessionToHistoryAndCancelsAlarm() = runBlocking<Unit> {
        startSession(25)
        time.advance(Duration.ofMinutes(12))

        viewModel.finish()
        val state = viewModel.uiState.awaitItem { it.active == null && it.history.isNotEmpty() && it.focusedTodayMinutes > 0 }
        val done = state.history.single()
        assertThat(done.status).isEqualTo(FocusStatus.COMPLETED)
        assertThat(done.actualDurationMillis).isEqualTo(Duration.ofMinutes(12).toMillis())
        assertThat(state.focusedTodayMinutes).isEqualTo(12)
        assertThat(reminders.focusEnd).isNull()
        assertThat(reminders.focusCalls.last()).isEqualTo("cancel")
    }

    @Test
    fun finish_withLinkedTask_recordsFocusedMinutesOnTask() = runBlocking<Unit> {
        val taskId = graph.tasks.save(Task(title = "Write report", actualMinutes = 5))
        viewModel.linkTask(taskId)
        viewModel.uiState.awaitItem { it.linkedTaskId == taskId && it.linkedTask != null }
        startSession(20)
        assertThat(viewModel.uiState.value.active!!.linkedTaskId).isEqualTo(taskId)

        time.advance(Duration.ofMinutes(8))
        viewModel.finish()
        viewModel.uiState.awaitItem { it.active == null && it.history.isNotEmpty() }
        withTimeout(5_000) { graph.tasks.observeTask(taskId).first { it?.actualMinutes == 13 } }
    }

    @Test
    fun onElapsed_completesSessionWhenPlannedTimeIsUp() = runBlocking<Unit> {
        startSession(5)
        time.advance(Duration.ofMinutes(3))
        viewModel.onElapsed()
        // Not elapsed yet: stays running.
        assertThat(viewModel.uiState.awaitItem { it.active != null }.active!!.status).isEqualTo(FocusStatus.RUNNING)

        time.advance(Duration.ofMinutes(4))
        val completed = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { viewModel.messages.first() } }
        viewModel.onElapsed()
        assertThat(completed.await()).isEqualTo(FocusMessage.Completed)
        val state = viewModel.uiState.awaitItem { it.active == null && it.history.isNotEmpty() }
        // Elapsed time is capped at the planned duration.
        assertThat(state.history.single().actualDurationMillis).isEqualTo(Duration.ofMinutes(5).toMillis())
        assertThat(reminders.focusEnd).isNull()
    }

    @Test
    fun cancel_recordsCancelledSessionWithoutFocusedMinutes() = runBlocking<Unit> {
        startSession(25)
        time.advance(Duration.ofMinutes(4))
        viewModel.cancel()
        val state = viewModel.uiState.awaitItem { it.active == null && it.history.isNotEmpty() }
        assertThat(state.history.single().status).isEqualTo(FocusStatus.CANCELLED)
        assertThat(state.focusedTodayMinutes).isEqualTo(0)
        assertThat(reminders.focusEnd).isNull()
    }
}
