package com.behnamjalali.planb.feature.projects

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.ProjectRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.NEW_ID
import com.behnamjalali.planb.core.model.PlannerIcon
import com.behnamjalali.planb.core.model.ProgressMode
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.ProjectMilestone
import com.behnamjalali.planb.core.model.ProjectStatus
import com.behnamjalali.planb.core.model.ProjectSummary
import com.behnamjalali.planb.core.model.Tag
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskSort
import com.behnamjalali.planb.core.model.TaskStatus
import com.behnamjalali.planb.core.model.TaskView
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class ProjectsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val filter: ProjectStatus = ProjectStatus.ACTIVE,
    val projects: List<ProjectSummary> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    projects: ProjectRepository,
) : ViewModel() {
    private val filter = savedState.getStateFlow(KEY_FILTER, ProjectStatus.ACTIVE.name)

    val uiState: StateFlow<ProjectsUiState> = filter.flatMapLatest { name ->
        val status = ProjectStatus.valueOf(name)
        projects.observeProjects(archived = status == ProjectStatus.ARCHIVED).map { list ->
            ProjectsUiState(loading = false, filter = status, projects = list.filter { it.project.status == status || status == ProjectStatus.ARCHIVED })
        }
    }.catch { emit(ProjectsUiState(loading = false, error = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsUiState())

    fun setFilter(status: ProjectStatus) {
        savedState[KEY_FILTER] = status.name
    }

    private companion object {
        const val KEY_FILTER = "projects_filter"
    }
}

data class ProjectDetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val summary: ProjectSummary? = null,
    val milestones: List<ProjectMilestone> = emptyList(),
    val openTasks: List<Task> = emptyList(),
    val completedTasks: List<Task> = emptyList(),
)

enum class ProjectEvent { Failed, Deleted, NotesSaved }

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val projects: ProjectRepository,
    private val tasks: TaskRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val projectId = savedState.toRoute<ProjectDetailRoute>().projectId
    private val _events = MutableSharedFlow<ProjectEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ProjectEvent> = _events

    val uiState: StateFlow<ProjectDetailUiState> = combine(
        projects.observeProject(projectId),
        projects.observeMilestones(projectId),
        tasks.observeTasks(TaskFilter(view = TaskView.ALL, today = time.today(), projectId = projectId, sort = TaskSort.MANUAL)),
        tasks.observeTasks(TaskFilter(view = TaskView.COMPLETED, today = time.today(), projectId = projectId)),
    ) { summary, milestones, open, done ->
        ProjectDetailUiState(
            loading = false,
            missing = summary == null,
            summary = summary,
            milestones = milestones,
            openTasks = open,
            completedTasks = done,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectDetailUiState())

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        runCatchingSafely { block() }.onFailure { _events.tryEmit(ProjectEvent.Failed) }
    }

    fun addTask(title: String) {
        if (title.isBlank()) return
        launchSafely { tasks.save(Task(title = title.trim(), projectId = projectId)) }
    }

    fun setTaskCompleted(id: EntityId, completed: Boolean) = launchSafely { tasks.setCompleted(id, completed) }

    fun setTaskStatus(id: EntityId, status: TaskStatus) = launchSafely { tasks.setStatus(id, status) }

    fun addMilestone(title: String, date: LocalDate?) {
        if (title.isBlank()) return
        launchSafely { projects.saveMilestone(ProjectMilestone(projectId = projectId, title = title.trim(), date = date)) }
    }

    fun toggleMilestone(milestone: ProjectMilestone) = launchSafely { projects.setMilestoneCompleted(milestone, !milestone.completed) }

    fun deleteMilestone(id: EntityId) = launchSafely { projects.deleteMilestone(id) }

    fun setStatus(status: ProjectStatus) = launchSafely { projects.setStatus(projectId, status) }

    fun setArchived(archived: Boolean) = launchSafely { projects.setArchived(projectId, archived) }

    fun delete() = launchSafely {
        projects.delete(projectId)
        _events.tryEmit(ProjectEvent.Deleted)
    }

    private var notesJob: Job? = null

    /** Debounced autosave of the project's notes (stored as its description). */
    fun updateNotes(text: String) {
        notesJob?.cancel()
        notesJob = viewModelScope.launch {
            delay(600)
            runCatchingSafely {
                val project = projects.getProject(projectId) ?: return@runCatchingSafely
                if (project.description != text) projects.save(project.copy(description = text))
            }.onSuccess { _events.tryEmit(ProjectEvent.NotesSaved) }
                .onFailure { _events.tryEmit(ProjectEvent.Failed) }
        }
    }
}

