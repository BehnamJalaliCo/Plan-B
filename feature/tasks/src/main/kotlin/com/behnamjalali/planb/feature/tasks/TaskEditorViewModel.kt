package com.behnamjalali.planb.feature.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.behnamjalali.planb.core.common.Digits
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.ProjectRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.NEW_ID
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.behnamjalali.planb.core.model.Tag
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TagForm(val id: Long, val name: String, val color: String)

/** Serializable editor state so in-progress edits survive process death. */
@Serializable
data class TaskForm(
    val id: Long = NEW_ID,
    val title: String = "",
    val description: String = "",
    val notes: String = "",
    val status: TaskStatus = TaskStatus.TODO,
    val priority: Int = 0,
    val startDate: Long? = null,
    val dueDate: Long? = null,
    val startTime: Int? = null,
    val dueTime: Int? = null,
    val reminder: Int? = null,
    val recurrence: String? = null,
    val recurrenceAnchor: Long? = null,
    val projectId: Long? = null,
    val parentTaskId: Long? = null,
    val tags: List<TagForm> = emptyList(),
    val estimate: String = "",
    val actual: String = "",
    val pendingSubtasks: List<String> = emptyList(),
    val completedAt: Long? = null,
    val archived: Boolean = false,
    val createdAt: Long = 0,
) {
    val rule: RecurrenceRule? get() = RecurrenceRule.decode(recurrence)
    val due: LocalDate? get() = dueDate?.let(LocalDate::ofEpochDay)
    val start: LocalDate? get() = startDate?.let(LocalDate::ofEpochDay)
    val dueAt: LocalTime? get() = dueTime?.let { LocalTime.ofSecondOfDay(it.toLong()) }
    val startAt: LocalTime? get() = startTime?.let { LocalTime.ofSecondOfDay(it.toLong()) }
    val estimateValid: Boolean get() = estimate.isBlank() || Digits.toLatin(estimate).toIntOrNull() != null
    val actualValid: Boolean get() = actual.isBlank() || Digits.toLatin(actual).toIntOrNull() != null

    /** A task can't start after it is due. */
    val datesValid: Boolean get() = startDate == null || dueDate == null || startDate <= dueDate
    val canSave: Boolean get() = title.isNotBlank() && estimateValid && actualValid && datesValid

    fun toTask(): Task = Task(
        id = id,
        title = title.trim(),
        description = description,
        status = status,
        priority = Priority.fromWeight(priority),
        startDate = start,
        dueDate = due,
        startTime = startAt,
        dueTime = dueAt,
        reminderOffsetMinutes = if (dueDate != null) reminder else null,
        projectId = projectId,
        parentTaskId = parentTaskId,
        recurrence = rule,
        recurrenceAnchor = recurrenceAnchor?.let(LocalDate::ofEpochDay),
        estimatedMinutes = Digits.toLatin(estimate).toIntOrNull(),
        actualMinutes = Digits.toLatin(actual).toIntOrNull(),
        notes = notes,
        completedAt = completedAt?.let(Instant::ofEpochMilli),
        archived = archived,
        tags = tags.map { Tag(it.id, it.name, AccentColor.fromKey(it.color)) },
    )

    companion object {
        fun from(task: Task) = TaskForm(
            id = task.id,
            title = task.title,
            description = task.description,
            notes = task.notes,
            status = task.status,
            priority = task.priority.weight,
            startDate = task.startDate?.toEpochDay(),
            dueDate = task.dueDate?.toEpochDay(),
            startTime = task.startTime?.toSecondOfDay(),
            dueTime = task.dueTime?.toSecondOfDay(),
            reminder = task.reminderOffsetMinutes,
            recurrence = task.recurrence?.encode(),
            recurrenceAnchor = task.recurrenceAnchor?.toEpochDay(),
            projectId = task.projectId,
            parentTaskId = task.parentTaskId,
            tags = task.tags.map { TagForm(it.id, it.name, it.color.key) },
            estimate = task.estimatedMinutes?.toString().orEmpty(),
            actual = task.actualMinutes?.toString().orEmpty(),
            completedAt = task.completedAt?.toEpochMilli(),
            archived = task.archived,
            createdAt = task.createdAt.toEpochMilli(),
        )
    }
}

sealed interface EditorEvent {
    data class Saved(val id: EntityId) : EditorEvent
    data object Deleted : EditorEvent
    data object Failed : EditorEvent
    data object NotFound : EditorEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    projects: ProjectRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val route = runCatching { savedState.toRoute<TaskEditorRoute>() }.getOrDefault(TaskEditorRoute())
    private val json = Json { ignoreUnknownKeys = true }
    // Must be read before getStateFlow(), which stores its default value in the handle.
    private val needsLoad = !savedState.contains(KEY_FORM)

    val form: StateFlow<TaskForm> = savedState.getStateFlow(KEY_FORM, "")
        .map(::decode)
        .stateIn(viewModelScope, SharingStarted.Eagerly, TaskForm())

    private fun decode(raw: String?): TaskForm =
        if (raw.isNullOrBlank()) TaskForm() else json.decodeFromString(TaskForm.serializer(), raw)

    /**
     * The latest form, read straight from the saved state. [form] reaches collectors a dispatch
     * later, so edits made in quick succession (e.g. adding a tag and saving) must not build on it.
     */
    private fun currentForm(): TaskForm = decode(savedState.get<String>(KEY_FORM))

