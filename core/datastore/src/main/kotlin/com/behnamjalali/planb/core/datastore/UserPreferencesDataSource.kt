package com.behnamjalali.planb.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.CalendarView
import com.behnamjalali.planb.core.model.DashboardConfig
import com.behnamjalali.planb.core.model.DashboardSection
import com.behnamjalali.planb.core.model.NumberFormatMode
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.model.ThemeMode
import com.behnamjalali.planb.core.model.UserSettings
import java.io.IOException
import java.time.DayOfWeek
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
    }

    val settings: Flow<UserSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::toSettings)

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { prefs ->
            val updated = transform(toSettings(prefs))
            write(prefs, updated)
        }
    }

    /** Raw key/value snapshot for backups (only known keys). */
    suspend fun export(): Map<String, String> {
        val prefs = dataStore.data.first()
        return prefs.asMap().entries.associate { (k, v) -> k.name to v.toString() }
    }

    suspend fun import(values: Map<String, String>) {
        val defaults = UserSettings()
        dataStore.edit { prefs ->
            val imported = toSettings(emptyPreferences(), values)
            // Device-specific onboarding state is not overwritten by a restore.
            write(prefs, imported.copy(onboardingCompleted = prefs[Keys.onboarding] ?: defaults.onboardingCompleted))
        }
    }

    private fun toSettings(prefs: Preferences): UserSettings = toSettings(prefs, null)

    private fun toSettings(prefs: Preferences, raw: Map<String, String>?): UserSettings {
        val d = UserSettings()
        fun str(key: Preferences.Key<String>): String? = raw?.get(key.name) ?: prefs[key]
        fun int(key: Preferences.Key<Int>): Int? = raw?.get(key.name)?.toIntOrNull() ?: prefs[key]
        fun bool(key: Preferences.Key<Boolean>): Boolean? = raw?.get(key.name)?.toBooleanStrictOrNull() ?: prefs[key]
        return UserSettings(
            language = str(Keys.language)?.let(AppLanguage::fromTag) ?: d.language,
            themeMode = str(Keys.theme).enumOr(d.themeMode),
            calendarSystemOverride = str(Keys.calendarSystem)?.let { runCatching { CalendarSystem.valueOf(it) }.getOrNull() },
            firstDayOfWeekOverride = int(Keys.firstDayOfWeek)?.takeIf { it in 1..7 }?.let(DayOfWeek::of),
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
    }
}

private inline fun <reified E : Enum<E>> String?.enumOr(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
