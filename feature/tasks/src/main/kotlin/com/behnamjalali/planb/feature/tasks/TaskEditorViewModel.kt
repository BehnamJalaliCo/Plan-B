package com.behnamjalali.planb.feature.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.behnamjalali.planb.core.common.Digits
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.ProjectRepository
import com.behnamjalali.planb.core.data.repository.DependencyCycleException
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskPlanningRepository
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
import com.behnamjalali.planb.core.model.TaskPlanning
import com.behnamjalali.planb.core.model.TaskReminder
import com.behnamjalali.planb.core.model.TaskReminderKind
import com.behnamjalali.planb.core.model.TaskReminderRules
import com.behnamjalali.planb.core.model.TaskStatus
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.ui.AssistantOutcome
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TagForm(val id: Long, val name: String, val color: String)

/** An extra reminder in the editor (Plan-B Pro #12): [kind] is a [TaskReminderKind] name, [at] epoch ms. */
@Serializable
data class ReminderForm(val kind: String, val offset: Int? = null, val at: Long? = null) {
    fun toModel(): TaskReminder? {
        val k = TaskReminderKind.entries.firstOrNull { it.name == kind } ?: return null
        return TaskReminder(kind = k, offsetMinutes = offset, at = at?.let(Instant::ofEpochMilli))
    }

    companion object {
        fun from(r: TaskReminder) = ReminderForm(r.kind.name, r.offsetMinutes, r.at?.toEpochMilli())
    }
}

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
    /** Hard deadline, epoch day (Plan-B Pro #11). */
    val deadline: Long? = null,
    /** Extra reminders and nagging (Plan-B Pro #12). */
    val extraReminders: List<ReminderForm> = emptyList(),
    val nag: Boolean = false,
    val nagInterval: Int = TaskReminderRules.DEFAULT_NAG_INTERVAL,
    /** Tasks this one waits for (Plan-B Pro #14). */
    val blockedBy: List<Long> = emptyList(),
    /** A time block set elsewhere (calendar); kept as it is. */
    val scheduledStart: Long? = null,
    val scheduledEnd: Long? = null,
) {
    val rule: RecurrenceRule? get() = RecurrenceRule.decode(recurrence)
    val due: LocalDate? get() = dueDate?.let(LocalDate::ofEpochDay)
    val deadlineDate: LocalDate? get() = deadline?.let(LocalDate::ofEpochDay)
    val canAddReminder: Boolean get() = extraReminders.size < TaskReminderRules.MAX_EXTRA
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
        deadline = deadlineDate,
        nag = nag,
        scheduledStart = scheduledStart?.let(Instant::ofEpochMilli),
        scheduledEnd = scheduledEnd?.let(Instant::ofEpochMilli),
    )

    /** The editor's extra reminders as stored; only the meaningful ones. */
    fun planningReminders(): List<TaskReminder> = extraReminders.mapNotNull { it.toModel() }

    companion object {
        fun from(task: Task, planning: TaskPlanning = TaskPlanning()) = TaskForm(
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
            deadline = task.deadline?.toEpochDay(),
            extraReminders = planning.reminders.map(ReminderForm::from),
            nag = task.nag,
            nagInterval = planning.nagIntervalMinutes,
            blockedBy = planning.blockedBy,
            scheduledStart = task.scheduledStart?.toEpochMilli(),
            scheduledEnd = task.scheduledEnd?.toEpochMilli(),
        )
    }
}

sealed interface EditorEvent {
    data class Saved(val id: EntityId) : EditorEvent
    data object Deleted : EditorEvent
    data object Failed : EditorEvent
    data object NotFound : EditorEvent

    /** The chosen task already waits (directly or not) for this one (Plan-B Pro #14). */
    data object DependencyCycle : EditorEvent

    /** An assistant change was applied (Plan-B Pro #39); the snackbar offers Undo. */
    data object AssistantApplied : EditorEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    projects: ProjectRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
    private val planning: TaskPlanningRepository,
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