@Serializable
data class ProjectForm(
    val id: Long = NEW_ID,
    val title: String = "",
    val description: String = "",
    val color: String = AccentColor.LAVENDER.key,
    val icon: String = PlannerIcon.FOLDER.key,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val progressMode: ProgressMode = ProgressMode.TASKS,
    val manualProgress: Float = 0f,
    val startDate: Long? = null,
    val dueDate: Long? = null,
    val tags: String = "",
    val createdAt: Long = 0,
    val sortOrder: Long = 0,
) {
    fun toProject() = Project(
        id = id,
        title = title.trim(),
        description = description,
        color = AccentColor.fromKey(color),
        icon = PlannerIcon.fromKey(icon),
        status = status,
        progressMode = progressMode,
        manualProgress = manualProgress,
        startDate = startDate?.let(LocalDate::ofEpochDay),
        dueDate = dueDate?.let(LocalDate::ofEpochDay),
        sortOrder = sortOrder,
        createdAt = Instant.ofEpochMilli(createdAt),
        tags = tags.split(',', '،').map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.distinct().map { Tag(name = it) },
    )

    companion object {
        fun from(p: Project) = ProjectForm(
            id = p.id, title = p.title, description = p.description, color = p.color.key, icon = p.icon.key, status = p.status,
            progressMode = p.progressMode, manualProgress = p.manualProgress, startDate = p.startDate?.toEpochDay(),
            dueDate = p.dueDate?.toEpochDay(), tags = p.tags.joinToString(", ") { it.name }, createdAt = p.createdAt.toEpochMilli(),
            sortOrder = p.sortOrder,
        )
    }
}

@HiltViewModel
class ProjectEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val projects: ProjectRepository,
) : ViewModel() {
    private val route = runCatching { savedState.toRoute<ProjectEditorRoute>() }.getOrDefault(ProjectEditorRoute())
    private val json = Json { ignoreUnknownKeys = true }
    private var original: ProjectForm? = savedState.get<String>(KEY_ORIGINAL)?.let { json.decodeFromString(ProjectForm.serializer(), it) }
    val form: StateFlow<ProjectForm> = savedState.getStateFlow(KEY_FORM, "")
        .map { if (it.isBlank()) ProjectForm() else json.decodeFromString(ProjectForm.serializer(), it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProjectForm())
    private val _saved = MutableSharedFlow<Long?>(extraBufferCapacity = 2)

    /** Emits the saved id, or null when saving failed. */
    val saved: SharedFlow<Long?> = _saved
    val isNew get() = route.projectId == NEW_ID
    val isDirty get() = original != null && original != form.value

    init {
        if (!savedState.contains(KEY_FORM)) {
            viewModelScope.launch {
                val initial = if (isNew) ProjectForm() else projects.getProject(route.projectId)?.let(ProjectForm::from) ?: ProjectForm()
                original = initial
                savedState[KEY_ORIGINAL] = json.encodeToString(ProjectForm.serializer(), initial)
                savedState[KEY_FORM] = json.encodeToString(ProjectForm.serializer(), initial)
            }
        }
    }

    fun update(transform: (ProjectForm) -> ProjectForm) {
        savedState[KEY_FORM] = json.encodeToString(ProjectForm.serializer(), transform(form.value))
    }

    fun save() {
        if (form.value.title.isBlank()) return
        viewModelScope.launch {
            runCatchingSafely { projects.save(form.value.toProject()) }
                .onSuccess {
                    original = form.value
                    _saved.tryEmit(it)
                }
                .onFailure { _saved.tryEmit(null) }
        }
    }

    private companion object {
        const val KEY_FORM = "project_form"
        const val KEY_ORIGINAL = "project_form_original"
    }
}

