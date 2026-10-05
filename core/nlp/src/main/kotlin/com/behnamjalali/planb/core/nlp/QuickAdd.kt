package com.behnamjalali.planb.core.nlp

import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** What a recognized part of a quick-add text means. */
enum class QuickAddKind { DATE, TIME, RECURRENCE, PRIORITY, TAG, PROJECT, DEADLINE, DURATION, REMINDER }

/**
 * A recognized part of the text: [start] until [end] (exclusive) in the original text.
 * [key] identifies the interpretation so the user can dismiss it ("keep as text"); it stays
 * the same while the rest of the text changes.
 */
data class QuickAddPart(val kind: QuickAddKind, val start: Int, val end: Int, val text: String, val key: String)

/** An existing project that "@name" can refer to. */
data class QuickAddProject(val id: EntityId, val name: String)

/**
 * Everything the parser needs besides the text: the local "now" (relative dates and the
 * ambiguous-hour rule), the user's calendar for numeric dates and month arithmetic, the first
 * day of the week ("next week", "next Friday", the weekend) and the projects for "@name".
 */
data class QuickAddContext(
    val now: LocalDateTime,
    val calendar: CalendarSystem,
    val firstDayOfWeek: DayOfWeek,
    val projects: List<QuickAddProject> = emptyList(),
)

/**
 * The result of parsing: [title] is the text without the recognized parts. [date] is set by
 * a date, a relative time, a time alone (today or tomorrow) or the start of a repeat;
 * [reminderMinutesBefore] is relative to the planned date and time.
 */
data class QuickAddResult(
    val title: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val recurrence: RecurrenceRule? = null,
    val priority: Priority? = null,
    val tags: List<String> = emptyList(),
    val project: QuickAddProject? = null,
    val deadline: LocalDate? = null,
    val durationMinutes: Int? = null,
    val reminderMinutesBefore: Int? = null,
    val parts: List<QuickAddPart> = emptyList(),
) {
    val isEmpty: Boolean get() = parts.isEmpty()
}
