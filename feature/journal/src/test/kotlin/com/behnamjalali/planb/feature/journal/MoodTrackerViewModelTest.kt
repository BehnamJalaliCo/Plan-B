package com.behnamjalali.planb.feature.journal

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.data.repository.OfflineJournalRepository
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.HealthMetric
import com.behnamjalali.planb.core.model.MoodFactor
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The mood and energy tracker (Plan-B Pro #30) on the real data layer. */
@RunWith(RobolectricTestRunner::class)
class MoodTrackerViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph
    private val today get() = graph.time.today()

    @Before
    fun setUp() {
        graph = TestDataGraph()
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        main.track(MoodTrackerViewModel(handle, graph.moods, graph.habits, graph.healthSync, graph.time)).also { main.keepCollecting(it.state) }

    @Test
    fun openedForACheckIn_showsTheSheetOnce_withTheChosenMood() = runBlocking<Unit> {
        val handle = SavedStateHandle(mapOf("checkIn" to true, "mood" to 4))
        val vm = viewModel(handle)
        assertThat(vm.state.awaitItem { !it.loading }.draft).isEqualTo(MoodDraft(mood = 4))
        vm.dismissDraft()
        vm.state.awaitItem { it.draft == null }
        // Recreated with the same saved state (rotation, process death): not opened again.
        main.clearViewModels()
        assertThat(viewModel(handle).state.awaitItem { !it.loading }.draft).isNull()
    }

    @Test
    fun checkIns_severalADay_editAndDeleteWithUndo() = runBlocking<Unit> {
        val vm = viewModel()
        val events = java.util.Collections.synchronizedList(mutableListOf<MoodEvent>())
        main.scope.launch { vm.events.collect { events += it } }
        vm.state.awaitItem { !it.loading }
        vm.openCheckIn()
        vm.updateDraft { it.copy(mood = 4, energy = 2, tags = "work, سفر") }
        vm.state.awaitItem { it.draft?.tags == "work, سفر" }
        vm.saveDraft()
        eventually { events.contains(MoodEvent.Saved) }
        graph.time.advance(Duration.ofHours(2))
        vm.openCheckIn(mood = 2)
        vm.state.awaitItem { it.draft?.mood == 2 }
        vm.saveDraft()
        val two = vm.state.awaitItem { it.todayEntries.size == 2 && it.draft == null }
        assertThat(two.todayEntries.first().mood).isEqualTo(2) // newest first
        val first = two.todayEntries.last()
        assertThat(first.tags).containsExactly("work", "سفر")
        vm.edit(first)
        vm.state.awaitItem { it.draft?.editingId == first.id && it.draft?.tags == "work, سفر" }
        vm.updateDraft { it.copy(mood = 5) }
        vm.state.awaitItem { it.draft?.mood == 5 }
        vm.saveDraft()
        val edited = vm.state.awaitItem { s -> s.todayEntries.any { it.id == first.id && it.mood == 5 } }.todayEntries.first { it.id == first.id }
        vm.delete(edited)
        eventually { events.any { it is MoodEvent.Deleted } }
        val event = events.filterIsInstance<MoodEvent.Deleted>().single()
        vm.state.awaitItem { it.todayEntries.size == 1 }
        vm.undoDelete(event.entry)
        val back = vm.state.awaitItem { it.todayEntries.size == 2 }
        assertThat(back.todayEntries.map { it.mood }).containsExactly(2, 5)
    }

    private suspend fun eventually(condition: () -> Boolean) = withTimeout(10_000) {
        while (!condition()) kotlinx.coroutines.delay(20)
    }

    @Test
    fun theJournalsCheckInIsPartOfTheTracker_andStaysTheJournals() = runBlocking<Unit> {
        val journal = OfflineJournalRepository(graph.db, graph.notes, graph.time)
        val page = journal.openPage(today, "Journal", "Today", null, null)
        journal.setPageMood(today, page, 3, 3)
        val vm = viewModel()
        val state = vm.state.awaitItem { it.todayEntries.isNotEmpty() }
        assertThat(state.todayEntries.single().noteId).isEqualTo(page)
        assertThat(journal.pageMood(today, page)!!.mood).isEqualTo(3)
    }

    @Test
    fun patterns_withHabitsFocusAndSleep() = runBlocking<Unit> {
        graph.pro = true
        val zone = graph.time.zone()
        val today = this@MoodTrackerViewModelTest.today // fixed: the clock moves below
        val walk = graph.habits.save(Habit(title = "Walk", startDate = today.minusDays(30)))
        // Ten days: good days have a walk, 60 focus minutes and 8 hours of sleep.
        (0L until 10L).forEach { daysAgo ->
            val day = today.minusDays(daysAgo)
            val good = daysAgo % 2 == 0L
            graph.time.setLocal(day, java.time.LocalTime.of(9, 0))
            graph.moods.checkIn(if (good) 5 else 2, null)
            if (good) {
                graph.habits.checkIn(walk, day)
                graph.focus.start(Duration.ofMinutes(60).toMillis(), null)
                graph.time.advance(Duration.ofMinutes(60))
                graph.focus.finish()
            }
            graph.health.set(HealthMetric.SLEEP_MINUTES, day, if (good) 480 else 300, zone)
        }
        graph.time.setLocal(today, java.time.LocalTime.of(20, 0))
        val vm = viewModel()
        val without = vm.state.awaitItem { (it.insights?.correlations?.size ?: 0) >= 2 }
        assertThat(without.sleepAccess).isFalse()
        assertThat(without.insights!!.correlations.map { it.factor }).containsExactly(MoodFactor.HABITS, MoodFactor.FOCUS)
        assertThat(without.insights!!.habitEffects.single().title).isEqualTo("Walk")
        assertThat(vm.sleepPermissions()).containsExactly("read:SLEEP_MINUTES")
        graph.health.grant(HealthMetric.SLEEP_MINUTES)
        vm.refreshHealth()
        val with = vm.state.awaitItem { it.sleepAccess && it.insights!!.correlations.size == 3 }
        assertThat(with.insights!!.correlations.first { it.factor == MoodFactor.SLEEP }.r).isWithin(0.001f).of(1f)
    }
}
