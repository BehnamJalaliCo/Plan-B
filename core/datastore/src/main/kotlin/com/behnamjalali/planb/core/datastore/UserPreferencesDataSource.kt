package com.behnamjalali.planb.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.CalendarDecorations
import com.behnamjalali.planb.core.model.CalendarSyncSettings
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.CalendarView
import com.behnamjalali.planb.core.model.ColorTheme
import com.behnamjalali.planb.core.model.DashboardConfig
import com.behnamjalali.planb.core.model.DashboardSection
import com.behnamjalali.planb.core.model.DayPlanSettings
import com.behnamjalali.planb.core.model.JournalSettings
import com.behnamjalali.planb.core.model.NumberFormatMode
import com.behnamjalali.planb.core.model.RitualState
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.model.ThemeMode
import com.behnamjalali.planb.core.model.UserSettings
import com.behnamjalali.planb.core.model.WritingSettings
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Preferences are stored as individual keys with stable names. Every read falls
 * back to the default for a missing or unparsable key, so adding new keys in
 * future versions never resets existing preferences.
 */
@Singleton
class UserPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    internal object Keys {
        val language = stringPreferencesKey("language")
        val theme = stringPreferencesKey("theme")
        val calendarSystem = stringPreferencesKey("calendar_system")
        val firstDayOfWeek = intPreferencesKey("first_day_of_week")
        val numberFormat = stringPreferencesKey("number_format")
        val defaultReminder = intPreferencesKey("default_reminder_minutes")
        val animations = booleanPreferencesKey("animations_enabled")
        val haptics = booleanPreferencesKey("haptics_enabled")
        val dashboardOrder = stringPreferencesKey("dashboard_order")
        val dashboardHidden = stringPreferencesKey("dashboard_hidden")
        val defaultTaskView = stringPreferencesKey("default_task_view")
        val defaultCalendarView = stringPreferencesKey("default_calendar_view")
        val focusMinutes = intPreferencesKey("focus_minutes")
        val breakMinutes = intPreferencesKey("short_break_minutes")
        val onboarding = booleanPreferencesKey("onboarding_completed")
        val languageChosen = booleanPreferencesKey("language_chosen")
        val colorTheme = stringPreferencesKey("color_theme")
        val calendarHolidays = booleanPreferencesKey("calendar_holidays")
        val calendarOccasions = booleanPreferencesKey("calendar_occasions")
        val calendarHijri = booleanPreferencesKey("calendar_hijri_date")

        /** Device calendar sync (Plan-B Pro #3): device state, never exported. */
        val calendarSyncEnabled = booleanPreferencesKey("calendar_sync_enabled")
        val calendarSyncVisible = stringPreferencesKey("calendar_sync_visible_ids")
        val calendarSyncTarget = longPreferencesKey("calendar_sync_target_id")
        val calendarSyncLast = longPreferencesKey("calendar_sync_last_at")
        val calendarSyncFailed = booleanPreferencesKey("calendar_sync_failed")

        // Plan-B Pro smart day (#5 working hours, #8 rituals). Times are minutes of the day.
        val workStart = intPreferencesKey("day_plan_work_start")
        val workEnd = intPreferencesKey("day_plan_work_end")
        val lunchEnabled = booleanPreferencesKey("day_plan_lunch_enabled")
        val lunchStart = intPreferencesKey("day_plan_lunch_start")
        val lunchEnd = intPreferencesKey("day_plan_lunch_end")
        val planBuffer = intPreferencesKey("day_plan_buffer_minutes")
        val morningReminder = booleanPreferencesKey("ritual_morning_reminder")
        val morningTime = intPreferencesKey("ritual_morning_time")
        val eveningReminder = booleanPreferencesKey("ritual_evening_reminder")
        val eveningTime = intPreferencesKey("ritual_evening_time")
        val focusDate = longPreferencesKey("ritual_focus_date")
        val focusTasks = stringPreferencesKey("ritual_focus_tasks")
        val morningDone = longPreferencesKey("ritual_morning_done")
        val eveningDone = longPreferencesKey("ritual_evening_done")

