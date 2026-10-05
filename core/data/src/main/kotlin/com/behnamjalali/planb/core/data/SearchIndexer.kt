package com.behnamjalali.planb.core.data

import com.behnamjalali.planb.core.common.SearchNormalizer
import com.behnamjalali.planb.core.database.entity.AttachmentEntity
import com.behnamjalali.planb.core.database.entity.CalendarEventEntity
import com.behnamjalali.planb.core.database.entity.GoalEntity
import com.behnamjalali.planb.core.database.entity.HabitEntity
import com.behnamjalali.planb.core.database.entity.NoteEntity
import com.behnamjalali.planb.core.database.entity.NotebookEntity
import com.behnamjalali.planb.core.database.entity.ProjectEntity
import com.behnamjalali.planb.core.database.entity.SearchIndexEntity
import com.behnamjalali.planb.core.database.entity.TaskEntity
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.SearchEntityType

/** Builds FTS rows from entities. The index stores normalized tokens only. */
internal object SearchIndexer {
    private const val TYPE_BITS = 16L

    fun rowId(type: SearchEntityType, id: Long): Long = id * TYPE_BITS + type.code

    private fun entry(type: SearchEntityType, id: Long, vararg parts: String) = SearchIndexEntity(
        rowId = rowId(type, id),
        entityType = type.code,
        entityId = id,
        content = SearchNormalizer.indexTokens(parts.joinToString(" ")).joinToString(" "),
    )

    fun task(e: TaskEntity) = entry(SearchEntityType.TASK, e.id, e.title, e.description, e.notes)
    fun project(e: ProjectEntity) = entry(SearchEntityType.PROJECT, e.id, e.title, e.description)
    /**
     * A locked note is found by its title only: its body, and the text recognized in its images
     * and recordings ([attachmentText], Plan-B Pro #17/#19), never enter the index.
     */
    fun note(e: NoteEntity, attachmentText: String = "") = if (e.locked || e.encryptedPayload != null) {
        entry(SearchEntityType.NOTE, e.id, e.title)
    } else {
        entry(SearchEntityType.NOTE, e.id, e.title, NoteDocument.decode(e.content).plainText(), attachmentText)
    }

    /** The searchable text of a note's attachments: recognized text and transcripts. */
    fun attachmentText(attachments: List<AttachmentEntity>): String =
        attachments.flatMap { listOfNotNull(it.ocrText, it.transcript) }.filter { it.isNotBlank() }.joinToString(" ")
    fun notebook(e: NotebookEntity) = entry(SearchEntityType.NOTEBOOK, e.id, e.title)
    fun habit(e: HabitEntity) = entry(SearchEntityType.HABIT, e.id, e.title, e.unit)
    fun goal(e: GoalEntity) = entry(SearchEntityType.GOAL, e.id, e.title, e.description, e.notes)
    fun event(e: CalendarEventEntity) = entry(SearchEntityType.EVENT, e.id, e.title, e.description, e.notes)

    /** Converts user input into a safe FTS4 prefix query, or null when nothing is searchable. */
    fun matchQuery(input: String): String? {
        val tokens = SearchNormalizer.tokens(input)
        if (tokens.isEmpty()) return null
        return tokens.take(8).joinToString(" ") { "$it*" }
    }
}
