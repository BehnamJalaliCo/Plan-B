package com.behnamjalali.planb.feature.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.SearchNormalizer
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.common.todayFlow
import com.behnamjalali.planb.core.data.repository.ProjectRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.SmartListRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.SavedFilter
import com.behnamjalali.planb.core.model.Tag
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskSort
import com.behnamjalali.planb.core.model.TaskView
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TasksFilterState(
    val view: TaskView = TaskView.TODAY,
    val projectId: EntityId? = null,
    val tagId: EntityId? = null,
    val sort: TaskSort = TaskSort.MANUAL,
    val query: String = "",
    /** A custom smart list (Plan-B Pro #10) shown instead of [view]; null for the built-in views. */
    val smartListId: EntityId? = null,
)

data class TasksUiState(
    val loading: Boolean = true,
    val filter: TasksFilterState = TasksFilterState(),
    val tasks: List<Task> = emptyList(),
    val projects: List<Project> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val selection: Set<EntityId> = emptySet(),
    val error: Boolean = false,
    val smartLists: List<SavedFilter> = emptyList(),
) {
    val activeSmartList: SavedFilter? get() = filter.smartListId?.let { id -> smartLists.firstOrNull { it.id == id } }
    val selecting: Boolean get() = selection.isNotEmpty()
    val projectNames: Map<EntityId, Project> get() = projects.associateBy { it.id }
    val canReorder: Boolean get() = filter.smartListId == null && filter.sort == TaskSort.MANUAL && filter.query.isBlank() &&
        filter.view in setOf(TaskView.INBOX, TaskView.ALL)
}

sealed interface TasksMessage {
    /** [token] identifies this delete for [TasksViewModel.undoDelete] / [TasksViewModel.commitDelete]. */
    data class Deleted(val token: Long, val count: Int) : TasksMessage
    data class Completed(val taskId: EntityId, val nextOccurrenceId: EntityId?) : TasksMessage
    data object Failed : TasksMessage
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    projects: ProjectRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
    @ApplicationScope private val appScope: CoroutineScope,
    private val smartListRepository: SmartListRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(
        TasksFilterState(
            view = savedState.get<String>(KEY_VIEW)?.let { runCatching { TaskView.valueOf(it) }.getOrNull() } ?: TaskView.TODAY,
            projectId = savedState.get<Long>(KEY_PROJECT),
            tagId = savedState.get<Long>(KEY_TAG),
            sort = savedState.get<String>(KEY_SORT)?.let { runCatching { TaskSort.valueOf(it) }.getOrNull() } ?: TaskSort.MANUAL,
            query = savedState.get<String>(KEY_QUERY).orEmpty(),
            smartListId = savedState.get<Long>(KEY_SMART_LIST),
        ),
    )

    private val smartLists = smartListRepository.observeLists()
    private val selection = MutableStateFlow<Set<EntityId>>(emptySet())

    /**
     * Deletes that can still be undone, by token. Their ids are hidden from the list; each one is
     * committed on its own, either by the snackbar that offered the undo or, when no snackbar
     * took it, by its own timer, so one delete's timeout or undo never affects another.
     */
    private val pendingDeletes = MutableStateFlow<Map<Long, Set<EntityId>>>(emptyMap())
    private val deleteTimers = ConcurrentHashMap<Long, Job>()
    private val nextDeleteToken = AtomicLong()

    /** How long a delete waits for an undo when no snackbar holds it; matches a short snackbar. */
    internal var undoWindowMillis: Long = UNDO_WINDOW_MILLIS

    private val _messages = MutableSharedFlow<TasksMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<TasksMessage> = _messages

    init {
        if (!savedState.contains(KEY_VIEW)) {
            viewModelScope.launch {
                val defaultView = settings.current().defaultTaskView
                // A view the user picked while settings were loading wins over the default.
                if (!savedState.contains(KEY_VIEW)) filter.update { it.copy(view = defaultView) }
            }
        }
    }

