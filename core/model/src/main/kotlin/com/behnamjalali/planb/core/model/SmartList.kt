package com.behnamjalali.planb.core.model

import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Date conditions of a smart list, on the planned date (overdue also looks at the deadline). */
enum class SmartDateRange { ANY, OVERDUE, TODAY, NEXT_7_DAYS, NO_DATE, CUSTOM }

/**
 * A custom smart list's filter (Plan-B Pro #10). Empty sets mean "any". [statuses] empty means
 * open tasks (to do and in progress); archived and trashed tasks never match.
 * [noProject] adds tasks without a project to [projectIds] (alone: only tasks without one).
 * [from]/[to] bound the planned date for [SmartDateRange.CUSTOM] (inclusive, either may be open).
 * [hasDeadline]: null = either, true = only with a deadline, false = only without.
 */
data class SmartFilter(
    val projectIds: Set<EntityId> = emptySet(),
    val noProject: Boolean = false,
    val tagIds: Set<EntityId> = emptySet(),
    val priorities: Set<Priority> = emptySet(),
    val statuses: Set<TaskStatus> = emptySet(),
    val dateRange: SmartDateRange = SmartDateRange.ANY,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val hasDeadline: Boolean? = null,
    val text: String = "",
    val sort: TaskSort = TaskSort.DUE_DATE,
)

/** A saved smart list as shown in Tasks. */
data class SavedFilter(
    val id: EntityId = NEW_ID,
    val name: String,
    val icon: PlannerIcon = PlannerIcon.STAR,
    val color: AccentColor = AccentColor.LAVENDER,
    val filter: SmartFilter = SmartFilter(),
    val sortOrder: Long = 0,
)

/**
 * The `saved_filters.query` JSON document, **version 1**:
 *
 * ```json
 * {"version":1,"projects":[3],"noProject":false,"tags":[1,2],"priorities":["HIGH","MEDIUM"],
 *  "statuses":["TODO","IN_PROGRESS"],"date":"NEXT_7_DAYS","from":"2026-10-01","to":"2026-10-31",
 *  "hasDeadline":true,"text":"report","sort":"DEADLINE"}
 * ```
 *
 * Every field is optional and defaults to "any". Enum values are constant names, dates are
 * ISO-8601 (`yyyy-MM-dd`). Readers drop unknown keys and unknown enum values, and read a newer
 * version as far as they understand it; a document that is not JSON reads as the empty filter.
 * Writers always write the current [VERSION].
 */
object SmartFilterCodec {
    const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        // Every field is written (absent nulls aside), so the document is self-describing.
        explicitNulls = false
        encodeDefaults = true
    }

    @Serializable
    private data class Document(
        val version: Int = VERSION,
        val projects: List<Long> = emptyList(),
        val noProject: Boolean = false,
        val tags: List<Long> = emptyList(),
        val priorities: List<String> = emptyList(),
        val statuses: List<String> = emptyList(),
        @SerialName("date") val dateRange: String? = null,
        val from: String? = null,
        val to: String? = null,
        val hasDeadline: Boolean? = null,
        val text: String = "",
        val sort: String? = null,
    )

    fun encode(filter: SmartFilter): String = json.encodeToString(
        Document.serializer(),
        Document(
            version = VERSION,
            projects = filter.projectIds.sorted(),
            noProject = filter.noProject,
            tags = filter.tagIds.sorted(),
            priorities = filter.priorities.sortedByDescending { it.weight }.map { it.name },
            statuses = filter.statuses.sorted().map { it.name },
            dateRange = filter.dateRange.takeIf { it != SmartDateRange.ANY }?.name,
            from = filter.from?.toString(),
            to = filter.to?.toString(),
            hasDeadline = filter.hasDeadline,
            text = filter.text.trim(),
            sort = filter.sort.name,
        ),
    )

    fun decode(value: String?): SmartFilter {
        if (value.isNullOrBlank()) return SmartFilter()
        val doc = runCatching { json.decodeFromString(Document.serializer(), value) }.getOrNull() ?: return SmartFilter()
        return SmartFilter(
            projectIds = doc.projects.toSet(),
            noProject = doc.noProject,
            tagIds = doc.tags.toSet(),
            priorities = doc.priorities.mapNotNull { name -> Priority.entries.firstOrNull { it.name == name } }.toSet(),
            statuses = doc.statuses.mapNotNull { name -> TaskStatus.entries.firstOrNull { it.name == name } }.toSet(),
            dateRange = doc.dateRange?.let { name -> SmartDateRange.entries.firstOrNull { it.name == name } } ?: SmartDateRange.ANY,
            from = doc.from?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            to = doc.to?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            hasDeadline = doc.hasDeadline,
            text = doc.text,
            sort = doc.sort?.let { name -> TaskSort.entries.firstOrNull { it.name == name } } ?: TaskSort.DUE_DATE,
        )
    }
}
