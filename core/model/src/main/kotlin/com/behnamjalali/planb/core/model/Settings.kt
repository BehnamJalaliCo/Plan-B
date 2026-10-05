package com.behnamjalali.planb.core.model

import java.time.DayOfWeek

enum class AppLanguage(val tag: String) {
    PERSIAN("fa"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { tag != null && tag.startsWith(it.tag) } ?: PERSIAN
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The app's color palette. [CLASSIC] is the free brand palette; the others are Plan-B Pro
 * themes (#33). Stored by [key]; unknown keys read as [CLASSIC].
 */
enum class ColorTheme(val key: String) {
    CLASSIC("classic"),
    OCEAN("ocean"),
    FOREST("forest"),
    SUNSET("sunset"),
    BLOSSOM("blossom"),
    MIDNIGHT("midnight"),
    ;

    val isPremium: Boolean get() = this != CLASSIC

    companion object {
        fun fromKey(key: String?): ColorTheme = entries.firstOrNull { it.key == key } ?: CLASSIC
    }
}

/** Launcher icon variants (Plan-B Pro, #33). The enabled launcher alias is the source of truth. */
enum class AppIcon(val key: String) {
    CLASSIC("classic"),
    OCEAN("ocean"),
    SUNSET("sunset"),
    FOREST("forest"),
    MIDNIGHT("midnight"),
}

enum class NumberFormatMode {
    /** Persian digits for Persian UI, Latin digits for English UI. */
    AUTO,
    PERSIAN,
    LATIN,
}

enum class CalendarView { DAY, WEEK, MONTH, AGENDA }

enum class DashboardSection(val key: String) {
    SUMMARY("summary"),
    TIMELINE("timeline"),
    TASKS("tasks"),
    UPCOMING("upcoming"),
    HABITS("habits"),
    FOCUS("focus"),
    PROJECTS("projects"),
    NOTES("notes"),
    ;

    companion object {
        val DEFAULT_ORDER: List<DashboardSection> = entries.toList()
        fun fromKey(key: String): DashboardSection? = entries.firstOrNull { it.key == key }
    }
}

data class DashboardConfig(
    val order: List<DashboardSection> = DashboardSection.DEFAULT_ORDER,
    val hidden: Set<DashboardSection> = emptySet(),
) {
    val visibleSections: List<DashboardSection> get() = order.filterNot { it in hidden }
}

/**
 * All user preferences. [calendarSystem] and [firstDayOfWeek] are nullable:
 * null means "follow the language default" (Jalali + Saturday for Persian).
 */
data class UserSettings(
    val language: AppLanguage = AppLanguage.PERSIAN,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val calendarSystemOverride: CalendarSystem? = null,
    val firstDayOfWeekOverride: DayOfWeek? = null,
    val numberFormat: NumberFormatMode = NumberFormatMode.AUTO,
    val defaultReminderMinutes: Int = 15,
    val animationsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val dashboard: DashboardConfig = DashboardConfig(),
    val defaultTaskView: TaskView = TaskView.TODAY,
    val defaultCalendarView: CalendarView = CalendarView.MONTH,
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val onboardingCompleted: Boolean = false,
    /** Chosen palette; only applied while the user has Plan-B Pro (see [effectiveColorTheme]). */
    val colorTheme: ColorTheme = ColorTheme.CLASSIC,
    /**
     * The user picked [language] on the first-run language screen (device state, like
     * [onboardingCompleted]). Installs that finished onboarding before the screen existed
     * count as having chosen.
     */
    val languageChosen: Boolean = false,
    /** Working hours and ritual reminders (Plan-B Pro #5, #8). */
    val dayPlan: DayPlanSettings = DayPlanSettings(),
    /** Today's top tasks and the days the rituals were done (Plan-B Pro #8). */
    val rituals: RitualState = RitualState(),
) {
    /** The palette to draw with: premium themes fall back to the classic one without Pro. */
    fun effectiveColorTheme(isPro: Boolean): ColorTheme = if (isPro || !colorTheme.isPremium) colorTheme else ColorTheme.CLASSIC

    val calendarSystem: CalendarSystem
        get() = calendarSystemOverride
            ?: if (language == AppLanguage.PERSIAN) CalendarSystem.JALALI else CalendarSystem.GREGORIAN

    val firstDayOfWeek: DayOfWeek
        get() = firstDayOfWeekOverride
            ?: if (calendarSystem == CalendarSystem.JALALI) DayOfWeek.SATURDAY else DayOfWeek.MONDAY

    val usePersianDigits: Boolean
        get() = when (numberFormat) {
            NumberFormatMode.AUTO -> language == AppLanguage.PERSIAN
            NumberFormatMode.PERSIAN -> true
            NumberFormatMode.LATIN -> false
        }
}
