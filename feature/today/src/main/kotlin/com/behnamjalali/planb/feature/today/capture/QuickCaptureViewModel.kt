package com.behnamjalali.planb.feature.today.capture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.NoteRepository
import com.behnamjalali.planb.core.data.repository.ProjectRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.Tag
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.UserSettings
import com.behnamjalali.planb.core.nlp.QuickAddContext
import com.behnamjalali.planb.core.nlp.QuickAddKind
import com.behnamjalali.planb.core.nlp.QuickAddParser
import com.behnamjalali.planb.core.nlp.QuickAddProject
import com.behnamjalali.planb.core.nlp.QuickAddResult
import com.behnamjalali.planb.core.speech.appendDictation
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CaptureType { TASK, NOTE, EVENT, HABIT, PROJECT }

/** Result of a capture, so the UI can confirm and offer "Open". */
sealed interface CaptureEvent {
    data class Saved(val type: CaptureType, val id: EntityId) : CaptureEvent
    data object Failed : CaptureEvent
}

/**
 * Draft state lives in [SavedStateHandle] so text survives rotation and
 * process recreation while the sheet is open.
 */
@HiltViewModel
class QuickCaptureViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    private val notes: NoteRepository,
    private val eventRepository: EventRepository,
    private val habits: HabitRepository,
    private val projects: ProjectRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {
    val type: StateFlow<CaptureType> = savedState.getStateFlow(KEY_TYPE, CaptureType.TASK)
    val title: StateFlow<String> = savedState.getStateFlow(KEY_TITLE, "")
    val body: StateFlow<String> = savedState.getStateFlow(KEY_BODY, "")
    val dateEpoch: StateFlow<Long?> = savedState.getStateFlow(KEY_DATE, time.today().toEpochDay())
    val timeSeconds: StateFlow<Int?> = savedState.getStateFlow(KEY_TIME, null)
    val highPriority: StateFlow<Boolean> = savedState.getStateFlow(KEY_PRIORITY, false)

    private val _events = MutableSharedFlow<CaptureEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<CaptureEvent> = _events

    val today: LocalDate get() = time.today()

    /**
     * Plan-B Pro #1: interpretations the user removed (their words stay text), by
     * [com.behnamjalali.planb.core.nlp.QuickAddPart.key]. Kept with the draft across rotation.
     */
    private val dismissed: StateFlow<ArrayList<String>> = savedState.getStateFlow(KEY_DISMISSED, ArrayList())

    /** Smart input is on for Plan-B Pro users (the sheet passes the Pro state in). */
    private var smartInput = false
    private var latestSettings: UserSettings? = null
    private var latestProjects: List<QuickAddProject> = emptyList()

    private val _parsed = MutableStateFlow<QuickAddResult?>(null)

    /** What the text says (Pro #1), or null when smart input is off or nothing was recognized. */
    val parsed: StateFlow<QuickAddResult?> = _parsed.asStateFlow()

    init {
        // The calendar, first day of the week and project names feed the parser.
        viewModelScope.launch {
            settings.settings.collect {
                latestSettings = it
                reparse()
            }
        }
        viewModelScope.launch {
            projects.observeProjects(archived = false).collect { list ->
                latestProjects = list.map { QuickAddProject(it.project.id, it.project.title) }
                reparse()
            }
        }
    }

    fun setSmartInput(enabled: Boolean) {
        if (smartInput == enabled) return
        smartInput = enabled
        reparse()
    }

    /** Keeps the words of a recognized part as plain text. */
    fun dismissPart(key: String) {
        if (key in dismissed.value) return
        savedState[KEY_DISMISSED] = ArrayList(dismissed.value + key)
        reparse()
    }

    private fun parse(text: String, kind: CaptureType): QuickAddResult? {
        val s = latestSettings ?: return null
        if (!smartInput || text.isBlank()) return null
        val kinds = when (kind) {
            CaptureType.TASK -> QuickAddKind.entries.toSet()
            CaptureType.EVENT -> EVENT_KINDS
            else -> return null
        }
        val context = QuickAddContext(time.localNow(), s.calendarSystem, s.firstDayOfWeek, latestProjects)
        return QuickAddParser.parse(text, context, dismissed.value.toSet(), kinds).takeUnless { it.isEmpty }
    }

    private fun reparse() {
        _parsed.value = parse(title.value, type.value)
    }

    fun setType(value: CaptureType) {
        savedState[KEY_TYPE] = value
        if (value == CaptureType.EVENT && dateEpoch.value == null) savedState[KEY_DATE] = today.toEpochDay()
        reparse()
    }
    fun setTitle(value: String) {
        savedState[KEY_TITLE] = value
        reparse()
    }
    fun setBody(value: String) { savedState[KEY_BODY] = value }

    /**
     * Plan-B Pro #40: dictated text joins the title and is read like typed text, so «فردا ساعت
     * ۵ عصر جلسه با علی» spoken becomes a task tomorrow at 17:00. A note's dictation goes to its
     * body once the title is set.
     */
    fun applyDictation(text: String) {
        if (type.value == CaptureType.NOTE && title.value.isNotBlank()) {
            setBody(appendDictation(body.value, text))
        } else {
            setTitle(appendDictation(title.value, text))
        }
    }

    /** A date picked by hand replaces one read from the text (whose words stay text). */
    fun setDate(value: LocalDate?) {
        dismissKinds(QuickAddKind.DATE)
        savedState[KEY_DATE_PICKED] = true
        savedState[KEY_DATE] = value?.toEpochDay()
    }

    private fun dismissKinds(vararg kinds: QuickAddKind) {
        val keys = parsed.value?.parts.orEmpty().filter { it.kind in kinds }.map { it.key }
        if (keys.isEmpty()) return
        savedState[KEY_DISMISSED] = ArrayList((dismissed.value + keys).distinct())
        reparse()
    }

    /**
     * Called each time the sheet opens. This ViewModel outlives the sheet, so a default date
     * computed earlier (e.g. yesterday) is moved to today unless the user picked one.
     */
    fun onOpened() {
        if (savedState.get<Boolean>(KEY_DATE_PICKED) != true) {
            savedState[KEY_DATE] = today.toEpochDay()
        }
    }
    fun setTime(value: LocalTime?) {
        dismissKinds(QuickAddKind.TIME)
        savedState[KEY_TIME] = value?.toSecondOfDay()
    }
    fun setHighPriority(value: Boolean) {
        dismissKinds(QuickAddKind.PRIORITY)
        savedState[KEY_PRIORITY] = value
    }

    val canSave: Boolean get() = title.value.isNotBlank() || (type.value == CaptureType.NOTE && body.value.isNotBlank())

    /** True while a save runs; a second tap on Save must not capture the item twice. */
    private var saving = false

    fun save(defaultNotebookTitle: String) {
        if (saving || !canSave) return
        saving = true
        val kind = type.value
        // Read the text once more, so a save right after typing never uses a stale reading.
        val smart = parse(title.value, kind)
        // A text made only of recognized parts keeps its words as the title.
        val text = smart?.title?.takeIf { it.isNotBlank() } ?: title.value.trim()
        val date = smart?.date ?: dateEpoch.value?.let(LocalDate::ofEpochDay)
        val at = smart?.time ?: timeSeconds.value?.let { LocalTime.ofSecondOfDay(it.toLong()) }
        viewModelScope.launch {
            val result = runCatchingSafely {
                when (kind) {
                    CaptureType.TASK -> {
                        val reminder = smart?.reminderMinutesBefore ?: if (at != null) settings.current().defaultReminderMinutes else null
                        tasks.save(
                            Task(
                                title = text,
                                dueDate = date,
                                dueTime = if (date != null) at else null,
                                reminderOffsetMinutes = reminder,
                                priority = smart?.priority ?: if (highPriority.value) Priority.HIGH else Priority.NONE,
                                recurrence = smart?.recurrence,
                                deadline = smart?.deadline,
                                estimatedMinutes = smart?.durationMinutes,
                                projectId = smart?.project?.id,
                                tags = smart?.tags.orEmpty().map { Tag(name = it) },
                            ),
                        )
                    }
                    CaptureType.NOTE -> {
                        val notebook = notes.ensureDefaultNotebook(defaultNotebookTitle)
                        val blocks = body.value.lines().filter { it.isNotBlank() }
                            .map { NoteBlock(UUID.randomUUID().toString(), text = it) }
                        notes.saveNote(Note(notebookId = notebook, title = text, document = NoteDocument(blocks = blocks)))
                    }
                    CaptureType.EVENT -> eventRepository.save(
                        CalendarEvent(
                            title = text,
                            date = date ?: today,
                            startTime = at,
                            endTime = at?.plusMinutes((smart?.durationMinutes ?: DEFAULT_EVENT_MINUTES).toLong())?.takeIf { it > at },
                            allDay = at == null,
                            recurrence = smart?.recurrence,
                            reminderOffsetMinutes = smart?.reminderMinutesBefore,
                        ),
                    )
                    CaptureType.HABIT -> habits.save(Habit(title = text, startDate = today))
                    CaptureType.PROJECT -> projects.save(Project(title = text))
                }
            }
            result.onSuccess { id ->
                clear()
                _events.tryEmit(CaptureEvent.Saved(kind, id))
            }.onFailure { _events.tryEmit(CaptureEvent.Failed) }
            saving = false
        }
    }

    fun clear() {
        savedState[KEY_TITLE] = ""
        savedState[KEY_BODY] = ""
        savedState[KEY_TIME] = null
        savedState[KEY_PRIORITY] = false
        savedState[KEY_DATE] = time.today().toEpochDay()
        savedState[KEY_DATE_PICKED] = false
        savedState[KEY_DISMISSED] = ArrayList<String>()
        reparse()
    }

    private companion object {
        const val KEY_TYPE = "capture_type"
        const val KEY_TITLE = "capture_title"
        const val KEY_BODY = "capture_body"
        const val KEY_DATE = "capture_date"
        const val KEY_TIME = "capture_time"
        const val KEY_PRIORITY = "capture_priority"
        const val KEY_DATE_PICKED = "capture_date_picked"
        const val KEY_DISMISSED = "capture_dismissed_parts"
        const val DEFAULT_EVENT_MINUTES = 60

        /** Events have no tags, project, priority or deadline: those words stay in the title. */
        val EVENT_KINDS = setOf(QuickAddKind.DATE, QuickAddKind.TIME, QuickAddKind.RECURRENCE, QuickAddKind.DURATION, QuickAddKind.REMINDER)
    }
}
