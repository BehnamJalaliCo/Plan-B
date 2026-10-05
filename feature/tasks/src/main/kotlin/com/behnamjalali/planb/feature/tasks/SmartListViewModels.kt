package com.behnamjalali.planb.feature.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.common.todayFlow
import com.behnamjalali.planb.core.data.repository.ProjectRepository
import com.behnamjalali.planb.core.data.repository.SmartListRepository
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.NEW_ID
import com.behnamjalali.planb.core.model.PlannerIcon
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.SavedFilter
import com.behnamjalali.planb.core.model.SmartDateRange
import com.behnamjalali.planb.core.model.SmartFilter
import com.behnamjalali.planb.core.model.Tag
import com.behnamjalali.planb.core.model.TaskSort
import com.behnamjalali.planb.core.model.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Serializable smart-list editor state (survives process death). Enum values are stored by name. */
@Serializable
data class SmartListForm(
    val id: Long = NEW_ID,
    val name: String = "",
    val icon: String = PlannerIcon.STAR.key,
    val color: String = AccentColor.LAVENDER.key,
    val projectIds: List<Long> = emptyList(),
    val noProject: Boolean = false,
    val tagIds: List<Long> = emptyList(),
    val priorities: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val dateRange: String = SmartDateRange.ANY.name,
    val from: Long? = null,
    val to: Long? = null,
    val hasDeadline: Boolean? = null,
    val text: String = "",
    val sort: String = TaskSort.DUE_DATE.name,
    val sortOrder: Long = 0,
) {
    val canSave: Boolean get() = name.isNotBlank()

    fun toFilter() = SmartFilter(
        projectIds = projectIds.toSet(),
        noProject = noProject,
        tagIds = tagIds.toSet(),
        priorities = priorities.mapNotNull { n -> Priority.entries.firstOrNull { it.name == n } }.toSet(),
        statuses = statuses.mapNotNull { n -> TaskStatus.entries.firstOrNull { it.name == n } }.toSet(),
        dateRange = SmartDateRange.entries.firstOrNull { it.name == dateRange } ?: SmartDateRange.ANY,
        from = from?.let(LocalDate::ofEpochDay),
        to = to?.let(LocalDate::ofEpochDay),
        hasDeadline = hasDeadline,
        text = text,
        sort = TaskSort.entries.firstOrNull { it.name == sort } ?: TaskSort.DUE_DATE,
    )

    fun toSavedFilter() = SavedFilter(
        id = id,
        name = name.trim(),
        icon = PlannerIcon.fromKey(icon),
        color = AccentColor.fromKey(color),
        filter = toFilter(),
        sortOrder = sortOrder,
    )

    companion object {
        fun from(list: SavedFilter) = SmartListForm(
            id = list.id,
            name = list.name,
            icon = list.icon.key,
            color = list.color.key,
            projectIds = list.filter.projectIds.sorted(),
            noProject = list.filter.noProject,
            tagIds = list.filter.tagIds.sorted(),
            priorities = list.filter.priorities.map { it.name },
            statuses = list.filter.statuses.map { it.name },
            dateRange = list.filter.dateRange.name,
            from = list.filter.from?.toEpochDay(),
            to = list.filter.to?.toEpochDay(),
            hasDeadline = list.filter.hasDeadline,
            text = list.filter.text,
            sort = list.filter.sort.name,
            sortOrder = list.sortOrder,
        )
    }
}

sealed interface SmartListEvent {
    data class Saved(val id: EntityId) : SmartListEvent
    data object Deleted : SmartListEvent
    data object Failed : SmartListEvent
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SmartListEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val smartLists: SmartListRepository,
    projects: ProjectRepository,
    tasks: TaskRepository,
    time: TimeProvider,
) : ViewModel() {
    private val route = runCatching { savedState.toRoute<SmartListEditorRoute>() }.getOrDefault(SmartListEditorRoute())
    private val json = Json { ignoreUnknownKeys = true }
    private val needsLoad = !savedState.contains(KEY_FORM)

    val form: StateFlow<SmartListForm> = savedState.getStateFlow(KEY_FORM, "")
        .map(::decode)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SmartListForm())

    val isNew: Boolean get() = route.filterId == NEW_ID

    val projects: StateFlow<List<Project>> = projects.observeActiveProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: StateFlow<List<Tag>> = tasks.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** How many tasks the list would show now (live preview while editing). */
    val matchCount: StateFlow<Int?> = combine(form.debounce(200), time.todayFlow()) { f, today -> f.toFilter() to today }
        .flatMapLatest { (filter, today) -> smartLists.observeTasks(filter, today).map<_, Int?> { it.size } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = MutableSharedFlow<SmartListEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<SmartListEvent> = _events

    init {
        if (needsLoad) {
            viewModelScope.launch {
                val initial = if (isNew) SmartListForm() else smartLists.getList(route.filterId)?.let(SmartListForm::from) ?: SmartListForm()
                write(initial)
            }
        }
    }

    private fun decode(raw: String?): SmartListForm =
        if (raw.isNullOrBlank()) SmartListForm() else runCatching { json.decodeFromString(SmartListForm.serializer(), raw) }.getOrDefault(SmartListForm())

    private fun current(): SmartListForm = decode(savedState.get<String>(KEY_FORM))

    private fun write(value: SmartListForm) {
        savedState[KEY_FORM] = json.encodeToString(SmartListForm.serializer(), value)
    }

    fun update(transform: (SmartListForm) -> SmartListForm) = write(transform(current()))

    private var saving = false

    fun save() {
        val value = current()
        if (saving || !value.canSave) return
        saving = true
        viewModelScope.launch {
            runCatchingSafely { smartLists.save(value.toSavedFilter()) }
                .onSuccess { _events.tryEmit(SmartListEvent.Saved(it)) }
                .onFailure {
                    saving = false
                    _events.tryEmit(SmartListEvent.Failed)
                }
        }
    }

    fun delete() {
        if (isNew) return
        viewModelScope.launch {
            runCatchingSafely { smartLists.delete(route.filterId) }
                .onSuccess { _events.tryEmit(SmartListEvent.Deleted) }
                .onFailure { _events.tryEmit(SmartListEvent.Failed) }
        }
    }

    private companion object {
        const val KEY_FORM = "smart_list_form"
    }
}

@HiltViewModel
class SmartListsViewModel @Inject constructor(private val smartLists: SmartListRepository) : ViewModel() {
    val lists: StateFlow<List<SavedFilter>?> = smartLists.observeLists().map<_, List<SavedFilter>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun move(id: EntityId, delta: Int) {
        val ids = lists.value.orEmpty().map { it.id }.toMutableList()
        val index = ids.indexOf(id)
        val target = index + delta
        if (index < 0 || target !in ids.indices) return
        ids.removeAt(index)
        ids.add(target, id)
        viewModelScope.launch { runCatchingSafely { smartLists.reorder(ids) } }
    }

    fun delete(id: EntityId) {
        viewModelScope.launch { runCatchingSafely { smartLists.delete(id) } }
    }
}
