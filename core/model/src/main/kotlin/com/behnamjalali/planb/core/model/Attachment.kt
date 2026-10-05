package com.behnamjalali.planb.core.model

import java.time.Instant

/** Persisted in `attachments.kind`; never rename. */
enum class AttachmentKind { IMAGE, FILE, AUDIO, DRAWING, SCAN }

/** Persisted in `attachments.owner_type`; never rename. */
enum class AttachmentOwner { TASK, NOTE, EVENT, MOOD }

/**
 * A file kept in the app's private attachments folder (Plan-B Pro #15, #17, #19). [fileName]
 * is a flat name inside that folder; [ocrText] and [transcript] are searchable through the
 * owner (never for locked notes).
 */
data class Attachment(
    val id: EntityId = NEW_ID,
    val owner: AttachmentOwner,
    val ownerId: EntityId,
    val kind: AttachmentKind,
    val fileName: String,
    val displayName: String = "",
    val mimeType: String,
    val sizeBytes: Long,
    val durationMillis: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val ocrText: String? = null,
    val transcript: String? = null,
    val createdAt: Instant = Instant.EPOCH,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
}