        // Plan-B Pro notes: writing mode word goal and streak (#24), journal (#25).
        val writingGoal = intPreferencesKey("writing_daily_goal")
        val writingTypewriter = booleanPreferencesKey("writing_typewriter")
        val writingDate = longPreferencesKey("writing_progress_date")
        val writingWords = intPreferencesKey("writing_words_today")
        val writingStreak = intPreferencesKey("writing_streak")
        val writingGoalDate = longPreferencesKey("writing_goal_reached_on")
        val journalReminder = booleanPreferencesKey("journal_reminder")
        val journalTime = intPreferencesKey("journal_reminder_time")
        val journalPrompts = stringPreferencesKey("journal_custom_prompts")

        /** Normalizer version the search index was built with (device state, never exported). */
        val searchIndexVersion = intPreferencesKey("search_index_version")
    }

    /**
     * Unreadable storage reads as "defaults" instead of failing every collector; any other
     * error is a bug and is propagated rather than silently ending the flow.
     */
    private val data: Flow<Preferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val settings: Flow<UserSettings> = data.map(::toSettings)

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { prefs ->
            val updated = transform(toSettings(prefs))
            write(prefs, updated)
        }
    }

    /** Version of the search normalizer the full-text index was last built with (0 = unknown). */
    suspend fun searchIndexVersion(): Int = data.first()[Keys.searchIndexVersion] ?: 0

    suspend fun setSearchIndexVersion(version: Int) {
        dataStore.edit { it[Keys.searchIndexVersion] = version }
    }

    /** Device calendar sync settings (Plan-B Pro #3); device-only, never in a backup. */
    val calendarSync: Flow<CalendarSyncSettings> = data.map(::calendarSyncOf)

    suspend fun updateCalendarSync(transform: (CalendarSyncSettings) -> CalendarSyncSettings) {
        dataStore.edit { prefs ->
            val s = transform(calendarSyncOf(prefs))
            prefs[Keys.calendarSyncEnabled] = s.enabled
            prefs[Keys.calendarSyncVisible] = s.visibleCalendarIds.sorted().joinToString(",")
            s.targetCalendarId?.let { prefs[Keys.calendarSyncTarget] = it } ?: prefs.remove(Keys.calendarSyncTarget)
            prefs[Keys.calendarSyncLast] = s.lastSyncAt
            prefs[Keys.calendarSyncFailed] = s.lastSyncFailed
        }
    }

    private fun calendarSyncOf(prefs: Preferences) = CalendarSyncSettings(
        enabled = prefs[Keys.calendarSyncEnabled] ?: false,
        visibleCalendarIds = prefs[Keys.calendarSyncVisible].orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.toSet(),
        targetCalendarId = prefs[Keys.calendarSyncTarget]?.takeIf { it > 0 },
        lastSyncAt = prefs[Keys.calendarSyncLast] ?: 0,
        lastSyncFailed = prefs[Keys.calendarSyncFailed] ?: false,
    )

    /** Raw key/value snapshot for backups; device-specific state (onboarding, index version) is left out. */
    suspend fun export(): Map<String, String> {
        val prefs = data.first()
        val values = prefs.asMap().entries
            .filter { (k, _) -> k.name !in DEVICE_KEYS }
            .associate { (k, v) -> k.name to v.toString() }
        // "Follow the language" is stored as an absent key; export it explicitly so a restore
        // resets an override instead of keeping it.
        return values + mapOf(
            Keys.calendarSystem.name to (values[Keys.calendarSystem.name] ?: AUTO),
            Keys.firstDayOfWeek.name to (values[Keys.firstDayOfWeek.name] ?: "0"),
        )
    }

    suspend fun import(values: Map<String, String>) {
        dataStore.edit { prefs ->
            // Keys missing from the backup keep their current values; malformed ones fall back
            // to the current value too.
            val current = toSettings(prefs)
            val imported = toSettings(prefs, values, fallback = current)
            // Device-specific onboarding state is not overwritten by a restore.
            write(prefs, imported.copy(onboardingCompleted = current.onboardingCompleted, languageChosen = current.languageChosen))
        }
    }

    private fun toSettings(prefs: Preferences): UserSettings = toSettings(prefs, null)

    /**
     * Reads settings from [prefs], overridden by [raw] backup values when given. Missing or
     * malformed values resolve to [fallback] (defaults, or the current settings on import).
     */
    private fun toSettings(prefs: Preferences, raw: Map<String, String>?, fallback: UserSettings = UserSettings()): UserSettings {
        val d = fallback
        fun str(key: Preferences.Key<String>): String? = raw?.get(key.name) ?: prefs[key]
        fun int(key: Preferences.Key<Int>): Int? = raw?.get(key.name)?.toIntOrNull() ?: prefs[key]
        fun bool(key: Preferences.Key<Boolean>): Boolean? = raw?.get(key.name)?.toBooleanStrictOrNull() ?: prefs[key]
        fun long(key: Preferences.Key<Long>): Long? = raw?.get(key.name)?.toLongOrNull() ?: prefs[key]
        fun time(key: Preferences.Key<Int>, default: LocalTime): LocalTime =
            int(key)?.takeIf { it in 0 until MINUTES_PER_DAY }?.let { LocalTime.of(it / 60, it % 60) } ?: default
        fun date(key: Preferences.Key<Long>, default: LocalDate?): LocalDate? =
            long(key)?.takeIf { it in DATE_RANGE }?.let(LocalDate::ofEpochDay) ?: default
        val plan = d.dayPlan
        return UserSettings(
            language = str(Keys.language)?.let { tag -> AppLanguage.entries.firstOrNull { tag.startsWith(it.tag) } } ?: d.language,
            themeMode = str(Keys.theme).enumOr(d.themeMode),
            calendarSystemOverride = when (val v = str(Keys.calendarSystem)) {
                null -> d.calendarSystemOverride
                AUTO -> null
                else -> CalendarSystem.entries.firstOrNull { it.name == v } ?: d.calendarSystemOverride
            },
            firstDayOfWeekOverride = when (val v = int(Keys.firstDayOfWeek)) {
                null -> d.firstDayOfWeekOverride
                0 -> null
                in 1..7 -> DayOfWeek.of(v)
                else -> d.firstDayOfWeekOverride
            },
            numberFormat = str(Keys.numberFormat).enumOr(d.numberFormat),
            defaultReminderMinutes = int(Keys.defaultReminder)?.coerceIn(0, 7 * 24 * 60) ?: d.defaultReminderMinutes,
            animationsEnabled = bool(Keys.animations) ?: d.animationsEnabled,
            hapticsEnabled = bool(Keys.haptics) ?: d.hapticsEnabled,
            dashboard = DashboardConfig(
                order = str(Keys.dashboardOrder)?.let(::parseOrder) ?: d.dashboard.order,
                hidden = str(Keys.dashboardHidden)?.split(',')?.mapNotNull(DashboardSection::fromKey)?.toSet()
                    ?: d.dashboard.hidden,
            ),
            defaultTaskView = str(Keys.defaultTaskView).enumOr(d.defaultTaskView),
            defaultCalendarView = str(Keys.defaultCalendarView).enumOr(d.defaultCalendarView),
            focusMinutes = int(Keys.focusMinutes)?.coerceIn(1, 180) ?: d.focusMinutes,
            shortBreakMinutes = int(Keys.breakMinutes)?.coerceIn(1, 60) ?: d.shortBreakMinutes,
            onboardingCompleted = bool(Keys.onboarding) ?: d.onboardingCompleted,
            colorTheme = str(Keys.colorTheme)?.let { key -> ColorTheme.entries.firstOrNull { it.key == key } } ?: d.colorTheme,
            // Installs from before the first-run language screen: finishing onboarding implies a choice.
            languageChosen = prefs[Keys.languageChosen] ?: prefs[Keys.onboarding] ?: d.languageChosen,
            calendarDecorations = CalendarDecorations(
                holidays = bool(Keys.calendarHolidays) ?: d.calendarDecorations.holidays,
                occasions = bool(Keys.calendarOccasions) ?: d.calendarDecorations.occasions,
                hijriDate = bool(Keys.calendarHijri) ?: d.calendarDecorations.hijriDate,
            ),
            dayPlan = DayPlanSettings(
                workStart = time(Keys.workStart, plan.workStart),
                workEnd = time(Keys.workEnd, plan.workEnd),
                lunchEnabled = bool(Keys.lunchEnabled) ?: plan.lunchEnabled,
                lunchStart = time(Keys.lunchStart, plan.lunchStart),
                lunchEnd = time(Keys.lunchEnd, plan.lunchEnd),
                bufferMinutes = int(Keys.planBuffer)?.coerceIn(0, MAX_BUFFER) ?: plan.bufferMinutes,
                morningReminder = bool(Keys.morningReminder) ?: plan.morningReminder,
                morningTime = time(Keys.morningTime, plan.morningTime),
                eveningReminder = bool(Keys.eveningReminder) ?: plan.eveningReminder,
                eveningTime = time(Keys.eveningTime, plan.eveningTime),
            ),
            rituals = RitualState(
                focusDate = date(Keys.focusDate, d.rituals.focusDate),
                focusTaskIds = str(Keys.focusTasks)?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.distinct()
                    ?.take(RitualState.TOP_COUNT) ?: d.rituals.focusTaskIds,
                morningDoneOn = date(Keys.morningDone, d.rituals.morningDoneOn),
                eveningDoneOn = date(Keys.eveningDone, d.rituals.eveningDoneOn),
            ),
            writing = WritingSettings(
                dailyGoal = int(Keys.writingGoal)?.coerceIn(0, WritingSettings.MAX_GOAL) ?: d.writing.dailyGoal,
                typewriter = bool(Keys.writingTypewriter) ?: d.writing.typewriter,
                progressDate = date(Keys.writingDate, d.writing.progressDate),
                wordsToday = int(Keys.writingWords)?.coerceIn(0, WritingSettings.MAX_WORDS_PER_DAY) ?: d.writing.wordsToday,
                streak = int(Keys.writingStreak)?.coerceIn(0, MAX_STREAK) ?: d.writing.streak,
                goalReachedOn = date(Keys.writingGoalDate, d.writing.goalReachedOn),
            ),
            journal = JournalSettings(
                reminder = bool(Keys.journalReminder) ?: d.journal.reminder,
                reminderTime = time(Keys.journalTime, d.journal.reminderTime),
                customPrompts = str(Keys.journalPrompts)?.let(::parsePrompts) ?: d.journal.customPrompts,
            ),
        )
    }

    /** One prompt per line; blank, overlong and duplicate lines are dropped. */
    private fun parsePrompts(raw: String): List<String> = raw.split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.length <= JournalSettings.MAX_PROMPT_LENGTH }
        .distinct()
        .take(JournalSettings.MAX_CUSTOM_PROMPTS)

    private companion object {
        const val AUTO = "AUTO"
        const val MINUTES_PER_DAY = 24 * 60
        const val MAX_BUFFER = 120
        const val MAX_STREAK = 100_000

        /** Plausible epoch days (years 1900–2200); anything else is treated as malformed. */
        val DATE_RANGE = -25_567L..84_000L
        val DEVICE_KEYS = setOf(
            Keys.onboarding.name, Keys.languageChosen.name, Keys.searchIndexVersion.name,
            Keys.calendarSyncEnabled.name, Keys.calendarSyncVisible.name, Keys.calendarSyncTarget.name,
            Keys.calendarSyncLast.name, Keys.calendarSyncFailed.name,
        )
    }

    /** Known sections in saved order; sections added in newer versions are appended. */
    private fun parseOrder(raw: String): List<DashboardSection> {
        val saved = raw.split(',').mapNotNull(DashboardSection::fromKey).distinct()
        return saved + DashboardSection.DEFAULT_ORDER.filterNot { it in saved }
    }

    private fun write(prefs: androidx.datastore.preferences.core.MutablePreferences, s: UserSettings) {
        prefs[Keys.language] = s.language.tag
        prefs[Keys.theme] = s.themeMode.name
        s.calendarSystemOverride?.let { prefs[Keys.calendarSystem] = it.name } ?: prefs.remove(Keys.calendarSystem)
        s.firstDayOfWeekOverride?.let { prefs[Keys.firstDayOfWeek] = it.value } ?: prefs.remove(Keys.firstDayOfWeek)
        prefs[Keys.numberFormat] = s.numberFormat.name
        prefs[Keys.defaultReminder] = s.defaultReminderMinutes
        prefs[Keys.animations] = s.animationsEnabled
        prefs[Keys.haptics] = s.hapticsEnabled
        prefs[Keys.dashboardOrder] = s.dashboard.order.joinToString(",") { it.key }
        prefs[Keys.dashboardHidden] = s.dashboard.hidden.joinToString(",") { it.key }
        prefs[Keys.defaultTaskView] = s.defaultTaskView.name
        prefs[Keys.defaultCalendarView] = s.defaultCalendarView.name
        prefs[Keys.focusMinutes] = s.focusMinutes
        prefs[Keys.breakMinutes] = s.shortBreakMinutes
        prefs[Keys.onboarding] = s.onboardingCompleted
        prefs[Keys.colorTheme] = s.colorTheme.key
        prefs[Keys.languageChosen] = s.languageChosen
        prefs[Keys.calendarHolidays] = s.calendarDecorations.holidays
        prefs[Keys.calendarOccasions] = s.calendarDecorations.occasions
        prefs[Keys.calendarHijri] = s.calendarDecorations.hijriDate
        val plan = s.dayPlan
        prefs[Keys.workStart] = plan.workStart.minutes()
        prefs[Keys.workEnd] = plan.workEnd.minutes()
        prefs[Keys.lunchEnabled] = plan.lunchEnabled
        prefs[Keys.lunchStart] = plan.lunchStart.minutes()
        prefs[Keys.lunchEnd] = plan.lunchEnd.minutes()
        prefs[Keys.planBuffer] = plan.bufferMinutes
        prefs[Keys.morningReminder] = plan.morningReminder
        prefs[Keys.morningTime] = plan.morningTime.minutes()
        prefs[Keys.eveningReminder] = plan.eveningReminder
        prefs[Keys.eveningTime] = plan.eveningTime.minutes()
        val rituals = s.rituals
        rituals.focusDate?.let { prefs[Keys.focusDate] = it.toEpochDay() } ?: prefs.remove(Keys.focusDate)
        prefs[Keys.focusTasks] = rituals.focusTaskIds.joinToString(",")
        rituals.morningDoneOn?.let { prefs[Keys.morningDone] = it.toEpochDay() } ?: prefs.remove(Keys.morningDone)
        rituals.eveningDoneOn?.let { prefs[Keys.eveningDone] = it.toEpochDay() } ?: prefs.remove(Keys.eveningDone)
        val writing = s.writing
        prefs[Keys.writingGoal] = writing.dailyGoal
        prefs[Keys.writingTypewriter] = writing.typewriter
        writing.progressDate?.let { prefs[Keys.writingDate] = it.toEpochDay() } ?: prefs.remove(Keys.writingDate)
        prefs[Keys.writingWords] = writing.wordsToday
        prefs[Keys.writingStreak] = writing.streak
        writing.goalReachedOn?.let { prefs[Keys.writingGoalDate] = it.toEpochDay() } ?: prefs.remove(Keys.writingGoalDate)
        prefs[Keys.journalReminder] = s.journal.reminder
        prefs[Keys.journalTime] = s.journal.reminderTime.minutes()
        prefs[Keys.journalPrompts] = s.journal.customPrompts.joinToString("\n") { it.replace('\n', ' ').replace('\r', ' ').trim() }
    }

    private fun LocalTime.minutes(): Int = hour * 60 + minute
}

private inline fun <reified E : Enum<E>> String?.enumOr(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
