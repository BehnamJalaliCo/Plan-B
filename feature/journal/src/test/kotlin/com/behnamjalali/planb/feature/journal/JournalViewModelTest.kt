package com.behnamjalali.planb.feature.journal

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.data.repository.OfflineJournalRepository
import com.behnamjalali.planb.core.data.repository.OfflineRitualJournalRepository
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.JournalPrompts
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The daily journal screen's logic (Plan-B Pro #25) on the real data layer. */
@RunWith(RobolectricTestRunner::class)
class JournalViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph
    private lateinit var journal: OfflineJournalRepository
    private val texts = JournalTexts(notebook = "Journal", pageTitle = "Today", prompt = "What made you smile?")

    @Before
    fun setUp() {
        graph = TestDataGraph()
        journal = OfflineJournalRepository(graph.db, graph.notes, graph.time)
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        main.track(JournalViewModel(handle, journal, graph.settings, graph.time)).also { main.keepCollecting(it.state) }

    @Test
    fun promptRotatesByDayAndOnRequest_andSurvivesProcessDeath() = runBlocking<Unit> {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        val first = vm.state.awaitItem { !it.loading }
        assertThat(first.promptKey).isEqualTo(JournalPrompts.forDate(graph.time.today(), emptyList()))
        vm.anotherPrompt()
        val next = vm.state.awaitItem { it.promptKey != first.promptKey }.promptKey
        assertThat(next).isEqualTo(JournalPrompts.forDate(graph.time.today(), emptyList(), shift = 1))
        // A recreated screen (same saved state) shows the same prompt.
        main.clearViewModels()
        assertThat(viewModel(handle).state.awaitItem { !it.loading }.promptKey).isEqualTo(next)
    }

    @Test
    fun write_createsTheDaysPageWithItsPrompt_once() = runBlocking<Unit> {
        val vm = viewModel()
        val key = vm.state.awaitItem { !it.loading }.promptKey
        vm.write(texts)
        val opened = withTimeout(10_000) { vm.events.first() } as JournalEvent.OpenNote
        val note = graph.notes.getNote(opened.id)!!
        assertThat(note.document.blocks.first().type).isEqualTo(BlockType.QUOTE)
        assertThat(note.document.blocks.first().text).isEqualTo("What made you smile?")
        val state = vm.state.awaitItem { it.page != null }
        assertThat(state.page!!.promptId).isEqualTo(key)
        vm.write(texts)
        assertThat((withTimeout(10_000) { vm.events.first() } as JournalEvent.OpenNote).id).isEqualTo(opened.id)
    }

    @Test
    fun moodAndEnergy_areOneCheckInOnTheDaysPage() = runBlocking<Unit> {
        val vm = viewModel()
        vm.state.awaitItem { !it.loading }
        vm.setMood(texts, 4)
        vm.state.awaitItem { it.mood?.mood == 4 }
        vm.setEnergy(texts, 2)
        val state = vm.state.awaitItem { it.mood?.energy == 2 }
        assertThat(state.mood!!.mood).isEqualTo(4)
        assertThat(journal.observeMoods(graph.time.today(), graph.time.today()).first()).hasSize(1)
        assertThat(state.page).isNotNull()
        vm.setTags(texts, "calm, #work")
        assertThat(vm.state.awaitItem { it.tags.isNotEmpty() }.tags).containsExactly("calm", "work")
    }

    @Test
    fun ritualReflection_isTheSameDaysPage_andStreakCounts() = runBlocking<Unit> {
        val today = graph.time.today()
        OfflineRitualJournalRepository(graph.db, graph.notes, graph.time).append(today.minusDays(1), "Evening", "Good day", "Journal", "Yesterday", "ritual_evening")
        val ritualPage = OfflineRitualJournalRepository(graph.db, graph.notes, graph.time).append(today, "Intention", "Write", "Journal", "Today", "ritual_morning")
        val vm = viewModel()
        val state = vm.state.awaitItem { it.page != null }
        assertThat(state.page!!.noteId).isEqualTo(ritualPage)
        assertThat(state.streak).isEqualTo(2)
        assertThat(state.recent.map { it.date }).containsExactly(today.minusDays(1))
        // The ritual's own key is not a prompt: today's rotation prompt is shown instead.
        assertThat(JournalPrompts.builtInNumber(state.promptKey)).isNotNull()
    }

    @Test
    fun settings_customPromptsAndReminder() = runBlocking<Unit> {
        val vm = viewModel()
        vm.state.awaitItem { !it.loading }
        vm.addPrompt("  What did I notice?\n ")
        vm.addPrompt("What did I notice?")
        vm.setReminder(true)
        vm.setReminderTime(LocalTime.of(22, 0))
        val s = vm.state.awaitItem { it.settings.reminder && it.settings.reminderTime == LocalTime.of(22, 0) && it.settings.customPrompts.isNotEmpty() }.settings
        assertThat(s.customPrompts).containsExactly("What did I notice?")
        vm.removePrompt("What did I notice?")
        vm.state.awaitItem { it.settings.customPrompts.isEmpty() }
    }
}
