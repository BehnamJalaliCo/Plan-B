package com.behnamjalali.planb.feature.notebooks.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.NoteLinkRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.NoteRef
import com.behnamjalali.planb.core.model.WritingSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Links of the note being edited (Plan-B Pro #16): the current titles of the notes it links to
 * (renames show at once; deleted or trashed notes are marked), the notes that link to it, and
 * the "[[" picker's search. Kept apart from the editor's own state.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class NoteKnowledgeViewModel @Inject constructor(
    private val links: NoteLinkRepository,
) : ViewModel() {
    private val noteId = MutableStateFlow<EntityId?>(null)
    private val linkIds = MutableStateFlow<Set<EntityId>>(emptySet())
    private val query = MutableStateFlow<String?>(null)

    /** The current state of every linked note; a missing id was deleted for good. */
    val refs: StateFlow<Map<EntityId, NoteRef>> = linkIds.flatMapLatest { links.observeRefs(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val backlinks: StateFlow<List<NoteRef>> = noteId.flatMapLatest { id -> if (id == null || id == 0L) flowOf(emptyList()) else links.observeBacklinks(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _suggestions = MutableStateFlow(Suggestions())
    val suggestions: StateFlow<Suggestions> = _suggestions.asStateFlow()

    data class Suggestions(val query: String? = null, val notes: List<NoteRef> = emptyList())

    init {
        viewModelScope.launch {
            query.debounce(SEARCH_DELAY_MS).distinctUntilChanged().collectLatest { q ->
                if (q == null) {
                    _suggestions.value = Suggestions()
                } else {
                    val found = runCatchingSafely { links.searchNotes(q, excludeId = noteId.value, limit = MAX_SUGGESTIONS) }.getOrDefault(emptyList())
                    _suggestions.value = Suggestions(q, found)
                }
            }
        }
    }

    fun bind(id: EntityId) {
        noteId.value = id
    }

    fun setLinkIds(ids: Set<EntityId>) {
        linkIds.value = ids
    }

    /** The text typed after "[[", or null when the caret left the search. */
    fun search(text: String?) {
        query.value = text
        if (text == null) _suggestions.value = Suggestions()
    }

    private companion object {
        const val SEARCH_DELAY_MS = 120L
        const val MAX_SUGGESTIONS = 6
    }
}

/**
 * Writing mode (Plan-B Pro #24): the daily word goal and streak (user preferences) and the
 * session timer. Words count when the note grows during the session; deleting and retyping
 * counts again, as it is writing too.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class WritingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val time: TimeProvider,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    val writing: StateFlow<WritingSettings> = settings.settings.map { it.writing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WritingSettings())

    private var sessionStart = time.monotonicMillis()

    /** Whole minutes since the session started, refreshed every half minute. */
    val sessionMinutes: StateFlow<Int> = flow {
        while (true) {
            emit(((time.monotonicMillis() - sessionStart) / 60_000L).toInt().coerceAtLeast(0))
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private var lastWords: Int? = null
    private val pending = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            pending.debounce(SAVE_DELAY_MS).collect { flush() }
        }
    }

    fun startSession(words: Int) {
        sessionStart = time.monotonicMillis()
        lastWords = words
    }

    /** The note now has [words] words; growth since the last call counts towards today's goal. */
    fun onWords(words: Int) {
        val previous = lastWords
        lastWords = words
        if (previous != null && words > previous) pending.value += words - previous
    }

    private suspend fun flush() {
        val added = pending.value
        if (added <= 0) return
        pending.value = 0
        val today = time.today()
        runCatchingSafely { settings.update { it.copy(writing = it.writing.addWords(today, added)) } }
    }

    fun setGoal(goal: Int) = viewModelScope.launch {
        runCatchingSafely { settings.update { it.copy(writing = it.writing.copy(dailyGoal = goal.coerceIn(0, WritingSettings.MAX_GOAL))) } }
    }

    fun setTypewriter(on: Boolean) = viewModelScope.launch {
        runCatchingSafely { settings.update { it.copy(writing = it.writing.copy(typewriter = on)) } }
    }

    fun today() = time.today()

    override fun onCleared() {
        // Words typed in the last moment still count (the settings store outlives this screen).
        val added = pending.value
        if (added > 0) {
            pending.value = 0
            val today = time.today()
            appScope.launch { runCatchingSafely { settings.update { it.copy(writing = it.writing.addWords(today, added)) } } }
        }
    }

    private companion object {
        const val SAVE_DELAY_MS = 1_500L
    }
}
