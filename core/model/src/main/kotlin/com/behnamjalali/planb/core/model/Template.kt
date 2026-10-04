package com.behnamjalali.planb.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
enum class TemplateType {
    @SerialName("note") NOTE,
    @SerialName("project") PROJECT,
    @SerialName("tasks") TASKS,
    @SerialName("habits") HABITS,
}

@Serializable
data class TemplateTask(
    val title: String,
    /** Due date relative to the day the template is applied; null = no date. */
    val dayOffset: Int? = null,
    val priority: Int = 0,
)

@Serializable
data class TemplateHabit(
    val title: String,
    val schedule: String = "DAILY",
    val target: Int = 1,
    val unit: String = "",
    val icon: String = "star",
    val color: String = "mint",
)

@Serializable
data class TemplateMilestone(val title: String, val dayOffset: Int? = null)

/**
 * Structured template content. Text may contain the placeholder `{{date}}`,
 * replaced with the formatted application date.
 */
@Serializable
data class TemplatePayload(
    val noteTitle: String? = null,
    val blocks: List<NoteBlock> = emptyList(),
    val tasks: List<TemplateTask> = emptyList(),
    val milestones: List<TemplateMilestone> = emptyList(),
    val habits: List<TemplateHabit> = emptyList(),
    val projectTitle: String? = null,
    val description: String = "",
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val DATE_PLACEHOLDER = "{{date}}"
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        fun decode(raw: String): TemplatePayload = json.decodeFromString(serializer(), raw)
    }
}

data class PlannerTemplate(
    val id: EntityId = NEW_ID,
    /** Stable key for built-in templates (localized at runtime); null for custom. */
    val builtInKey: String? = null,
    val title: String,
    val type: TemplateType,
    val payload: TemplatePayload,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
) {
    val builtIn: Boolean get() = builtInKey != null
}
