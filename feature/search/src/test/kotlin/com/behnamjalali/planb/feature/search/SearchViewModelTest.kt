package com.behnamjalali.planb.feature.search

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.SearchEntityType
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        graph = TestDataGraph()
        viewModel = main.track(SearchViewModel(SavedStateHandle(), graph.search))
        main.keepCollecting(viewModel.uiState)
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

    private suspend fun awaitResults(query: String): SearchUiState.Results =
        viewModel.uiState.awaitItem { it is SearchUiState.Results && it.query == query } as SearchUiState.Results

    @Test
    fun blankQuery_showsPromptState() = runBlocking<Unit> {
        graph.tasks.save(Task(title = "Anything"))
        assertThat(viewModel.uiState.value).isEqualTo(SearchUiState.Idle)

        viewModel.setQuery("   ")
        delay(400) // longer than the 220 ms debounce
        assertThat(viewModel.query.value).isEqualTo("   ")
        assertThat(viewModel.uiState.value).isEqualTo(SearchUiState.Idle)
    }

    @Test
    fun query_yieldsResultsOnlyAfterDebounce() = runBlocking<Unit> {
        val id = graph.tasks.save(Task(title = "Quarterly report"))
        viewModel.setQuery("report")
        // The debounce window has not elapsed yet.
        assertThat(viewModel.uiState.value).isEqualTo(SearchUiState.Idle)

        val results = awaitResults("report")
        assertThat(results.results.map { it.type to it.id }).containsExactly(SearchEntityType.TASK to id)
        assertThat(results.results.single().title).isEqualTo("Quarterly report")
    }

    @Test
    fun arabicKeyboardLetters_matchPersianText() = runBlocking<Unit> {
        // "کاری" written with Persian kaf (U+06A9) and Persian yeh (U+06CC).
        val id = graph.tasks.save(Task(title = "برنامه کاری هفته"))
        graph.tasks.save(Task(title = "خرید"))

        // "كاري" typed on an Arabic keyboard: Arabic kaf (U+0643) and Arabic yeh (U+064A).
        viewModel.setQuery("كاري")
        val results = awaitResults("كاري")
        assertThat(results.results.map { it.id }).containsExactly(id)
    }

    @Test
    fun query_searchesAcrossEntityTypes() = runBlocking<Unit> {
        val today = graph.time.today()
        val task = graph.tasks.save(Task(title = "Prepare meeting agenda"))
        val notebook = graph.notes.ensureDefaultNotebook("Default")
        val note = graph.notes.saveNote(Note(notebookId = notebook, title = "Meeting minutes"))
        val habit = graph.habits.save(Habit(title = "Meeting prep", startDate = today))
        val event = graph.events.save(CalendarEvent(title = "Team meeting", date = today))
        graph.tasks.save(Task(title = "Unrelated"))

        viewModel.setQuery("meeting")
        val results = awaitResults("meeting").results
        assertThat(results.map { it.type to it.id }).containsExactly(
            SearchEntityType.TASK to task,
            SearchEntityType.NOTE to note,
            SearchEntityType.HABIT to habit,
            SearchEntityType.EVENT to event,
        )
    }

    @Test
    fun typingRapidly_onlyLatestQueryProducesResults_andClearingReturnsToPrompt() = runBlocking<Unit> {
        graph.tasks.save(Task(title = "Groceries"))
        val gym = graph.tasks.save(Task(title = "Gym session"))

        val seen = java.util.Collections.synchronizedList(mutableListOf<SearchUiState>())
        main.scope.launch { viewModel.uiState.collect { seen += it } }

        viewModel.setQuery("g")
        viewModel.setQuery("gy")
        viewModel.setQuery("gym")
        val results = awaitResults("gym")
        assertThat(results.results.map { it.id }).containsExactly(gym)
        // Intermediate keystrokes inside the debounce window never ran a search.
        assertThat(seen.filterIsInstance<SearchUiState.Results>().map { it.query }).containsExactly("gym")

        viewModel.setQuery("zzz")
        assertThat(awaitResults("zzz").results).isEmpty()

        viewModel.setQuery("")
        assertThat(viewModel.uiState.awaitItem { it == SearchUiState.Idle }).isEqualTo(SearchUiState.Idle)
    }

    @Test
    fun refresh_dropsResultsDeletedOrRenamedMeanwhile() = runBlocking<Unit> {
        val gone = graph.tasks.save(Task(title = "Milk run"))
        val renamed = graph.tasks.save(Task(title = "Milk tea"))
        viewModel.setQuery("milk")
        assertThat(awaitResults("milk").results.map { it.id }).containsExactly(gone, renamed)

        // The user opens results, deletes one and renames the other, then comes back.
        graph.tasks.delete(listOf(gone))
        graph.tasks.save(graph.tasks.getTask(renamed)!!.copy(title = "Green tea"))
        viewModel.refresh()
        viewModel.uiState.awaitItem { it is SearchUiState.Results && it.results.isEmpty() }
    }
}
