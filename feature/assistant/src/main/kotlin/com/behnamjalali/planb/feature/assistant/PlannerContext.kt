package com.behnamjalali.planb.feature.assistant

import com.behnamjalali.planb.core.ai.AiPlanItem
import com.behnamjalali.planb.core.ai.AssistantLanguage
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.DayPlanSettings
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.EventOccurrence
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TimeRange
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

internal fun AppLanguage.assistantLanguage(): AssistantLanguage =
    if (this == AppLanguage.PERSIAN) AssistantLanguage.PERSIAN else AssistantLanguage.ENGLISH

/**
 * Turns the user's planner data into the plain text the assistant reads. Dates are ISO with
 * English weekday names (unambiguous for every model, whatever calendar the app shows); titles
 * stay as written. Only what these functions return is ever sent, and the user sees it first.
 */
internal object PlannerContext {
    fun today(today: LocalDate, now: LocalTime?): String = buildString {
        append("Today is ").append(weekday(today)).append(' ').append(today)
        now?.let { append(", ").append(hhmm(it)) }
        append('.')
    }

    fun tasks(tasks: List<Task>, projectNames: Map<EntityId, String>): String = tasks.joinToString("\n") { task(it, projectNames) }

    fun task(task: Task, projectNames: Map<EntityId, String>): String = buildString {
        append(if (task.isCompleted) "- [x] " else "- [ ] ")
        append(task.title.replace('\n', ' ').trim())
        val details = buildList {
            when (task.priority) {
                Priority.HIGH -> add("high priority")
                Priority.MEDIUM -> add("medium priority")
                Priority.LOW -> add("low priority")
                else -> Unit
            }
            task.dueDate?.let { date -> add("planned " + weekday(date) + " " + date + (task.dueTime?.let { " " + hhmm(it) } ?: "")) }
            task.deadline?.let { add("deadline $it") }
            task.projectId?.let { projectNames[it] }?.let { add("project $it") }
            task.estimatedMinutes?.let { add("about $it min") }
            if (task.subtaskCount > 0) add("${task.completedSubtaskCount}/${task.subtaskCount} subtasks done")
            if (task.openBlockerCount > 0) add("waits for ${task.openBlockerCount} other task(s)")
        }
        if (details.isNotEmpty()) append(" (").append(details.joinToString("; ")).append(')')
    }

    fun events(occurrences: List<EventOccurrence>): String = occurrences
        .sortedWith(compareBy({ it.date }, { it.event.startTime ?: LocalTime.MIN }))
        .joinToString("\n") { o -> event(o.date, o.event) }

    private fun event(date: LocalDate, event: CalendarEvent): String = buildString {
        append("- ").append(weekday(date)).append(' ').append(date).append(' ')
        if (event.allDay || event.startTime == null) {
            append("all day")
        } else {
            append(hhmm(event.startTime!!))
            event.endTime?.let { append("–").append(hhmm(it)) }
        }
        append(' ').append(event.title.replace('\n', ' ').trim())
    }

    /** A note as title plus plain text (links become their titles, rich blocks their text). */
    fun note(note: Note): String = buildString {
        append(note.title.trim())
        val body = note.document.plainText().trim()
        if (body.isNotEmpty()) append("\n\n").append(body)
    }

    fun hhmm(time: LocalTime): String = "%02d:%02d".format(Locale.ROOT, time.hour, time.minute)

    private fun weekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
}

/** A proposed block the app checked: inside the working hours, in the future, overlapping nothing. */
data class PlanProposal(
    val task: Task,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val why: String?,
    val range: TimeRange,
)

data class ValidatedPlan(val proposals: List<PlanProposal>, val skipped: Int)

/**
 * Checks every block the assistant proposed before the user sees it. A proposal is dropped (and
 * counted in [ValidatedPlan.skipped]) when its task was not offered or appears twice, its day is
 * not one of [days], its times don't parse, it lies outside the working hours, starts before
 * [earliest], is shorter than 5 minutes or longer than 8 hours, or overlaps something busy or an
 * earlier proposal. All maths is on instants in [zone] (DST-safe).
 */
object PlanValidator {
    private val TIME = Regex("^(\\d{1,2})[:٫.](\\d{2})")

    fun parseTime(text: String): LocalTime? {
        val match = TIME.find(text.trim()) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour == 24 && minute == 0) return LocalTime.MAX
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    fun validate(
        items: List<AiPlanItem>,
        tasks: Map<EntityId, Task>,
        days: List<LocalDate>,
        settings: DayPlanSettings,
        busy: List<TimeRange>,
        earliest: LocalDateTime,
        zone: ZoneId,
    ): ValidatedPlan {
        val accepted = mutableListOf<PlanProposal>()
        val taken = mutableListOf<TimeRange>()
        val used = HashSet<EntityId>()
        var skipped = 0
        val workStart = if (settings.hasWorkingHours) settings.workStart else LocalTime.of(6, 0)
        val workEnd = if (settings.hasWorkingHours) settings.workEnd else LocalTime.of(23, 0)
        for (item in items) {
            val task = tasks[item.taskId]
            val date = item.date?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: days.firstOrNull().takeIf { item.date == null }
            val start = parseTime(item.start)
            val end = parseTime(item.end)
            val ok = task != null && item.taskId !in used && date != null && date in days && start != null && end != null &&
                end > start && start >= workStart && end <= workEnd &&
                Duration.between(start, end).toMinutes() in MIN_MINUTES..MAX_MINUTES &&
                !LocalDateTime.of(date, start).isBefore(earliest)
            if (!ok) {
                skipped++
                continue
            }
            val range = TimeRange(ZonedDateTime.of(date!!, start!!, zone).toInstant(), ZonedDateTime.of(date, end!!, zone).toInstant())
            if ((busy + taken).any { it.start < range.end && range.start < it.end }) {
                skipped++
                continue
            }
            used += item.taskId
            taken += range
            accepted += PlanProposal(task!!, date, start, end, item.why?.takeIf { it.isNotBlank() }, range)
        }
        return ValidatedPlan(accepted.sortedBy { it.range.start }, skipped)
    }

    private const val MIN_MINUTES = 5L
    private const val MAX_MINUTES = 8 * 60L
}