    /** The tasks this one waits for, as tasks (title, done or not). */
    val blockers: StateFlow<List<Task>> = form.map { it.blockedBy }.distinctUntilChanged()
        .mapLatest { ids -> ids.mapNotNull { tasks.getTask(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Open tasks this one could wait for (the dependency picker). */
    val dependencyCandidates: StateFlow<List<Task>> = tasks.observeTasks(TaskFilter(view = TaskView.ALL, today = time.today(), topLevelOnly = false))
        .map { list -> list.filter { it.id != route.taskId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<EditorEvent> = _events

    val isNew: Boolean get() = route.taskId == NEW_ID

    val taskId: EntityId get() = route.taskId

    init {
        if (needsLoad) {
            viewModelScope.launch {
                val initial = if (route.taskId != NEW_ID) {
                    val task = tasks.getTask(route.taskId)
                    if (task == null) {
                        _events.tryEmit(EditorEvent.NotFound)
                        return@launch
                    }
                    TaskForm.from(task, planning.planning(task.id))
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

    fun setDeadline(date: LocalDate?) = update { it.copy(deadline = date?.toEpochDay()) }

    fun addReminder(reminder: ReminderForm) = update { f ->
        if (!f.canAddReminder || reminder in f.extraReminders) f else f.copy(extraReminders = f.extraReminders + reminder)
    }

    fun removeReminder(index: Int) = update { f -> f.copy(extraReminders = f.extraReminders.filterIndexed { i, _ -> i != index }) }

    fun setNag(nag: Boolean, interval: Int? = null) = update { f ->
        f.copy(nag = nag, nagInterval = TaskReminderRules.normalizeNagInterval(interval ?: f.nagInterval))
    }

    /** Adds a task this one waits for, unless that would make tasks wait for each other in a circle. */
    fun addBlocker(id: EntityId) {
        if (id in currentForm().blockedBy) return
        viewModelScope.launch {
            val cycle = id == route.taskId || (route.taskId != NEW_ID && runCatchingSafely { planning.wouldCreateCycle(route.taskId, id) }.getOrDefault(true))
            if (cycle) {
                _events.tryEmit(EditorEvent.DependencyCycle)
            } else {
                update { f -> if (id in f.blockedBy) f else f.copy(blockedBy = f.blockedBy + id) }
            }
        }
    }

    fun removeBlocker(id: EntityId) = update { f -> f.copy(blockedBy = f.blockedBy - id) }

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

    /** What the last assistant change replaced: the form before it and the subtasks it saved. */
    private var beforeAssistant: Pair<TaskForm, List<EntityId>>? = null

    /**
     * Plan-B Pro #39: subtasks or a title the user confirmed in the assistant sheet. A new task
     * keeps the subtasks pending until it is saved; an existing one saves them at once. Both can
     * be undone ([undoAssistant]).
     */
    fun applyAssistant(outcome: AssistantOutcome) {
        val before = currentForm()
        when (outcome) {
            is AssistantOutcome.SetTitle -> {
                beforeAssistant = before to emptyList()
                update { it.copy(title = outcome.title.replace('\n', ' ').trim()) }
                _events.tryEmit(EditorEvent.AssistantApplied)
            }
            is AssistantOutcome.AddSubtasks -> {
                val titles = outcome.items.map { it.trim() }.filter { it.isNotEmpty() }
                if (titles.isEmpty()) return
                if (isNew) {
                    beforeAssistant = before to emptyList()
                    update { it.copy(pendingSubtasks = it.pendingSubtasks + titles) }
                    _events.tryEmit(EditorEvent.AssistantApplied)
                } else {
                    viewModelScope.launch {
                        runCatchingSafely { titles.map { tasks.save(Task(title = it, parentTaskId = route.taskId, projectId = before.projectId)) } }
                            .onSuccess { ids ->
                                beforeAssistant = before to ids
                                _events.tryEmit(EditorEvent.AssistantApplied)
                            }
                            .onFailure { _events.tryEmit(EditorEvent.Failed) }
                    }
                }
            }
            else -> Unit
        }
    }

    fun undoAssistant() {
        val (form, saved) = beforeAssistant ?: return
        beforeAssistant = null
        // Only what the assistant changed goes back; later edits to other fields stay.
        update { it.copy(title = form.title, pendingSubtasks = form.pendingSubtasks) }
        if (saved.isNotEmpty()) {
            viewModelScope.launch { runCatchingSafely { tasks.delete(saved) }.onFailure { _events.tryEmit(EditorEvent.Failed) } }
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
                // Pro planning data is written only when it changed, so free edits work as before.
                val before = original ?: TaskForm()
                if (current.extraReminders != before.extraReminders || current.nagInterval != before.nagInterval) {
                    planning.setReminders(id, current.planningReminders(), current.nagInterval)
                }
                if (current.blockedBy != before.blockedBy) planning.setDependencies(id, current.blockedBy)
                id
            }.onSuccess { id ->
                original = current
                _events.tryEmit(EditorEvent.Saved(id))
            }.onFailure { error ->
                saving = false
                _events.tryEmit(if (error is DependencyCycleException) EditorEvent.DependencyCycle else EditorEvent.Failed)
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
