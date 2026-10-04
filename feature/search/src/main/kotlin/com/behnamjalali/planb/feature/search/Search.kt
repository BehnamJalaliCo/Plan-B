package com.behnamjalali.planb.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.data.repository.SearchRepository
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerEmptyState
import com.behnamjalali.planb.core.designsystem.component.PlannerErrorState
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerPill
import com.behnamjalali.planb.core.designsystem.component.PlannerSearchBar
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.SearchEntityType
import com.behnamjalali.planb.core.model.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

@Serializable data object SearchRoute

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Results(val query: String, val results: List<SearchResult>) : SearchUiState
    data object Error : SearchUiState
}

/** Debounced FTS search; each keystroke cancels the previous query (mapLatest). */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val search: SearchRepository,
) : ViewModel() {
    val query: StateFlow<String> = savedState.getStateFlow(KEY, "")

    /** Bumped by [refresh]; re-runs the current query so results never show deleted or renamed items. */
    private val refreshes = MutableStateFlow(0)

    val uiState: StateFlow<SearchUiState> = combine(query.debounce(220).distinctUntilChanged(), refreshes) { q, _ -> q }.mapLatest { q ->
        if (q.isBlank()) {
            SearchUiState.Idle
        } else {
            try {
                SearchUiState.Results(q, search.search(q))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchUiState.Error
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Idle)

    fun setQuery(value: String) { savedState[KEY] = value }

    /** Runs the current query again; called whenever the screen (re)appears, e.g. back from a result. */
    fun refresh() = refreshes.update { it + 1 }

    private companion object { const val KEY = "search_query" }
}

@Composable
fun searchTypeLabel(type: SearchEntityType): String = stringResource(
    when (type) {
        SearchEntityType.TASK -> R.string.search_type_task
        SearchEntityType.PROJECT -> R.string.search_type_project
        SearchEntityType.NOTE -> R.string.search_type_note
        SearchEntityType.NOTEBOOK -> R.string.search_type_notebook
        SearchEntityType.HABIT -> R.string.search_type_habit
        SearchEntityType.GOAL -> R.string.search_type_goal
        SearchEntityType.EVENT -> R.string.search_type_event
    },
)

private fun icon(type: SearchEntityType) = when (type) {
    SearchEntityType.TASK -> Icons.Rounded.CheckCircle
    SearchEntityType.PROJECT -> Icons.Rounded.Folder
    SearchEntityType.NOTE -> Icons.AutoMirrored.Rounded.Notes
    SearchEntityType.NOTEBOOK -> Icons.AutoMirrored.Rounded.MenuBook
    SearchEntityType.HABIT -> Icons.Rounded.Repeat
    SearchEntityType.GOAL -> Icons.Rounded.Flag
    SearchEntityType.EVENT -> Icons.Rounded.Event
}

@Composable
fun SearchDestination(onBack: () -> Unit, onOpen: (SearchResult) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The opened result may have been edited or deleted meanwhile.
    LifecycleStartEffect(viewModel) {
        viewModel.refresh()
        onStopOrDispose { }
    }
    SearchScreen(query, state, viewModel::setQuery, onBack, onOpen)
}

@Composable
fun SearchScreen(
    query: String,
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpen: (SearchResult) -> Unit,
    autoFocus: Boolean = true,
) {
    val focus = remember { FocusRequester() }
    // Focus (and the keyboard) only on first open, not every time the user comes back to the screen.
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    if (autoFocus && !focusedOnce) {
        LaunchedEffect(Unit) {
            runCatching { focus.requestFocus() }
            focusedOnce = true
        }
    }
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(stringResource(R.string.search_title), onBack = onBack)
        PlannerSearchBar(query, onQueryChange, stringResource(R.string.search_hint), Modifier.padding(horizontal = Spacing.screen), focusRequester = focus)
        when (state) {
            SearchUiState.Idle -> PlannerEmptyState(Icons.Rounded.Search, stringResource(R.string.search_prompt_title), stringResource(R.string.search_prompt))
            SearchUiState.Loading -> PlannerLoadingState()
            SearchUiState.Error -> PlannerErrorState(stringResource(R.string.search_error))
            is SearchUiState.Results -> if (state.results.isEmpty()) {
                PlannerEmptyState(Icons.Rounded.SearchOff, stringResource(R.string.search_no_results_title), stringResource(R.string.search_no_results))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    state.results.groupBy { it.type }.forEach { (type, list) ->
                        item(key = "h_$type") { PlannerSectionHeader(searchTypeLabel(type)) }
                        items(list, key = { "${it.type}_${it.id}" }) { r ->
                            PlannerCard(Modifier.fillMaxWidth(), onClick = { onOpen(r) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(icon(r.type), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.md))
                                    Spacer(Modifier.width(Spacing.md))
                                    Column(Modifier.weight(1f)) {
                                        Text(r.title.ifBlank { stringResource(com.behnamjalali.planb.core.ui.R.string.ui_untitled) },
                                            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (r.snippet.isNotBlank()) {
                                            Text(r.snippet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    if (r.archived) PlannerPill(stringResource(R.string.search_archived))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

