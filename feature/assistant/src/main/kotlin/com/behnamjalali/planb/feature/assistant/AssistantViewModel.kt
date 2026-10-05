package com.behnamjalali.planb.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.ai.AiAssistant
import com.behnamjalali.planb.core.ai.AiContextBudget
import com.behnamjalali.planb.core.ai.AiError
import com.behnamjalali.planb.core.ai.AiMessage
import com.behnamjalali.planb.core.ai.AiResult
import com.behnamjalali.planb.core.ai.AiRole
import com.behnamjalali.planb.core.ai.AiSettingsRepository
import com.behnamjalali.planb.core.ai.AiStreamEvent
import com.behnamjalali.planb.core.ai.AssistantParsing
import com.behnamjalali.planb.core.ai.AssistantPrompts
import com.behnamjalali.planb.core.ai.PlanBusyInput
import com.behnamjalali.planb.core.ai.PlanPromptInput
import com.behnamjalali.planb.core.ai.PlanTaskInput
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.DayPlanRepository
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.data.repository.NoteRepository
import com.behnamjalali.planb.core.data.repository.ProjectRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.DayPlanner
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.PlannedBlock
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.model.TimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URI
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the chat can read along with a question. */
enum class ContextKind { NONE, TODAY, WEEK, NOTE }

data class NoteRef(val id: EntityId, val title: String)

data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val streaming: Boolean = false,
    val error: AiError? = null,
)

data class PlanPreview(
    val week: Boolean,
    val loading: Boolean = true,
    val proposals: List<PlanProposal> = emptyList(),
    val selected: Set<EntityId> = emptySet(),
    val skipped: Int = 0,
    /** Nothing open to plan. */
    val nothingToPlan: Boolean = false,
    val error: AiError? = null,
    val saving: Boolean = false,
)

data class AssistantUiState(
    /** Null while loading the settings. */
    val ready: Boolean? = null,
    /** The provider's host, shown as "sent only to …". */
    val host: String = "",
    val context: ContextKind = ContextKind.TODAY,
    val note: NoteRef? = null,
    /** Exactly the context text that goes with the next question. */
    val contextText: String = "",
    val contextTokens: Int = 0,
    val truncated: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val streaming: Boolean = false,
    val plan: PlanPreview? = null,
)

sealed interface AssistantEvent {
    data class PlanApplied(val count: Int) : AssistantEvent
    data object PlanUndone : AssistantEvent
    data object Failed : AssistantEvent
}

