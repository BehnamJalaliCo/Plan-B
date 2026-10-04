package com.behnamjalali.planb.feature.notebooks

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.DocumentFiles
import com.behnamjalali.planb.core.data.repository.NoteRepository
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Markdown
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.NotebookSection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotebooksUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val notebooks: List<Notebook> = emptyList(),
    val archivedNotebooks: List<Notebook> = emptyList(),
    val pinned: List<Note> = emptyList(),
    val recent: List<Note> = emptyList(),
    val archivedNotes: List<Note> = emptyList(),
    val showArchive: Boolean = false,
)

sealed interface NotebooksEvent {
    data class OpenNote(val id: EntityId) : NotebooksEvent
    data object Imported : NotebooksEvent
    data object ImportFailed : NotebooksEvent
    data object Failed : NotebooksEvent
}

@HiltViewModel
class NotebooksViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val notes: NoteRepository,
    private val files: DocumentFiles,
) : ViewModel() {
    private val showArchive = savedState.getStateFlow(KEY_ARCHIVE, false)
    private val _events = MutableSharedFlow<NotebooksEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<NotebooksEvent> = _events

    val uiState: StateFlow<NotebooksUiState> = combine(
        listOf(
            notes.observeNotebooks(false), notes.observeNotebooks(true), notes.observePinnedOrFavorite(8),
            notes.observeRecent(6), notes.observeArchivedNotes(), showArchive,
        ),
    ) { v ->
        @Suppress("UNCHECKED_CAST")
        NotebooksUiState(
            loading = false,
            notebooks = v[0] as List<Notebook>,
            archivedNotebooks = v[1] as List<Notebook>,
            pinned = v[2] as List<Note>,
            recent = v[3] as List<Note>,
            archivedNotes = v[4] as List<Note>,
            showArchive = v[5] as Boolean,
        )
    }.catch { emit(NotebooksUiState(loading = false, error = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotebooksUiState())

    fun toggleArchive() { savedState[KEY_ARCHIVE] = !showArchive.value }

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        runCatchingSafely { block() }.onFailure { _events.tryEmit(NotebooksEvent.Failed) }
    }

    fun saveNotebook(notebook: Notebook) {
        if (notebook.title.isBlank()) return
        launchSafely { notes.saveNotebook(notebook) }
    }

    fun setNotebookArchived(id: EntityId, archived: Boolean) = launchSafely { notes.setNotebookArchived(id, archived) }
    fun deleteNotebook(id: EntityId) = launchSafely { notes.deleteNotebook(id) }
    fun restoreNote(id: EntityId) = launchSafely { notes.setArchived(id, false) }
    fun deleteNote(id: EntityId) = launchSafely { notes.deleteNote(id) }

    fun move(id: EntityId, delta: Int) = launchSafely {
        val ids = uiState.value.notebooks.map { it.id }.toMutableList()
        val i = ids.indexOf(id)
        val target = i + delta
        if (i < 0 || target !in ids.indices) return@launchSafely
        ids.removeAt(i)
        ids.add(target, id)
        notes.reorderNotebooks(ids)
    }

    fun importMarkdown(uri: Uri, fallbackTitle: String, defaultNotebookTitle: String) {
        viewModelScope.launch {
            runCatchingSafely {
                val text = files.readText(uri)
                val imported = Markdown.import(text, fallbackTitle) { UUID.randomUUID().toString() }
                val notebook = notes.ensureDefaultNotebook(defaultNotebookTitle)
                notes.saveNote(Note(notebookId = notebook, title = imported.title, document = imported.document))
            }.onSuccess {
                _events.tryEmit(NotebooksEvent.Imported)
                _events.tryEmit(NotebooksEvent.OpenNote(it))
            }.onFailure { _events.tryEmit(NotebooksEvent.ImportFailed) }
        }
    }

    private companion object {
        const val KEY_ARCHIVE = "notebooks_show_archive"
    }
}

data class NotebookDetailUiState(
    val loading: Boolean = true,
    val notebook: Notebook? = null,
    val missing: Boolean = false,
    val sections: List<NotebookSection> = emptyList(),
    val selectedSection: EntityId? = null,
    val notes: List<Note> = emptyList(),
    val otherNotebooks: List<Notebook> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotebookDetailViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val notes: NoteRepository,
) : ViewModel() {
    val notebookId = savedState.toRoute<NotebookDetailRoute>().notebookId
    private val section = savedState.getStateFlow<Long?>(KEY_SECTION, null)
    private val _failed = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    val failed: SharedFlow<Unit> = _failed

    val uiState: StateFlow<NotebookDetailUiState> = section.flatMapLatest { sectionId ->
        combine(
            notes.observeNotebook(notebookId),
            notes.observeSections(notebookId),
            notes.observeNotes(notebookId, sectionId),
            notes.observeNotebooks(false),
        ) { notebook, sections, list, all ->
            NotebookDetailUiState(
                loading = false,
                notebook = notebook,
                missing = notebook == null,
                sections = sections,
                selectedSection = sectionId?.takeIf { id -> sections.any { it.id == id } },
                notes = list,
                otherNotebooks = all.filter { it.id != notebookId },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotebookDetailUiState())

    fun selectSection(id: EntityId?) { savedState[KEY_SECTION] = id }

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        runCatchingSafely { block() }.onFailure { _failed.tryEmit(Unit) }
    }

    fun saveSection(section: NotebookSection) {
        if (section.title.isBlank()) return
        launchSafely { notes.saveSection(section) }
    }

    fun moveSection(sectionId: EntityId, target: EntityId) = launchSafely {
        notes.moveSection(sectionId, target)
        if (section.value == sectionId) selectSection(null)
    }

    fun deleteSection(id: EntityId) = launchSafely {
        notes.deleteSection(id)
        if (section.value == id) selectSection(null)
    }

    fun togglePin(note: Note) = launchSafely { notes.setPinned(note.id, !note.pinned) }

    private companion object {
        const val KEY_SECTION = "notebook_section"
    }
}
