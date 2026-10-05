package com.behnamjalali.planb.core.model

import java.time.Instant
import java.time.LocalDate

/** A note as links, backlinks, the link picker and the graph see it: no body. */
data class NoteRef(
    val id: EntityId,
    val title: String,
    val notebookId: EntityId,
    val updatedAt: Instant = Instant.EPOCH,
    val locked: Boolean = false,
    /** In the trash: links to it are shown as unavailable. */
    val trashed: Boolean = false,
)

/** A saved state of a note (Plan-B Pro #16, history). */
data class NoteVersion(
    val id: EntityId,
    val noteId: EntityId,
    val createdAt: Instant,
    val title: String,
    val document: NoteDocument,
    val size: Long,
)

/** Notes and the links between them (Plan-B Pro #21); [edges] are (from, to) note ids. */
data class NoteGraph(
    val notes: List<NoteRef> = emptyList(),
    val edges: List<Pair<EntityId, EntityId>> = emptyList(),
    /** Tag ids per note id. */
    val tags: Map<EntityId, Set<EntityId>> = emptyMap(),
)

/** A day's journal page as the journal list shows it. */
data class JournalPage(
    val entryId: EntityId,
    val date: LocalDate,
    val noteId: EntityId,
    val promptId: String?,
    val title: String,
    /** The first lines of the page; empty for a locked page. */
    val preview: String,
    val locked: Boolean,
)
