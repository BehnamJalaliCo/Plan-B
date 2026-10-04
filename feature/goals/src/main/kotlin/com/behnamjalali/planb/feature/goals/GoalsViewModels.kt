package com.behnamjalali.planb.feature.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.GoalRepository
import com.behnamjalali.planb.core.data.repository.ProjectRepository
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Goal
import com.behnamjalali.planb.core.model.GoalMilestone
import com.behnamjalali.planb.core.model.GoalPace
import com.behnamjalali.planb.core.model.NEW_ID
import com.behnamjalali.planb.core.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data object GoalsRoute
@Serializable data class GoalDetailRoute(val goalId: Long)
@Serializable data class GoalEditorRoute(val goalId: Long = 0)

data class GoalsUiState(val loading: Boolean = true, val error: Boolean = false, val showArchived: Boolean = false, val goals: List<Goal> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GoalsViewModel @Inject constructor(private val savedState: SavedStateHandle, goals: GoalRepository) : ViewModel() {
    private val archived = savedState.getStateFlow(KEY, false)
    val uiState: StateFlow<GoalsUiState> = archived.flatMapLatest { a ->
        goals.observeGoals(a).map { GoalsUiState(loading = false, showArchived = a, goals = it) }
    }.catch { emit(GoalsUiState(loading = false, error = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    fun toggleArchived() { savedState[KEY] = !archived.value }

    private companion object { const val KEY = "goals_archived" }
}

data class GoalDetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val goal: Goal? = null,
    val pace: GoalPace? = null,
    val milestones: List<GoalMilestone> = emptyList(),
    val project: Project? = null,
)

enum class GoalEvent { Deleted, Failed }

@HiltViewModel
class GoalDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val goals: GoalRepository,
    projects: ProjectRepository,
    time: TimeProvider,
) : ViewModel() {
    val goalId = savedState.toRoute<GoalDetailRoute>().goalId
    private val _events = MutableSharedFlow<GoalEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<GoalEvent> = _events

    val uiState: StateFlow<GoalDetailUiState> = combine(
        goals.observeGoal(goalId), goals.observeMilestones(goalId), projects.observeActiveProjects(),
    ) { goal, milestones, projectList ->
        if (goal == null) {
            GoalDetailUiState(loading = false, missing = true)
        } else {
            val start = goal.createdAt.atZone(time.zone()).toLocalDate()
            GoalDetailUiState(
                loading = false,
                goal = goal,
                pace = GoalPace.of(goal, start, time.today()),
                milestones = milestones,
                project = projectList.firstOrNull { it.id == goal.projectId },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalDetailUiState())

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        runCatchingSafely { block() }.onFailure { _events.tryEmit(GoalEvent.Failed) }
    }

    /** Serializes progress changes, so each one builds on the stored value left by the previous one. */
    private val progressMutex = Mutex()

    fun setProgress(value: Double) = launchSafely { progressMutex.withLock { goals.updateProgress(goalId, value) } }

    /**
     * Adds [delta] (negative to subtract) to the stored progress. The new value is computed from
     * the repository, not from what the screen showed, so rapid taps all count.
     */
    fun adjustProgress(delta: Double) = launchSafely {
        progressMutex.withLock {
            val goal = goals.getGoal(goalId) ?: return@withLock
            goals.updateProgress(goalId, (goal.currentValue + delta).coerceAtLeast(0.0))
        }
    }
    fun addMilestone(title: String) {
        if (title.isBlank()) return
        launchSafely { goals.saveMilestone(GoalMilestone(goalId = goalId, title = title.trim())) }
    }
    fun toggleMilestone(m: GoalMilestone) = launchSafely { goals.setMilestoneCompleted(m, !m.completed) }
    fun deleteMilestone(id: EntityId) = launchSafely { goals.deleteMilestone(id) }
    fun setArchived(archived: Boolean) = launchSafely { goals.setArchived(goalId, archived) }
    fun delete() = launchSafely {
        goals.delete(goalId)
        _events.tryEmit(GoalEvent.Deleted)
    }
}

@Serializable
data class GoalForm(
    val id: Long = NEW_ID,
    val title: String = "",
    val description: String = "",
    val target: String = "",
    val current: String = "0",
    val unit: String = "",
    val deadline: Long? = null,
    val projectId: Long? = null,
    val notes: String = "",
    val createdAt: Long = 0,
    val archived: Boolean = false,
) {
    val targetValue get() = GoalNumbers.parse(target)?.takeIf { it > 0 }
    val currentValue get() = GoalNumbers.parse(current)
    val valid get() = title.isNotBlank() && targetValue != null && currentValue != null

    fun toGoal() = Goal(
        id = id, title = title.trim(), description = description, target = targetValue ?: 1.0, currentValue = currentValue ?: 0.0,
        unit = unit.trim(), deadline = deadline?.let(LocalDate::ofEpochDay), projectId = projectId, notes = notes,
        createdAt = Instant.ofEpochMilli(createdAt), archived = archived,
    )

    companion object {
        fun from(g: Goal) = GoalForm(
            g.id, g.title, g.description, GoalNumbers.format(g.target), GoalNumbers.format(g.currentValue), g.unit, g.deadline?.toEpochDay(), g.projectId, g.notes,
            g.createdAt.toEpochMilli(), g.archived,
        )
    }
}

@HiltViewModel
class GoalEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val goals: GoalRepository,
    projects: ProjectRepository,
) : ViewModel() {
    private val route = runCatching { savedState.toRoute<GoalEditorRoute>() }.getOrDefault(GoalEditorRoute())
    private val json = Json { ignoreUnknownKeys = true }
    // Must be read before getStateFlow(), which stores its default value in the handle.
    private val needsLoad = !savedState.contains(KEY_FORM)
    private var original: GoalForm? = savedState.get<String>(KEY_ORIGINAL)?.let { json.decodeFromString(GoalForm.serializer(), it) }
    val form: StateFlow<GoalForm> = savedState.getStateFlow(KEY_FORM, "")
        .map { if (it.isBlank()) GoalForm() else json.decodeFromString(GoalForm.serializer(), it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GoalForm())
    val projects: StateFlow<List<Project>> = projects.observeActiveProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _saved = MutableSharedFlow<Boolean>(extraBufferCapacity = 2)
    val saved: SharedFlow<Boolean> = _saved
    val isNew get() = route.goalId == NEW_ID
    val isDirty get() = original != null && original != form.value

    init {
        if (needsLoad) {
            viewModelScope.launch {
                val initial = if (isNew) GoalForm() else goals.getGoal(route.goalId)?.let(GoalForm::from) ?: GoalForm()
                original = initial
                savedState[KEY_ORIGINAL] = json.encodeToString(GoalForm.serializer(), initial)
                savedState[KEY_FORM] = json.encodeToString(GoalForm.serializer(), initial)
            }
        }
    }

    fun update(transform: (GoalForm) -> GoalForm) {
        savedState[KEY_FORM] = json.encodeToString(GoalForm.serializer(), transform(form.value))
    }

    fun save() {
        if (!form.value.valid) return
        viewModelScope.launch {
            runCatchingSafely { goals.save(form.value.toGoal()) }
                .onSuccess { original = form.value; _saved.tryEmit(true) }
                .onFailure { _saved.tryEmit(false) }
        }
    }

    private companion object {
        const val KEY_FORM = "goal_form"
        const val KEY_ORIGINAL = "goal_form_original"
    }
}
