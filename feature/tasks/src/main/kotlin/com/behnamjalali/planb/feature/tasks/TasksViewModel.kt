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
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.Tag
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskSort
import com.behnamjalali.planb.core.model.TaskView
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
)

data class TasksUiState(
    val loading: Boolean = true,
    val filter: TasksFilterState = TasksFilterState(),
    val tasks: List<Task> = emptyList(),
    val projects: List<Project> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val selection: Set<EntityId> = emptySet(),
    val error: Boolean = false,
) {
    val selecting: Boolean get() = selection.isNotEmpty()
    val projectNames: Map<EntityId, Project> get() = projects.associateBy { it.id }
    val canReorder: Boolean get() = filter.sort == TaskSort.MANUAL && filter.query.isBlank() &&
        filter.view in setOf(TaskView.INBOX, TaskView.ALL)
}

sealed interface TasksMessage {
    data class Deleted(val count: Int) : TasksMessage
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
) : ViewModel() {

    private val filter = MutableStateFlow(
        TasksFilterState(
            view = savedState.get<String>(KEY_VIEW)?.let { runCatching { TaskView.valueOf(it) }.getOrNull() } ?: TaskView.TODAY,
            projectId = savedState.get<Long>(KEY_PROJECT),
            tagId = savedState.get<Long>(KEY_TAG),
            sort = savedState.get<String>(KEY_SORT)?.let { runCatching { TaskSort.valueOf(it) }.getOrNull() } ?: TaskSort.MANUAL,
        ),
    )
    private val selection = MutableStateFlow<Set<EntityId>>(emptySet())

    /** Ids hidden while a delete can still be undone; committed when the snackbar closes. */
    private val pendingDelete = MutableStateFlow<Set<EntityId>>(emptySet())

    private val _messages = MutableSharedFlow<TasksMessage>(extraBufferCapacity = 4)
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

    private val taskList = combine(filter.map { it.copy(query = "") }, time.todayFlow()) { f, today -> f to today }
        .flatMapLatest { (f, today) ->
            tasks.observeTasks(TaskFilter(view = f.view, today = today, projectId = f.projectId, tagId = f.tagId, sort = f.sort))
        }

    private val query = filter.map { it.query }.debounce(150).onStart { emit(filter.value.query) }

    val uiState: StateFlow<TasksUiState> = combine(
        taskList, query, filter, projects.observeActiveProjects(), tasks.observeTags(), selection, pendingDelete,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val list = values[0] as List<Task>
        val q = values[1] as String
        val f = values[2] as TasksFilterState
        @Suppress("UNCHECKED_CAST")
        val pending = values[6] as Set<EntityId>
        val normalized = SearchNormalizer.normalize(q)
        @Suppress("UNCHECKED_CAST")
        TasksUiState(
            loading = false,
            filter = f,
            tasks = list.filter { it.id !in pending }.filter {
                normalized.isBlank() || SearchNormalizer.normalize(it.title + " " + it.description).contains(normalized)
            },
            projects = values[3] as List<Project>,
            tags = values[4] as List<Tag>,
            selection = values[5] as Set<EntityId>,
        )
    }.catch { emit(TasksUiState(loading = false, error = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setView(view: TaskView) {
        savedState[KEY_VIEW] = view.name
        selection.value = emptySet()
        filter.update { it.copy(view = view) }
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

    fun setQuery(query: String) = filter.update { it.copy(query = query) }

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

    /** Undo a completion; also removes the occurrence a recurring completion created. */
    fun undoComplete(id: EntityId, nextOccurrenceId: EntityId?) = launchSafely {
        val original = tasks.getTask(id)
        if (nextOccurrenceId != null) {
            val next = tasks.getTask(nextOccurrenceId)
            tasks.delete(listOf(nextOccurrenceId))
            if (original != null && next != null) {
                tasks.save(original.copy(recurrence = next.recurrence, recurrenceAnchor = next.recurrenceAnchor))
            }
        }
        tasks.setCompleted(id, false)
    }

    fun completeSelected(completed: Boolean) = launchSafely {
        tasks.setCompleted(selection.value.toList(), completed)
        clearSelection()
    }

    fun archiveSelected(archived: Boolean) = launchSafely {
        tasks.setArchived(selection.value.toList(), archived)
        clearSelection()
    }

    fun moveSelected(projectId: EntityId?) = launchSafely {
        tasks.moveToProject(selection.value.toList(), projectId)
        clearSelection()
    }

    fun duplicate(id: EntityId) = launchSafely { tasks.duplicate(id) }

    fun requestDelete(ids: Collection<EntityId>) {
        if (ids.isEmpty()) return
        pendingDelete.update { it + ids }
        clearSelection()
        _messages.tryEmit(TasksMessage.Deleted(ids.size))
    }

    fun undoDelete() { pendingDelete.value = emptySet() }

    fun commitDelete() {
        val ids = pendingDelete.value
        if (ids.isEmpty()) return
        launchSafely {
            tasks.delete(ids.toList())
            pendingDelete.update { it - ids }
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
        val ids = pendingDelete.value
        if (ids.isNotEmpty()) appScope.launch { runCatchingSafely { tasks.delete(ids.toList()) } }
    }

    private companion object {
        const val KEY_VIEW = "tasks_view"
        const val KEY_PROJECT = "tasks_project"
        const val KEY_TAG = "tasks_tag"
        const val KEY_SORT = "tasks_sort"
    }
}
