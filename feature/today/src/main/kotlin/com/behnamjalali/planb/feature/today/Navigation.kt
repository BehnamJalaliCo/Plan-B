package com.behnamjalali.planb.feature.today

import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

/** The morning or evening ritual (Plan-B Pro #8); [kind] is [RitualKind.key]. */
@Serializable
data class RitualRoute(val kind: String)

/** Working hours and ritual reminders (Plan-B Pro #5, #8). */
@Serializable
data object DayPlanSettingsRoute

enum class RitualKind(val key: String) {
    MORNING("morning"),
    EVENING("evening"),
    ;

    companion object {
        fun fromKey(key: String?): RitualKind = entries.firstOrNull { it.key == key } ?: MORNING
    }
}