    private var original: TaskForm? = savedState.get<String>(KEY_ORIGINAL)?.let { json.decodeFromString(TaskForm.serializer(), it) }

    val projects: StateFlow<List<Project>> = projects.observeActiveProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val subtasks: StateFlow<List<Task>> =
        (if (route.taskId != NEW_ID) tasks.observeSubtasks(route.taskId) else flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val calendarSystem: StateFlow<CalendarSystem> = settings.settings.map { it.calendarSystem }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CalendarSystem.JALALI)

    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<EditorEvent> = _events

    val isNew: Boolean get() = route.taskId == NEW_ID

    init {
        if (needsLoad) {
            viewModelScope.launch {
                val initial = if (route.taskId != NEW_ID) {
                    val task = tasks.getTask(route.taskId)
                    if (task == null) {
                        _events.tryEmit(EditorEvent.NotFound)
                        return@launch
                    }
                    TaskForm.from(task)
                } else {
                    TaskForm(
                        projectId = route.projectId,
                        parentTaskId = route.parentId,
                        dueDate = route.dueEpochDay,
                    )
                }
                original = initial
                savedState[KEY_ORIGINAL] = json.encodeToString(TaskForm.serializer(), initial)
                write(initial)
            }
        }
    }

    val isDirty: Boolean get() = original != null && currentForm() != original

    private fun write(value: TaskForm) {
        savedState[KEY_FORM] = json.encodeToString(TaskForm.serializer(), value)
    }

    fun update(transform: (TaskForm) -> TaskForm) = write(transform(currentForm()))

    fun setDueDate(date: LocalDate?) = update {
        it.copy(dueDate = date?.toEpochDay(), reminder = if (date == null) null else it.reminder)
    }

    fun setDueTime(time: LocalTime?) = viewModelScope.launch {
        val defaultReminder = settings.current().defaultReminderMinutes
        update {
            it.copy(
                dueTime = time?.toSecondOfDay(),
                reminder = when {
                    time == null -> it.reminder
                    it.reminder == null && it.dueTime == null -> defaultReminder
                    else -> it.reminder
                },
            )
        }
    }

    fun setRecurrence(rule: RecurrenceRule?) = update {
        it.copy(
            recurrence = rule?.encode(),
            dueDate = it.dueDate ?: if (rule != null) time.today().toEpochDay() else null,
            recurrenceAnchor = if (rule == null) null else it.recurrenceAnchor,
        )
    }

    fun addTag(name: String) {
        val clean = name.trim().removePrefix("#")
        if (clean.isBlank()) return
        update { f ->
            if (f.tags.any { it.name.equals(clean, ignoreCase = true) }) f
            else f.copy(tags = f.tags + TagForm(NEW_ID, clean, AccentColor.entries[f.tags.size % AccentColor.entries.size].key))
        }
    }

    fun removeTag(name: String) = update { f -> f.copy(tags = f.tags.filterNot { it.name == name }) }

    fun addSubtask(title: String) {
        val clean = title.trim()
        if (clean.isBlank()) return
        if (isNew) {
            update { it.copy(pendingSubtasks = it.pendingSubtasks + clean) }
        } else {
            viewModelScope.launch {
                runCatchingSafely { tasks.save(Task(title = clean, parentTaskId = route.taskId, projectId = currentForm().projectId)) }
                    .onFailure { _events.tryEmit(EditorEvent.Failed) }
            }
        }
    }

    fun removePendingSubtask(index: Int) = update { f ->
        f.copy(pendingSubtasks = f.pendingSubtasks.filterIndexed { i, _ -> i != index })
    }

    fun toggleSubtask(id: EntityId, completed: Boolean) = viewModelScope.launch {
        runCatchingSafely { tasks.setCompleted(id, completed) }.onFailure { _events.tryEmit(EditorEvent.Failed) }
    }

    fun deleteSubtask(id: EntityId) = viewModelScope.launch {
        runCatchingSafely { tasks.delete(listOf(id)) }.onFailure { _events.tryEmit(EditorEvent.Failed) }
    }

    /** True from the first Save until it fails; a second tap must not insert the task twice. */
    private var saving = false

    /**
     * Saves the form. [pendingTag] is tag text typed but not yet confirmed with the keyboard's
     * Done key; it is added rather than silently dropped.
     */
    fun save(pendingTag: String = "") {
        if (saving) return
        addTag(pendingTag)
        val current = currentForm()
        if (!current.canSave) return
        saving = true
        viewModelScope.launch {
            runCatchingSafely {
                val id = tasks.save(current.toTask())
                current.pendingSubtasks.forEach { tasks.save(Task(title = it, parentTaskId = id, projectId = current.projectId)) }
                id
            }.onSuccess { id ->
                original = current
                _events.tryEmit(EditorEvent.Saved(id))
            }.onFailure {
                saving = false
                _events.tryEmit(EditorEvent.Failed)
            }
        }
    }

    fun delete() {
        if (isNew) return
        viewModelScope.launch {
            runCatchingSafely { tasks.delete(listOf(route.taskId)) }
                .onSuccess { _events.tryEmit(EditorEvent.Deleted) }
                .onFailure { _events.tryEmit(EditorEvent.Failed) }
        }
    }

    private companion object {
        const val KEY_FORM = "task_form"
        const val KEY_ORIGINAL = "task_form_original"
    }
}