/**
 * The Assistant screen (Plan-B Pro #39): a chat about the plan with the context the user picks
 * (shown before it is sent), and "plan my day/week" proposals that are checked by
 * [PlanValidator] and written only when the user accepts them, with Undo. The chat lives only
 * in memory; nothing is stored or logged.
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val assistant: AiAssistant,
    aiSettings: AiSettingsRepository,
    private val tasks: TaskRepository,
    private val projects: ProjectRepository,
    private val notes: NoteRepository,
    private val calendar: EventRepository,
    private val plans: DayPlanRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    private val _events = Channel<AssistantEvent>(Channel.BUFFERED)
    val events: Flow<AssistantEvent> = _events.receiveAsFlow()

    /** Notes the chat can read (locked notes are never offered). */
    val notesToPick: StateFlow<List<NoteRef>> = notes.observeRecent(NOTE_CHOICES)
        .map { list -> list.filter { !it.locked }.map { NoteRef(it.id, it.title) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var chatJob: Job? = null
    private var contextJob: Job? = null
    private var planJob: Job? = null
    private var nextId = 0L

    /** Blocks before the last accepted plan, for Undo: task id to its previous block (or null). */
    private var undo: Map<EntityId, PlannedBlock?> = emptyMap()

    init {
        viewModelScope.launch {
            aiSettings.settings.collect { s ->
                val host = runCatching { URI(s.effectiveBaseUrl).host }.getOrNull().orEmpty()
                _state.update { it.copy(ready = s.isReady, host = host) }
            }
        }
        refreshContext()
    }

    fun setInput(text: String) = _state.update { it.copy(input = text) }

    fun setContext(kind: ContextKind, note: NoteRef? = null) {
        _state.update { it.copy(context = kind, note = if (kind == ContextKind.NOTE) note else null) }
        refreshContext()
    }

    /** Rebuilds the context text the user sees (and that will be sent). */
    fun refreshContext() {
        contextJob?.cancel()
        contextJob = viewModelScope.launch {
            val current = _state.value
            val text = runCatchingSafely { buildContext(current.context, current.note) }.getOrDefault("")
            val cut = AiContextBudget.truncate(text)
            _state.update { it.copy(contextText = cut.text, contextTokens = AiContextBudget.approxTokens(cut.text), truncated = cut.truncated) }
        }
    }

    private suspend fun buildContext(kind: ContextKind, note: NoteRef?): String {
        val today = time.today()
        val header = PlannerContext.today(today, time.localNow().toLocalTime())
        return when (kind) {
            ContextKind.NONE -> ""
            ContextKind.TODAY -> {
                val list = tasks.observeTasks(TaskFilter(view = TaskView.TODAY, today = today)).first()
                val occurrences = calendar.observeOccurrences(today, today).first()
                section(header, list, occurrences.let(PlannerContext::events))
            }
            ContextKind.WEEK -> {
                val todayList = tasks.observeTasks(TaskFilter(view = TaskView.TODAY, today = today)).first()
                val upcoming = tasks.observeTasks(TaskFilter(view = TaskView.UPCOMING, today = today, upcomingUntil = today.plusDays(WEEK_DAYS - 1))).first()
                val occurrences = calendar.observeOccurrences(today, today.plusDays(WEEK_DAYS - 1)).first()
                section(header, (todayList + upcoming).distinctBy { it.id }, PlannerContext.events(occurrences))
            }
            ContextKind.NOTE -> {
                val n = note?.let { notes.getNote(it.id) }?.takeIf { !it.locked } ?: return ""
                PlannerContext.note(n)
            }
        }
    }

    private suspend fun section(header: String, list: List<Task>, calendar: String): String {
        val names = projectNames()
        return buildString {
            append(header).append("\n\nTasks:\n")
            append(PlannerContext.tasks(list, names).ifEmpty { "(none)" })
            if (calendar.isNotEmpty()) append("\n\nCalendar:\n").append(calendar)
        }
    }

    private suspend fun projectNames(): Map<EntityId, String> =
        projects.observeProjects(archived = false).first().associate { it.project.id to it.project.title }

    private fun contextLabel(kind: ContextKind): String? = when (kind) {
        ContextKind.NONE -> null
        ContextKind.TODAY -> "today's tasks and calendar"
        ContextKind.WEEK -> "this week's tasks and calendar"
        ContextKind.NOTE -> "a note"
    }

    fun send() {
        val current = _state.value
        val question = current.input.trim()
        if (question.isEmpty() || current.streaming || current.ready != true) return
        val history = current.messages.filter { it.error == null && it.text.isNotBlank() }
            .takeLast(MAX_HISTORY)
            .map { AiMessage(if (it.fromUser) AiRole.USER else AiRole.ASSISTANT, it.text.take(MAX_HISTORY_CHARS)) }
        val userMessage = ChatMessage(nextId++, fromUser = true, text = question)
        val replyId = nextId++
        _state.update {
            it.copy(
                input = "",
                streaming = true,
                messages = it.messages + userMessage + ChatMessage(replyId, fromUser = false, text = "", streaming = true),
            )
        }
        chatJob = viewModelScope.launch {
            val language = settings.settings.first().language.assistantLanguage()
            val messages = AssistantPrompts.chat(language, contextLabel(current.context), current.contextText, history, question)
            assistant.stream(messages, maxTokens = CHAT_TOKENS).collect { event ->
                when (event) {
                    is AiStreamEvent.Delta -> updateMessage(replyId) { it.copy(text = it.text + event.text) }
                    is AiStreamEvent.Failed -> updateMessage(replyId) { it.copy(streaming = false, error = event.error) }
                    AiStreamEvent.Done -> updateMessage(replyId) { it.copy(streaming = false) }
                }
            }
            finishStreaming(replyId)
        }
    }

    private fun updateMessage(id: Long, transform: (ChatMessage) -> ChatMessage) = _state.update { s ->
        s.copy(messages = s.messages.map { if (it.id == id) transform(it) else it })
    }

    private fun finishStreaming(id: Long) {
        updateMessage(id) { it.copy(streaming = false) }
        _state.update { it.copy(streaming = false) }
    }

    /** Stops the reply; what arrived stays. */
    fun stop() {
        chatJob?.cancel()
        _state.value.messages.lastOrNull { it.streaming }?.let { finishStreaming(it.id) }
        _state.update { it.copy(streaming = false) }
    }

    fun clearChat() {
        stop()
        _state.update { it.copy(messages = emptyList()) }
    }

    // region Plan my day / week

    fun plan(week: Boolean) {
        if (_state.value.ready != true) return
        planJob?.cancel()
        _state.update { it.copy(plan = PlanPreview(week = week)) }
        planJob = viewModelScope.launch {
            val result = runCatchingSafely { computePlan(week) }
            result.onSuccess { preview -> _state.update { it.copy(plan = preview) } }
                .onFailure { _state.update { it.copy(plan = PlanPreview(week = week, loading = false, error = AiError.PROVIDER_ERROR)) } }
        }
    }

    private suspend fun computePlan(week: Boolean): PlanPreview {
        val today = time.today()
        val zone = time.zone()
        val now = time.localNow()
        val dayPlan = settings.current().dayPlan
        val days = if (week) (0 until WEEK_DAYS).map { today.plusDays(it) } else listOf(today)
        val last = days.last()
        val todayList = tasks.observeTasks(TaskFilter(view = TaskView.TODAY, today = today)).first()
        val upcoming = if (week) tasks.observeTasks(TaskFilter(view = TaskView.UPCOMING, today = today, upcomingUntil = last)).first() else emptyList()
        val candidates = (todayList + upcoming).distinctBy { it.id }
            .filter { !it.isCompleted && it.scheduledStart == null && it.dueTime == null && it.openBlockerCount == 0 }
            .take(MAX_PLAN_TASKS)
        if (candidates.isEmpty()) return PlanPreview(week = week, loading = false, nothingToPlan = true)

        val from = today.atStartOfDay(zone).toInstant()
        val to = last.plusDays(1).atStartOfDay(zone).toInstant()
        val occurrences = calendar.observeOccurrences(today, last).first()
        val blocks = plans.observeBlocks(from, to).first()
        val busy = mutableListOf<TimeRange>()
        val busyInput = mutableListOf<PlanBusyInput>()
        for (day in days) {
            val dayBusy = DayPlanner.eventRanges(occurrences, day, zone) + DayPlanner.taskRanges((todayList + upcoming + blocks).distinctBy { it.id }, day, zone)
            busy += dayBusy
            dayBusy.forEach { r ->
                busyInput += PlanBusyInput(
                    day.toString(),
                    PlannerContext.hhmm(r.start.atZone(zone).toLocalTime()),
                    PlannerContext.hhmm(r.end.atZone(zone).toLocalTime()),
                )
            }
        }
        val earliest = now.truncatedTo(ChronoUnit.MINUTES).let { it.plusMinutes(((5 - it.minute % 5) % 5).toLong()) }
        val input = PlanPromptInput(
            days = days.map(LocalDate::toString),
            workStart = PlannerContext.hhmm(dayPlan.workStart),
            workEnd = PlannerContext.hhmm(dayPlan.workEnd),
            earliestToday = PlannerContext.hhmm(earliest.toLocalTime()),
            tasks = candidates.map { t ->
                PlanTaskInput(
                    id = t.id,
                    title = t.title,
                    priority = when (t.priority) {
                        Priority.HIGH -> "high"
                        Priority.MEDIUM -> "medium"
                        Priority.LOW -> "low"
                        else -> null
                    },
                    due = t.dueDate?.toString(),
                    deadline = t.deadline?.toString(),
                    estimateMinutes = t.estimatedMinutes,
                )
            },
            busy = busyInput,
        )
        val language = settings.settings.first().language.assistantLanguage()
        return when (val reply = assistant.complete(AssistantPrompts.plan(language, input), maxTokens = PLAN_TOKENS)) {
            is AiResult.Failure -> PlanPreview(week = week, loading = false, error = reply.error)
            is AiResult.Success -> {
                val validated = PlanValidator.validate(
                    AssistantParsing.parsePlan(reply.value),
                    candidates.associateBy { it.id },
                    days,
                    dayPlan,
                    busy,
                    earliest,
                    zone,
                )
                PlanPreview(
                    week = week,
                    loading = false,
                    proposals = validated.proposals,
                    selected = validated.proposals.map { it.task.id }.toSet(),
                    skipped = validated.skipped,
                )
            }
        }
    }

    fun togglePlanItem(taskId: EntityId) = _state.update { s ->
        val plan = s.plan ?: return@update s
        s.copy(plan = plan.copy(selected = if (taskId in plan.selected) plan.selected - taskId else plan.selected + taskId))
    }

    fun dismissPlan() {
        planJob?.cancel()
        _state.update { it.copy(plan = null) }
    }

    /** Writes the selected blocks in one transaction and remembers the old ones for Undo. */
    fun acceptPlan() {
        val plan = _state.value.plan ?: return
        if (plan.loading || plan.saving) return
        val chosen = plan.proposals.filter { it.task.id in plan.selected }
        if (chosen.isEmpty()) return
        _state.update { it.copy(plan = plan.copy(saving = true)) }
        viewModelScope.launch {
            val previous = chosen.associate { p ->
                val t = p.task
                t.id to (t.scheduledStart?.let { s -> t.scheduledEnd?.let { e -> PlannedBlock(t.id, s, e) } })
            }
            runCatchingSafely { plans.applyBlocks(chosen.map { PlannedBlock(it.task.id, it.range.start, it.range.end) }) }
                .onSuccess { count ->
                    undo = previous
                    _state.update { it.copy(plan = null) }
                    _events.trySend(AssistantEvent.PlanApplied(count))
                }
                .onFailure {
                    _state.update { s -> s.copy(plan = s.plan?.copy(saving = false)) }
                    _events.trySend(AssistantEvent.Failed)
                }
        }
    }

    /** Puts back the blocks the last accepted plan replaced. */
    fun undoPlan() {
        val previous = undo
        if (previous.isEmpty()) return
        undo = emptyMap()
        viewModelScope.launch {
            runCatchingSafely {
                plans.clearBlocks(previous.filterValues { it == null }.keys.toList())
                plans.applyBlocks(previous.values.filterNotNull())
            }.onSuccess { _events.trySend(AssistantEvent.PlanUndone) }
                .onFailure { _events.trySend(AssistantEvent.Failed) }
        }
    }

    // endregion

    private companion object {
        const val WEEK_DAYS = 7L
        const val NOTE_CHOICES = 30
        const val MAX_HISTORY = 10
        const val MAX_HISTORY_CHARS = 4_000
        const val MAX_PLAN_TASKS = 40
        const val CHAT_TOKENS = 1500
        const val PLAN_TOKENS = 2000
    }
}
