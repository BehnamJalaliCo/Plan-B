package com.behnamjalali.planb.feature.journal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.common.todayFlow
import com.behnamjalali.planb.core.data.repository.JournalRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.JournalPage
import com.behnamjalali.planb.core.model.JournalPrompts
import com.behnamjalali.planb.core.model.JournalSettings
import com.behnamjalali.planb.core.model.MoodCalendar
import com.behnamjalali.planb.core.model.MoodEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** The daily journal (Plan-B Pro #25). */
@Serializable
data object JournalRoute

/** The mood calendar and insights (Plan-B Pro #25). */
@Serializable
data object MoodCalendarRoute

data class JournalUi(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.MIN,
    /** Today's page, once written. */
    val page: JournalPage? = null,
    /** The prompt key to show today (the page's own once it exists). */
    val promptKey: String = JournalPrompts.builtInKeys.first(),
    val mood: MoodEntry? = null,
    val tags: List<String> = emptyList(),
    val streak: Int = 0,
    val recent: List<JournalPage> = emptyList(),
    val settings: JournalSettings = JournalSettings(),
)

/** Strings the data layer needs in the user's language: the notebook, the page title and the prompt. */
data class JournalTexts(val notebook: String, val pageTitle: String, val prompt: String?)

sealed interface JournalEvent {
    data class OpenNote(val id: EntityId) : JournalEvent
    data object Failed : JournalEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JournalViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val journal: JournalRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {
    /** "Another prompt" steps along the rotation; kept across rotation and process death. */
    private val shift = savedState.getStateFlow(KEY_SHIFT, 0)

    private val today = time.todayFlow()

    private val pages = today.flatMapLatest { day -> journal.observePages(day.minusDays(RECENT_DAYS), day) }
    private val dates = today.flatMapLatest { day -> journal.observePageDates(day.minusDays(STREAK_DAYS), day) }
    private val todayPage = combine(today, pages) { day, list -> list.firstOrNull { it.date == day } }
    private val pageExtras = todayPage.flatMapLatest { page ->
        if (page == null) {
            flowOf(null to emptyList())
        } else {
            combine(journal.observeMoods(page.date, page.date), journal.observeTags(page.noteId)) { moods, tags ->
                moods.firstOrNull { it.noteId == page.noteId } to tags
            }
        }
    }

    val state: StateFlow<JournalUi> = combine(
        combine(today, pages, dates, ::Triple),
        todayPage,
        pageExtras,
        settings.settings.map { it.journal },
        shift,
    ) { (day, list, days), page, (mood, tags), journalSettings, steps ->
        JournalUi(
            loading = false,
            today = day,
            page = page,
            promptKey = page?.promptId?.takeIf { isKnownPrompt(it, journalSettings) } ?: JournalPrompts.forDate(day, journalSettings.customPrompts, steps),
            mood = mood,
            tags = tags,
            streak = MoodCalendar.currentStreak(days, day),
            recent = list.filter { it.date != day },
            settings = journalSettings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUi())

    private fun isKnownPrompt(key: String, s: JournalSettings): Boolean =
        JournalPrompts.builtInNumber(key) != null || JournalPrompts.customText(key, s.customPrompts) != null

    private val _events = MutableSharedFlow<JournalEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<JournalEvent> = _events

    fun anotherPrompt() {
        savedState[KEY_SHIFT] = shift.value + 1
    }

    private suspend fun ensurePage(texts: JournalTexts): EntityId {
        val s = state.value
        s.page?.let { return it.noteId }
        return journal.openPage(s.today, texts.notebook, texts.pageTitle, s.promptKey, texts.prompt)
    }

    /** Opens today's page in the editor, creating it (with the prompt) first. */
    fun write(texts: JournalTexts) = launch { _events.tryEmit(JournalEvent.OpenNote(ensurePage(texts))) }

    fun setMood(texts: JournalTexts, mood: Int?) = launch {
        val id = ensurePage(texts)
        val current = state.value.mood
        journal.setPageMood(state.value.today, id, mood, current?.energy)
    }

    fun setEnergy(texts: JournalTexts, energy: Int?) = launch {
        val id = ensurePage(texts)
        val current = state.value.mood
        journal.setPageMood(state.value.today, id, current?.mood, energy)
    }

    fun setTags(texts: JournalTexts, raw: String) = launch {
        val id = ensurePage(texts)
        journal.setTags(id, raw.split(',', '،').map { it.trim() })
    }

    fun setReminder(on: Boolean) = launch { settings.update { it.copy(journal = it.journal.copy(reminder = on)) } }

    fun setReminderTime(at: LocalTime) = launch { settings.update { it.copy(journal = it.journal.copy(reminderTime = at)) } }

    fun addPrompt(text: String) = launch {
        val clean = text.replace('\n', ' ').trim().take(JournalSettings.MAX_PROMPT_LENGTH)
        if (clean.isEmpty()) return@launch
        settings.update {
            val prompts = (it.journal.customPrompts + clean).distinct().take(JournalSettings.MAX_CUSTOM_PROMPTS)
            it.copy(journal = it.journal.copy(customPrompts = prompts))
        }
    }

    fun removePrompt(text: String) = launch {
        settings.update { it.copy(journal = it.journal.copy(customPrompts = it.journal.customPrompts - text)) }
    }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch {
        runCatchingSafely { block() }.onFailure { _events.tryEmit(JournalEvent.Failed) }
    }

    private companion object {
        const val KEY_SHIFT = "journal_prompt_shift"
        const val RECENT_DAYS = 60L
        const val STREAK_DAYS = 800L
    }
}
