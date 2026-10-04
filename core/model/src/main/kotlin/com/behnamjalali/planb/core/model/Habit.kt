package com.behnamjalali.planb.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

sealed interface HabitSchedule {
    data object Daily : HabitSchedule
    data class SelectedDays(val days: Set<DayOfWeek>) : HabitSchedule
    data class TimesPerWeek(val times: Int) : HabitSchedule
    data class EveryNDays(val interval: Int) : HabitSchedule

    fun encode(): String = when (this) {
        Daily -> "DAILY"
        is SelectedDays -> "DAYS:" + days.sorted().joinToString(",") { it.value.toString() }
        is TimesPerWeek -> "WEEKLY:$times"
        is EveryNDays -> "EVERY:$interval"
    }

    companion object {
        fun decode(value: String?): HabitSchedule {
            if (value.isNullOrBlank()) return Daily
            val (kind, arg) = (value.split(':', limit = 2) + "").let { it[0] to it[1] }
            return runCatching {
                when (kind) {
                    "DAYS" -> SelectedDays(
                        arg.split(',').filter { it.isNotBlank() }.map { DayOfWeek.of(it.toInt()) }.toSet(),
                    ).takeIf { it.days.isNotEmpty() } ?: Daily
                    "WEEKLY" -> TimesPerWeek(arg.toInt().coerceIn(1, 7))
                    "EVERY" -> EveryNDays(arg.toInt().coerceAtLeast(1))
                    else -> Daily
                }
            }.getOrDefault(Daily)
        }
    }
}

data class Habit(
    val id: EntityId = NEW_ID,
    val title: String,
    val icon: PlannerIcon = PlannerIcon.STAR,
    val color: AccentColor = AccentColor.MINT,
    val schedule: HabitSchedule = HabitSchedule.Daily,
    /** Amount per scheduled day needed to count the day as done. */
    val target: Int = 1,
    val unit: String = "",
    val reminderTime: LocalTime? = null,
    val startDate: LocalDate,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val archived: Boolean = false,
    /** Health Connect metric that checks the habit off automatically. */
    val healthMetric: HealthMetric? = null,
    /** Daily amount of [healthMetric] that counts as done. */
    val healthThreshold: Long? = null,
)

/** Health Connect data a habit can be checked off with; stored by [name]. */
enum class HealthMetric {
    STEPS,
    SLEEP_MINUTES,
    HYDRATION_ML,
    ACTIVE_MINUTES,
    DISTANCE_METERS,
    ;

    companion object {
        fun fromKey(key: String?): HealthMetric? = entries.firstOrNull { it.name == key }
    }
}

data class HabitCompletion(
    val id: EntityId = NEW_ID,
    val habitId: EntityId,
    val date: LocalDate,
    val amount: Int = 1,
    val createdAt: Instant = Instant.EPOCH,
)
