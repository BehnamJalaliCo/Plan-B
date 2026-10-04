package com.behnamjalali.planb.core.model

import java.time.Instant

data class Notebook(
    val id: EntityId = NEW_ID,
    val title: String,
    val icon: PlannerIcon = PlannerIcon.BOOK,
    val color: AccentColor = AccentColor.LAVENDER,
    val sortOrder: Long = 0,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val archived: Boolean = false,
    val noteCount: Int = 0,
)

data class NotebookSection(
    val id: EntityId = NEW_ID,
    val notebookId: EntityId,
    val title: String,
    val sortOrder: Long = 0,
)

enum class NoteFormat(val key: String) {
    /** JSON-encoded [NoteDocument]; the editor's native format. */
    BLOCKS_V1("blocks-v1"),
    ;

    companion object {
        fun fromKey(key: String?): NoteFormat = entries.firstOrNull { it.key == key } ?: BLOCKS_V1
    }
}

data class Note(
    val id: EntityId = NEW_ID,
    val notebookId: EntityId,
    val sectionId: EntityId? = null,
    val title: String,
    val document: NoteDocument = NoteDocument.EMPTY,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val sortOrder: Long = 0,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val archived: Boolean = false,
    val tags: List<Tag> = emptyList(),
    /** Set while the note is in the trash. */
    val deletedAt: Instant? = null,
    /** Shown only after unlocking; its body may be stored encrypted. */
    val locked: Boolean = false,
) {
    val contentFormat: NoteFormat get() = NoteFormat.BLOCKS_V1
}
