package com.behnamjalali.planb.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.ai.AiAssistant
import com.behnamjalali.planb.core.ai.AiContextBudget
import com.behnamjalali.planb.core.ai.AiError
import com.behnamjalali.planb.core.ai.AiStreamEvent
import com.behnamjalali.planb.core.ai.AssistantLanguage
import com.behnamjalali.planb.core.ai.AssistantParsing
import com.behnamjalali.planb.core.ai.AssistantPrompts
import com.behnamjalali.planb.core.ai.TextAction
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.ui.AssistantAction
import com.behnamjalali.planb.core.ui.AssistantRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun AssistantAction.textAction(): TextAction = when (this) {
    AssistantAction.SUMMARIZE -> TextAction.SUMMARIZE
    AssistantAction.REWRITE -> TextAction.REWRITE
    AssistantAction.TRANSLATE -> TextAction.TRANSLATE
    AssistantAction.CONTINUE -> TextAction.CONTINUE
    AssistantAction.EXTRACT_TASKS -> TextAction.EXTRACT_TASKS
    AssistantAction.SUGGEST_TITLES -> TextAction.SUGGEST_TITLES
    AssistantAction.BREAK_DOWN -> TextAction.BREAK_DOWN
}

/** Actions whose answer is a list the user picks from (subtasks, tasks, titles). */
internal val AssistantAction.isList: Boolean
    get() = this == AssistantAction.EXTRACT_TASKS || this == AssistantAction.SUGGEST_TITLES || this == AssistantAction.BREAK_DOWN

data class AiActionState(
    /** Null while unknown; false shows the setup hint. */
    val ready: Boolean? = null,
    val action: AssistantAction? = null,
    val running: Boolean = false,
    /** The reply so far (text actions) or the raw answer (list actions). */
    val output: String = "",
    val items: List<String> = emptyList(),
    val selected: Set<Int> = emptySet(),
    val error: AiError? = null,
    /** Size of what is sent for the chosen text. */
    val sentChars: Int = 0,
    val sentTokens: Int = 0,
    val truncated: Boolean = false,
    /** Tasks just created from a note, for Undo. */
    val createdTasks: List<EntityId> = emptyList(),
    val tasksUndone: Boolean = false,
    /** Creating the tasks failed (a local error, not the provider's). */
    val saveFailed: Boolean = false,
)

/**
 * The contextual assistant sheet of the note and task editors (Plan-B Pro #39). The user picks
 * an action; only the chosen text is sent; the reply is a preview until the user applies it
 * (the editor applies text changes with Undo; tasks extracted from a note are created here, with
 * Undo).
 */
@HiltViewModel
class AiActionViewModel @Inject constructor(
    private val assistant: AiAssistant,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AiActionState())
    val state: StateFlow<AiActionState> = _state.asStateFlow()

    val ready: StateFlow<Boolean?> = assistant.ready.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var job: Job? = null

    private suspend fun language(): AssistantLanguage = settings.settings.first().language.assistantLanguage()

    fun run(action: AssistantAction, request: AssistantRequest, translateTo: AssistantLanguage? = null) {
        job?.cancel()
        val cut = AiContextBudget.truncate(request.text)
        _state.value = AiActionState(
            action = action,
            running = true,
            sentChars = cut.text.length + request.title.length,
            sentTokens = AiContextBudget.approxTokens(cut.text + request.title),
            truncated = cut.truncated,
        )
        job = viewModelScope.launch {
            val messages = AssistantPrompts.textAction(action.textAction(), language(), request.title, cut.text, translateTo)
            assistant.stream(messages, maxTokens = if (action.isList) LIST_TOKENS else TEXT_TOKENS).collect { event ->
                when (event) {
                    is AiStreamEvent.Delta -> _state.update { it.copy(output = it.output + event.text) }
                    is AiStreamEvent.Failed -> _state.update { it.copy(running = false, error = event.error) }
                    AiStreamEvent.Done -> _state.update { s ->
                        if (action.isList) {
                            val items = AssistantParsing.parseList(s.output)
                            s.copy(running = false, items = items, selected = if (action == AssistantAction.SUGGEST_TITLES) emptySet() else items.indices.toSet())
                        } else {
                            s.copy(running = false, output = AssistantParsing.plainText(s.output))
                        }
                    }
                }
            }
            _state.update { if (it.running) it.copy(running = false) else it }
        }
    }

    fun stop() {
        job?.cancel()
        _state.update { s -> s.copy(running = false, output = AssistantParsing.plainText(s.output)) }
    }

    fun toggle(index: Int) = _state.update { s ->
        s.copy(selected = if (index in s.selected) s.selected - index else s.selected + index)
    }

    /** Back to the list of actions. */
    fun reset() {
        job?.cancel()
        _state.value = AiActionState()
    }

    /** The picked items, in their order. */
    fun selectedItems(): List<String> = _state.value.let { s -> s.items.filterIndexed { i, _ -> i in s.selected } }

    /** Creates the picked items as tasks (planned for no date), keeping their ids for Undo. */
    fun createTasks(projectId: EntityId?) {
        val items = selectedItems()
        if (items.isEmpty() || _state.value.createdTasks.isNotEmpty()) return
        viewModelScope.launch {
            runCatchingSafely { items.map { tasks.save(Task(title = it, projectId = projectId)) } }
                .onSuccess { ids -> _state.update { it.copy(createdTasks = ids, tasksUndone = false) } }
                .onFailure { _state.update { it.copy(saveFailed = true) } }
        }
    }

    fun undoCreatedTasks() {
        val ids = _state.value.createdTasks
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatchingSafely { tasks.delete(ids) }.onSuccess { _state.update { it.copy(createdTasks = emptyList(), tasksUndone = true) } }
        }
    }

    private companion object {
        const val TEXT_TOKENS = 1500
        const val LIST_TOKENS = 600
    }
}