    private val taskList = combine(filter.map { it.copy(query = "") }, time.todayFlow(), smartLists) { f, today, lists ->
        Triple(f, today, lists.firstOrNull { it.id == f.smartListId })
    }.distinctUntilChanged()
        .flatMapLatest { (f, today, list) ->
            if (list != null) {
                smartListRepository.observeTasks(list.filter, today)
            } else {
                tasks.observeTasks(TaskFilter(view = f.view, today = today, projectId = f.projectId, tagId = f.tagId, sort = f.sort))
            }
        }

    private val query = filter.map { it.query }.debounce(150).onStart { emit(filter.value.query) }

    val uiState: StateFlow<TasksUiState> = combine(
        taskList, query, filter, projects.observeActiveProjects(), tasks.observeTags(), selection, pendingDeletes, smartLists,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val list = values[0] as List<Task>
        val q = values[1] as String
        val f = values[2] as TasksFilterState
        @Suppress("UNCHECKED_CAST")
        val pending = (values[6] as Map<Long, Set<EntityId>>).values.flatten().toSet()
        val normalized = SearchNormalizer.normalize(q)
        val visible = list.filter { it.id !in pending }.filter {
            normalized.isBlank() || SearchNormalizer.normalize(it.title + " " + it.description).contains(normalized)
        }
        val visibleIds = visible.mapTo(HashSet()) { it.id }
        @Suppress("UNCHECKED_CAST")
        TasksUiState(
            loading = false,
            filter = f,
            tasks = visible,
            projects = values[3] as List<Project>,
            tags = values[4] as List<Tag>,
            // A filter, search or delete can hide selected tasks; bulk actions must never reach them.
            selection = (values[5] as Set<EntityId>).filterTo(LinkedHashSet()) { it in visibleIds },
            smartLists = values[7] as List<SavedFilter>,
        )
    }.onEach { state ->
        // Drop hidden ids from the selection itself too, so they don't come back selected later.
        // Intersect the current selection (not this state's, which may already be stale) with what is visible.
        if (!state.error && selection.value.isNotEmpty()) {
            val visible = state.tasks.mapTo(HashSet()) { it.id }
            selection.update { current -> if (current.all { it in visible }) current else current.filterTo(LinkedHashSet()) { it in visible } }
        }
    }.catch { emit(TasksUiState(loading = false, error = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setView(view: TaskView) {
        savedState[KEY_VIEW] = view.name
        savedState[KEY_SMART_LIST] = null
        selection.value = emptySet()
        filter.update { it.copy(view = view, smartListId = null) }
    }

    /** Shows a custom smart list (Plan-B Pro #10) instead of a built-in view. */
    fun setSmartList(id: EntityId) {
        savedState[KEY_SMART_LIST] = id
        selection.value = emptySet()
        filter.update { it.copy(smartListId = id) }
    }

    fun setProject(id: EntityId?) {
        savedState[KEY_PROJECT] = id
        filter.update { it.copy(projectId = id) }
    }

    fun setTag(id: EntityId?) {
        savedState[KEY_TAG] = id
        filter.update { it.copy(tagId = id) }
    }

    fun setSort(sort: TaskSort) {
        savedState[KEY_SORT] = sort.name
        filter.update { it.copy(sort = sort) }
    }

    fun setQuery(query: String) {
        savedState[KEY_QUERY] = query
        filter.update { it.copy(query = query) }
    }

    fun toggleSelection(id: EntityId) = selection.update { if (id in it) it - id else it + id }
    fun clearSelection() { selection.value = emptySet() }
    fun selectAll() { selection.value = uiState.value.tasks.map { it.id }.toSet() }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatchingSafely { block() }.onFailure { _messages.tryEmit(TasksMessage.Failed) }
        }
    }

    fun setCompleted(id: EntityId, completed: Boolean) = launchSafely {
        val next = tasks.setCompleted(id, completed)
        if (completed) _messages.tryEmit(TasksMessage.Completed(id, next))
    }

    /**
     * Undo a completion. Reopening a recurring occurrence also removes the untouched occurrence
     * its completion created and moves the series back (see TaskRepository.setCompleted).
     */
    fun undoComplete(id: EntityId) = launchSafely {
        tasks.setCompleted(id, false)
    }

    /** The selected tasks that are actually on screen; never a task a filter has since hidden. */
    private fun visibleSelection(): List<EntityId> {
        val visible = uiState.value.tasks.mapTo(HashSet()) { it.id }
        return selection.value.filter { it in visible }
    }

    fun completeSelected(completed: Boolean) {
        val ids = visibleSelection()
        launchSafely {
            tasks.setCompleted(ids, completed)
            clearSelection()
        }
    }

    fun archiveSelected(archived: Boolean) {
        val ids = visibleSelection()
        launchSafely {
            tasks.setArchived(ids, archived)
            clearSelection()
        }
    }

    fun moveSelected(projectId: EntityId?) {
        val ids = visibleSelection()
        launchSafely {
            tasks.moveToProject(ids, projectId)
            clearSelection()
        }
    }

    fun duplicate(id: EntityId) = launchSafely { tasks.duplicate(id) }

    /** Hides [ids] at once and deletes them unless [undoDelete] is called for the returned token. */
    fun requestDelete(ids: Collection<EntityId>): Long? {
        if (ids.isEmpty()) return null
        val token = nextDeleteToken.incrementAndGet()
        pendingDeletes.update { it + (token to ids.toSet()) }
        clearSelection()
        deleteTimers[token] = viewModelScope.launch {
            delay(undoWindowMillis)
            commitDelete(token)
        }
        _messages.tryEmit(TasksMessage.Deleted(token, ids.size))
        return token
    }

    /**
     * Called by the snackbar that offers the undo for [token]: it now decides between [undoDelete]
     * and [commitDelete], so the fallback timer stops. False when the delete is already committed.
     */
    fun holdDelete(token: Long): Boolean {
        deleteTimers.remove(token)?.cancel()
        return token in pendingDeletes.value
    }

    fun undoDelete(token: Long) {
        deleteTimers.remove(token)?.cancel()
        pendingDeletes.update { it - token }
    }

    fun commitDelete(token: Long) {
        deleteTimers.remove(token)?.cancel()
        val ids = pendingDeletes.value[token] ?: return
        // The application scope finishes the delete even if this screen goes away meanwhile.
        appScope.launch {
            runCatchingSafely { tasks.delete(ids.toList()) }.onFailure { _messages.tryEmit(TasksMessage.Failed) }
            pendingDeletes.update { it - token }
        }
    }

    fun move(id: EntityId, delta: Int) = launchSafely {
        val current = uiState.value.tasks.map { it.id }.toMutableList()
        val index = current.indexOf(id)
        val target = index + delta
        if (index < 0 || target !in current.indices) return@launchSafely
        current.removeAt(index)
        current.add(target, id)
        tasks.reorder(current)
    }

    fun reorder(orderedIds: List<EntityId>) = launchSafely { tasks.reorder(orderedIds) }

    override fun onCleared() {
        // A pending delete the user did not undo is committed on a scope that outlives the screen.
        deleteTimers.values.forEach { it.cancel() }
        deleteTimers.clear()
        val ids = pendingDeletes.value.values.flatten()
        if (ids.isNotEmpty()) appScope.launch { runCatchingSafely { tasks.delete(ids) } }
    }

    private companion object {
        const val KEY_VIEW = "tasks_view"
        const val KEY_PROJECT = "tasks_project"
        const val KEY_TAG = "tasks_tag"
        const val KEY_SORT = "tasks_sort"
        const val KEY_QUERY = "tasks_query"
        const val KEY_SMART_LIST = "tasks_smart_list"

        /** [androidx.compose.material3.SnackbarDuration.Short]. */
        const val UNDO_WINDOW_MILLIS = 4_000L
    }
}
