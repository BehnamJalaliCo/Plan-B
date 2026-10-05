package com.behnamjalali.planb.feature.focus

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.model.AmbientSound
import com.behnamjalali.planb.core.model.FocusStatus
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalTime
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
    private val controls = FakeFocusProControls()
    private val time get() = graph.time
    private val reminders get() = graph.reminders

    @Before
    fun setUp() {
        graph = TestDataGraph()
        viewModel = main.track(FocusViewModel(SavedStateHandle(), graph.focus, graph.tasks, graph.settings, graph.reminders, graph.time, controls))
        main.keepCollecting(viewModel.uiState)
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

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
        withTimeout(20_000) { graph.tasks.observeTask(taskId).first { it?.actualMinutes == 13 } }
    }

    @Test
    fun onElapsed_completesSessionWhenPlannedTimeIsUp() = runBlocking<Unit> {
        startSession(5)
        time.advance(Duration.ofMinutes(3))
        // Wait for this check to finish before moving the clock past the end.
        viewModel.onElapsed().join()
        // Not elapsed yet: stays running.
        assertThat(viewModel.uiState.awaitItem { it.active != null }.active!!.status).isEqualTo(FocusStatus.RUNNING)

        time.advance(Duration.ofMinutes(4))
        val completed = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { viewModel.messages.first() } }
        viewModel.onElapsed()
        assertThat(completed.await()).isEqualTo(FocusMessage.Completed)
        val state = viewModel.uiState.awaitItem { it.active == null && it.history.isNotEmpty() }
        // Elapsed time is capped at the planned duration.
        assertThat(state.history.single().actualDurationMillis).isEqualTo(Duration.ofMinutes(5).toMillis())
        assertThat(reminders.focusEnd).isNull()
    }

    @Test
    fun doubleTapStart_startsOneSession_withoutAJunkCancelledOne() = runBlocking<Unit> {
        viewModel.uiState.awaitItem { !it.loading }
        val first = viewModel.start()
        val second = viewModel.start()
        first.join()
        second.join()
        // A later tap while the session runs is ignored as well.
        viewModel.start().join()

        val state = viewModel.uiState.awaitItem { it.active?.status == FocusStatus.RUNNING }
        assertThat(graph.focus.observeHistory(10).first()).isEmpty()
        assertThat(state.active!!.startedAt).isEqualTo(time.now())
    }

    @Test
    fun focusedToday_startsAgainAfterMidnight() = runBlocking<Unit> {
        val day = time.today()
        time.setLocal(day, LocalTime.of(23, 30))
        graph.focus.start(Duration.ofMinutes(10).toMillis(), null)
        time.advance(Duration.ofMinutes(10))
        graph.focus.finish()

        // A screen opened one second before midnight and left open.
        time.setLocal(day, LocalTime.of(23, 59, 59))
        val lateViewModel = main.track(FocusViewModel(SavedStateHandle(), graph.focus, graph.tasks, graph.settings, graph.reminders, graph.time, controls))
        main.keepCollecting(lateViewModel.uiState)
        lateViewModel.uiState.awaitItem { !it.loading && it.focusedTodayMinutes == 10 }

        time.setLocal(day.plusDays(1), LocalTime.of(0, 0, 30))
        lateViewModel.uiState.awaitItem { it.focusedTodayMinutes == 0 }
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

    // region Focus Pro (#26)
    @Test
    fun proStart_usesTheDefaultSoundAndStrictMode_freeStartDoesNot() = runBlocking<Unit> {
        graph.settings.update { it.copy(focusPro = it.focusPro.copy(sound = AmbientSound.RAIN, strict = true)) }
        viewModel.uiState.awaitItem { it.focusPro.sound == AmbientSound.RAIN }
        viewModel.start(pro = false)
        val free = viewModel.uiState.awaitItem { it.active != null }.active!!
        assertThat(free.soundId).isNull()
        assertThat(free.strict).isFalse()
        viewModel.cancel()
        viewModel.uiState.awaitItem { it.active == null }
        viewModel.start(pro = true)
        val pro = viewModel.uiState.awaitItem { it.active != null }
        assertThat(pro.active!!.soundId).isEqualTo("rain")
        assertThat(pro.active!!.strict).isTrue()
        assertThat(pro.sound).isEqualTo(AmbientSound.RAIN)
        // Effects ran for the new session (sound service and Do Not Disturb).
        assertThat(graph.focusEffects.changes.last()!!.soundId).isEqualTo("rain")
    }

    @Test
    fun setSound_previewsWhenIdle_andChangesTheRunningSession() = runBlocking<Unit> {
        viewModel.uiState.awaitItem { !it.loading }
        viewModel.setSound(AmbientSound.OCEAN)
        viewModel.uiState.awaitItem { it.focusPro.sound == AmbientSound.OCEAN }
        eventually { controls.previews.lastOrNull() == AmbientSound.OCEAN }
        viewModel.start(pro = true)
        viewModel.uiState.awaitItem { it.active?.soundId == "ocean" }
        // Starting stops any preview.
        eventually { controls.previews.size >= 2 && controls.previews.last() == null }
        viewModel.setSound(null)
        viewModel.uiState.awaitItem { it.active != null && it.active!!.soundId == null && it.sound == null }
        viewModel.setStrict(true)
        viewModel.uiState.awaitItem { it.active?.strict == true && it.focusPro.strict }
        viewModel.setVolume(140)
        assertThat(viewModel.uiState.awaitItem { it.focusPro.volume != 60 }.focusPro.volume).isEqualTo(100)
    }

    @Test
    fun onResume_rechecksDoNotDisturbAccess_andAppliesIt() = runBlocking<Unit> {
        assertThat(viewModel.uiState.awaitItem { !it.loading }.dndAccess).isFalse()
        controls.access = true
        viewModel.onResume()
        viewModel.uiState.awaitItem { it.dndAccess }
        eventually { controls.refreshes > 0 }
    }

    private suspend fun eventually(condition: () -> Boolean) = withTimeout(10_000) {
        while (!condition()) kotlinx.coroutines.delay(20)
    }

    @Test
    fun cycle_suggestsALongBreakAfterEveryFourthSessionOfTheDay() = runBlocking<Unit> {
        graph.settings.update { it.copy(focusPro = it.focusPro.copy(dailyGoalMinutes = 60, longBreakEvery = 2, longBreakMinutes = 20)) }
        repeat(2) {
            graph.focus.start(Duration.ofMinutes(25).toMillis(), null)
            time.advance(Duration.ofMinutes(25))
            graph.focus.finish()
        }
        val state = viewModel.uiState.awaitItem { it.completedToday == 2 && it.focusPro.longBreakEvery == 2 && it.focusedTodayMinutes == 50 }
        assertThat(state.cycle.longBreak).isTrue()
        assertThat(state.cycle.breakMinutes).isEqualTo(20)
        assertThat(state.focusedTodayMinutes).isEqualTo(50)
        viewModel.setDailyGoal(90)
        viewModel.setLongBreak(4, 15)
        val updated = viewModel.uiState.awaitItem { it.focusPro.dailyGoalMinutes == 90 && it.focusPro.longBreakEvery == 4 }
        assertThat(updated.cycle.longBreak).isFalse()
        assertThat(updated.cycle.positionInCycle).isEqualTo(3)
    }
    // endregion
}

/** Records what the Focus screen asks of Focus Pro's platform side. */
class FakeFocusProControls : com.behnamjalali.planb.core.focus.FocusProControls {
    @Volatile var access = false
    @Volatile var refreshes = 0
    val previews: MutableList<AmbientSound?> = java.util.Collections.synchronizedList(mutableListOf())
    override fun hasDndAccess() = access
    override fun dndAccessIntent() = android.content.Intent("test.DND")
    override suspend fun refresh() {
        refreshes++
    }
    override fun preview(sound: AmbientSound?, volume: Int) {
        previews += sound
    }
}
