package com.behnamjalali.planb.feature.today.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.DayPlanRepository
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.DayPlanRequest
import com.behnamjalali.planb.core.model.DayPlanSettings
import com.behnamjalali.planb.core.model.DayPlanner
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.PlannedBlock
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.model.UnplannedReason
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Plan my day" schedules unscheduled tasks; "Replan" moves missed or clashing blocks (Plan-B Pro #5). */
enum class PlanMode { PLAN, REPLAN }

/** A proposed block: [moved] for a block "Replan" moves. Times are local to the device's zone. */
data class Proposal(val task: Task, val block: PlannedBlock, val start: LocalTime, val end: LocalTime, val moved: Boolean)

data class DayPlanUiState(
    val loading: Boolean = true,
    val mode: PlanMode = PlanMode.PLAN,
    val settings: DayPlanSettings = DayPlanSettings(),
    val proposals: List<Proposal> = emptyList(),
    val selected: Set<EntityId> = emptySet(),
    val notFit: List<Task> = emptyList(),
    val blocked: List<Task> = emptyList(),
    /** Nothing was left to plan (every task has a time, or every block is still ahead). */
    val nothingToDo: Boolean = false,
    val saving: Boolean = false,
)

sealed interface DayPlanEvent {
    data class Applied(val count: Int) : DayPlanEvent
    data object Failed : DayPlanEvent
}

/**
 * Builds a preview of the day from a snapshot of today's tasks, events and blocks. Nothing is
 * written until the user accepts, and then only the selected blocks.
 */
@HiltViewModel
class DayPlanViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val eventRepository: EventRepository,
    private val plans: DayPlanRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(DayPlanUiState())
    val state: StateFlow<DayPlanUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DayPlanEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<DayPlanEvent> = _events

    fun load(mode: PlanMode) {
        _state.value = DayPlanUiState(loading = true, mode = mode)
        viewModelScope.launch {
            val result = runCatchingSafely { compute(mode) }
            result.onSuccess { _state.value = it }.onFailure {
                _state.value = DayPlanUiState(loading = false, mode = mode, nothingToDo = true)
                _events.tryEmit(DayPlanEvent.Failed)
            }
        }
    }

    private suspend fun compute(mode: PlanMode): DayPlanUiState {
        val today = time.today()
        val zone = time.zone()
        val now = time.now()
        val dayPlan = settings.current().dayPlan
        val dayStart = today.atStartOfDay(zone).toInstant()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
        val todayTasks = tasks.observeTasks(TaskFilter(view = TaskView.TODAY, today = today)).first()
        val occurrences = eventRepository.observeOccurrences(today, today).first()
        val eventBusy = DayPlanner.eventRanges(occurrences, today, zone)
        val byId = HashMap<EntityId, Task>()
        todayTasks.forEach { byId[it.id] = it }
        return when (mode) {
            PlanMode.PLAN -> {
                // Blocks of other tasks today (also ones not in Today's list) are busy too.
                val blocks = plans.observeBlocks(dayStart, dayEnd).first()
                blocks.forEach { byId.putIfAbsent(it.id, it) }
                val busy = eventBusy + DayPlanner.taskRanges((todayTasks + blocks).distinctBy { it.id }, today, zone)
                val candidates = DayPlanner.candidates(todayTasks, today)
                val plan = DayPlanner.plan(DayPlanRequest(today, zone, now, dayPlan, busy, candidates))
                state(mode, dayPlan, plan.blocks, plan.unplanned.map { it.taskId to it.reason }, byId, zone, moved = false)
                    .copy(nothingToDo = candidates.isEmpty())
            }
            PlanMode.REPLAN -> {
                // Unfinished blocks of the last two weeks (a missed day moves forward too).
                val existing = plans.observeBlocks(dayStart.minus(Duration.ofDays(REPLAN_LOOKBACK_DAYS)), dayEnd).first()
                existing.forEach { byId.putIfAbsent(it.id, it) }
                val blocks = existing.mapNotNull { t -> t.scheduledStart?.let { s -> t.scheduledEnd?.let { e -> PlannedBlock(t.id, s, e) } } }
                val busy = eventBusy + DayPlanner.taskRanges(todayTasks.filter { it.scheduledStart == null }, today, zone)
                val request = DayPlanRequest(today, zone, now, dayPlan, busy)
                val plan = DayPlanner.replan(request, blocks)
                state(mode, dayPlan, plan.blocks, plan.unplanned.map { it.taskId to it.reason }, byId, zone, moved = true)
                    .copy(nothingToDo = plan.blocks.isEmpty() && plan.unplanned.isEmpty())
            }
        }
    }

    private fun state(
        mode: PlanMode,
        dayPlan: DayPlanSettings,
        blocks: List<PlannedBlock>,
        unplanned: List<Pair<EntityId, UnplannedReason>>,
        byId: Map<EntityId, Task>,
        zone: ZoneId,
        moved: Boolean,
    ): DayPlanUiState {
        val proposals = blocks.mapNotNull { block ->
            byId[block.taskId]?.let { Proposal(it, block, block.start.atZone(zone).toLocalTime(), block.end.atZone(zone).toLocalTime(), moved) }
        }
        return DayPlanUiState(
            loading = false,
            mode = mode,
            settings = dayPlan,
            proposals = proposals,
            selected = proposals.map { it.task.id }.toSet(),
            notFit = unplanned.filter { it.second == UnplannedReason.NO_FIT }.mapNotNull { byId[it.first] },
            blocked = unplanned.filter { it.second == UnplannedReason.BLOCKED }.mapNotNull { byId[it.first] },
        )
    }

    fun toggle(taskId: EntityId) {
        _state.update { s -> s.copy(selected = if (taskId in s.selected) s.selected - taskId else s.selected + taskId) }
    }

    /** Writes the selected blocks (one transaction). */
    fun accept() {
        val current = _state.value
        if (current.saving || current.loading) return
        val blocks = current.proposals.filter { it.task.id in current.selected }.map { it.block }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatchingSafely { plans.applyBlocks(blocks) }
                .onSuccess { _events.tryEmit(DayPlanEvent.Applied(it)) }
                .onFailure { _events.tryEmit(DayPlanEvent.Failed) }
            _state.update { it.copy(saving = false) }
        }
    }

    private companion object {
        const val REPLAN_LOOKBACK_DAYS = 14L
    }
}
