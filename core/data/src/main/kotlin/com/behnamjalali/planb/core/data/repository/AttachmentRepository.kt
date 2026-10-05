package com.behnamjalali.planb.core.data.repository

import androidx.room.withTransaction
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.AttachmentFiles
import com.behnamjalali.planb.core.data.AttachmentLimits
import com.behnamjalali.planb.core.data.ImageProcessor
import com.behnamjalali.planb.core.data.SearchIndexer
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.entity.AttachmentEntity
import com.behnamjalali.planb.core.model.Attachment
import com.behnamjalali.planb.core.model.AttachmentKind
import com.behnamjalali.planb.core.model.AttachmentOwner
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.rich.Drawing
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** A file could not be added: it is too large, or all attachments together would be. */
class AttachmentLimitException(val reason: Reason) : IOException("Attachment limit: $reason") {
    enum class Reason { FILE_TOO_LARGE, STORAGE_FULL }
}

/**
 * Files of notes' rich blocks (Plan-B Pro #15, #17, #19): photos, scans, files, recordings and
 * drawings in the app-private attachments folder, one `attachments` row each (owner: the note).
 * Files are never shared with other apps except when the user opens or shares one, and never
 * uploaded. Limits follow the backup format ([AttachmentLimits]).
 */
interface AttachmentRepository {
    fun observeForNote(noteId: EntityId): Flow<List<Attachment>>
    suspend fun get(id: EntityId): Attachment?

    /** The stored file of an attachment (inside the private attachments folder). */
    fun file(attachment: Attachment): File

    /** Stores a photo or scan, scaled down and re-encoded ([AttachmentLimits.MAX_IMAGE_DIMENSION]). */
    suspend fun addImage(noteId: EntityId, kind: AttachmentKind, displayName: String, open: () -> InputStream): Attachment

    /** Copies any file as it is (at most [AttachmentLimits.MAX_FILE_BYTES]). */
    suspend fun addFile(noteId: EntityId, displayName: String, mimeType: String, open: () -> InputStream): Attachment

    /** Moves a finished recording into the attachments folder. */
    suspend fun addRecording(noteId: EntityId, recording: File, durationMillis: Long, displayName: String): Attachment

    /**
     * Saves a drawing: its strokes as a vector file and [png] as its preview. Existing
     * attachments ([previewId], [vectorId]) are replaced in place. Returns (preview, vector).
     */
    suspend fun saveDrawing(noteId: EntityId, previewId: EntityId?, vectorId: EntityId?, drawing: Drawing, png: ByteArray, width: Int, height: Int): Pair<Attachment, Attachment>

    suspend fun readDrawing(vectorId: EntityId): Drawing

    /** Text recognized in an image or scan; the owning note is re-indexed (unless locked). */
    suspend fun setOcrText(id: EntityId, text: String?)

    /** A recording's transcript; the owning note is re-indexed (unless locked). */
    suspend fun setTranscript(id: EntityId, text: String?)

    /**
     * Removes attachments of [noteId] that its blocks no longer use — neither the saved note
     * nor its unsaved draft nor a kept version. A locked note's body cannot be read, so its
     * attachments are kept. Returns how many were removed (rows and files).
     */
    suspend fun deleteUnused(noteId: EntityId): Int

    /** [deleteUnused] for every note with attachments (app start). */
    suspend fun deleteUnusedEverywhere(): Int
}

@Singleton
class OfflineAttachmentRepository @Inject constructor(
    private val db: PlanBDatabase,
    private val files: AttachmentFiles,
    private val images: ImageProcessor,
    private val time: TimeProvider,
) : AttachmentRepository {
    private val dao get() = db.attachmentDao()

    override fun observeForNote(noteId: EntityId) =
        dao.observeForOwner(AttachmentOwner.NOTE.name, noteId).map { list -> list.map { it.toModel() } }

    override suspend fun get(id: EntityId) = dao.get(id)?.toModel()

    override fun file(attachment: Attachment): File = files.file(attachment.fileName)

    override suspend fun addImage(noteId: EntityId, kind: AttachmentKind, displayName: String, open: () -> InputStream): Attachment {
        val source = copyToTemp(open, AttachmentLimits.MAX_SOURCE_IMAGE_BYTES)
        try {
            val name = files.newFileName("img")
            val target = files.file(name).also { it.parentFile?.mkdirs() }
            val stored = try {
                images.store(source, target)
            } catch (e: Exception) {
                target.delete()
                throw e
            }
            // The extension follows the stored format.
            val finalName = files.newFileName(if (stored.mimeType == "image/png") "png" else "jpg")
            val final = files.file(finalName)
            if (!target.renameTo(final)) {
                target.delete()
                throw IOException("Cannot store the image")
            }
            return insert(
                noteId, kind, finalName, final, displayName, stored.mimeType,
                width = stored.width, height = stored.height,
            )
        } finally {
            source.delete()
        }
    }

    override suspend fun addFile(noteId: EntityId, displayName: String, mimeType: String, open: () -> InputStream): Attachment {
        val source = copyToTemp(open, AttachmentLimits.MAX_FILE_BYTES)
        val name = files.newFileName(displayName.substringAfterLast('.', "").takeIf { '.' in displayName }.orEmpty())
        val target = files.file(name).also { it.parentFile?.mkdirs() }
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
        return insert(noteId, AttachmentKind.FILE, name, target, displayName, mimeType.ifBlank { "application/octet-stream" })
    }

    override suspend fun addRecording(noteId: EntityId, recording: File, durationMillis: Long, displayName: String): Attachment {
        if (recording.length() > AttachmentLimits.MAX_FILE_BYTES) {
            recording.delete()
            throw AttachmentLimitException(AttachmentLimitException.Reason.FILE_TOO_LARGE)
        }
        val name = files.newFileName("m4a")
        val target = files.file(name).also { it.parentFile?.mkdirs() }
        if (!recording.renameTo(target)) {
            recording.copyTo(target, overwrite = true)
            recording.delete()
        }
        return insert(noteId, AttachmentKind.AUDIO, name, target, displayName, "audio/mp4", durationMillis = durationMillis)
    }

    override suspend fun saveDrawing(
        noteId: EntityId,
        previewId: EntityId?,
        vectorId: EntityId?,
        drawing: Drawing,
        png: ByteArray,
        width: Int,
        height: Int,
    ): Pair<Attachment, Attachment> {
        val vectorBytes = drawing.encode().toByteArray(Charsets.UTF_8)
        if (png.size > AttachmentLimits.MAX_FILE_BYTES || vectorBytes.size > AttachmentLimits.MAX_FILE_BYTES) {
            throw AttachmentLimitException(AttachmentLimitException.Reason.FILE_TOO_LARGE)
        }
        val preview = replaceOrInsert(noteId, previewId, "png", png, "image/png", width, height)
        val vector = replaceOrInsert(noteId, vectorId, "json", vectorBytes, DRAWING_MIME, drawing.width, drawing.height)
        return preview to vector
    }

    private suspend fun replaceOrInsert(noteId: EntityId, id: EntityId?, ext: String, bytes: ByteArray, mime: String, width: Int, height: Int): Attachment {
        val existing = id?.let { dao.get(it) }?.takeIf { it.ownerType == AttachmentOwner.NOTE.name && it.ownerId == noteId }
        val name = files.newFileName(ext)
        val target = files.file(name).also { it.parentFile?.mkdirs() }
        target.writeBytes(bytes)
        if (existing == null) {
            return insert(noteId, AttachmentKind.DRAWING, name, target, "", mime, width = width, height = height)
        }
        // A new file name for every save: cached previews of the old one can never show stale strokes.
        checkSpace(bytes.size.toLong() - existing.sizeBytes, extraFiles = 0)
        val updated = existing.copy(fileName = name, sizeBytes = bytes.size.toLong(), width = width, height = height)
        dao.update(updated)
        runCatching { files.file(existing.fileName).delete() }
        return updated.toModel()
    }

    override suspend fun readDrawing(vectorId: EntityId): Drawing {
        val row = dao.get(vectorId) ?: return Drawing()
        return runCatching { Drawing.decode(files.file(row.fileName).readText(Charsets.UTF_8)) }.getOrDefault(Drawing())
    }

    override suspend fun setOcrText(id: EntityId, text: String?) = updateText(id) { dao.setOcrText(id, text?.trim()?.takeIf(String::isNotEmpty)) }

    override suspend fun setTranscript(id: EntityId, text: String?) = updateText(id) { dao.setTranscript(id, text?.trim()?.takeIf(String::isNotEmpty)) }

    private suspend fun updateText(id: EntityId, write: suspend () -> Unit) {
        db.withTransaction {
            write()
            val row = dao.get(id) ?: return@withTransaction
            if (row.ownerType != AttachmentOwner.NOTE.name) return@withTransaction
            val note = db.noteDao().getNote(row.ownerId) ?: return@withTransaction
            // Notes in the trash are not searchable; a locked note stays indexed by its title only.
            if (note.deletedAt != null) return@withTransaction
            db.searchDao().upsert(SearchIndexer.note(note, SearchIndexer.attachmentText(dao.forOwner(AttachmentOwner.NOTE.name, note.id))))
        }
    }

    override suspend fun deleteUnused(noteId: EntityId): Int {
        val removed = db.withTransaction {
            val rows = dao.forOwner(AttachmentOwner.NOTE.name, noteId)
            if (rows.isEmpty()) return@withTransaction emptyList()
            val note = db.noteDao().getNote(noteId) ?: return@withTransaction emptyList()
            if (note.locked || note.encryptedPayload != null) return@withTransaction emptyList()
            val used = buildSet {
                addAll(NoteDocument.decode(note.content).attachmentIds())
                db.noteDraftDao().get(noteId)?.let { addAll(NoteDocument.decode(it.content).attachmentIds()) }
                db.noteVersionDao().observeForNote(noteId, MAX_VERSIONS_CHECKED).first().forEach { addAll(NoteDocument.decode(it.content).attachmentIds()) }
            }
            // A file added in the last minutes may belong to a block that is not saved yet.
            val cutoff = time.now().minusMillis(GRACE_MILLIS)
            val unused = rows.filter { it.id !in used && it.createdAt.isBefore(cutoff) }
            unused.forEach { dao.delete(it.id) }
            if (unused.any { it.ocrText != null || it.transcript != null }) {
                db.searchDao().upsert(SearchIndexer.note(note, SearchIndexer.attachmentText(dao.forOwner(AttachmentOwner.NOTE.name, noteId))))
            }
            unused.map { it.fileName }
        }
        removed.forEach { runCatching { files.file(it).delete() } }
        return removed.size
    }

    override suspend fun deleteUnusedEverywhere(): Int =
        dao.ownerIds(AttachmentOwner.NOTE.name).sumOf { deleteUnused(it) }

    private suspend fun insert(
        noteId: EntityId,
        kind: AttachmentKind,
        name: String,
        file: File,
        displayName: String,
        mimeType: String,
        durationMillis: Long? = null,
        width: Int? = null,
        height: Int? = null,
    ): Attachment {
        val size = file.length()
        try {
            if (size > AttachmentLimits.MAX_FILE_BYTES) throw AttachmentLimitException(AttachmentLimitException.Reason.FILE_TOO_LARGE)
            return db.withTransaction {
                checkSpace(size, extraFiles = 1)
                val entity = AttachmentEntity(
                    ownerType = AttachmentOwner.NOTE.name,
                    ownerId = noteId,
                    kind = kind.name,
                    fileName = name,
                    displayName = displayName.take(MAX_NAME),
                    mimeType = mimeType,
                    sizeBytes = size,
                    durationMillis = durationMillis,
                    width = width,
                    height = height,
                    createdAt = time.now(),
                )
                entity.copy(id = dao.insert(entity)).toModel()
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    private suspend fun checkSpace(extraBytes: Long, extraFiles: Int) {
        if (dao.count() + extraFiles > AttachmentLimits.MAX_COUNT || dao.totalBytes() + extraBytes > AttachmentLimits.MAX_TOTAL_BYTES) {
            throw AttachmentLimitException(AttachmentLimitException.Reason.STORAGE_FULL)
        }
    }

    /** Copies a stream into a private temporary file, stopping once it exceeds [limit] bytes. */
    private fun copyToTemp(open: () -> InputStream, limit: Long): File {
        val temp = File(files.directory.parentFile, TEMP_DIR).apply { mkdirs() }.let { File.createTempFile("import", ".tmp", it) }
        try {
            open().use { input ->
                temp.outputStream().use { out ->
                    val buffer = ByteArray(BUFFER)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > limit) throw AttachmentLimitException(AttachmentLimitException.Reason.FILE_TOO_LARGE)
                        out.write(buffer, 0, read)
                    }
                }
            }
            return temp
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    private fun AttachmentEntity.toModel() = Attachment(
        id = id,
        owner = runCatching { AttachmentOwner.valueOf(ownerType) }.getOrDefault(AttachmentOwner.NOTE),
        ownerId = ownerId,
        kind = runCatching { AttachmentKind.valueOf(kind) }.getOrDefault(AttachmentKind.FILE),
        fileName = fileName,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        durationMillis = durationMillis,
        width = width,
        height = height,
        ocrText = ocrText,
        transcript = transcript,
        createdAt = createdAt,
    )

    companion object {
        /** MIME type of a drawing's vector file. */
        const val DRAWING_MIME = "application/vnd.planb.drawing+json"
        private const val TEMP_DIR = "attachment-import"
        private const val BUFFER = 64 * 1024
        private const val MAX_NAME = 200
        private const val MAX_VERSIONS_CHECKED = 1_000
        private const val GRACE_MILLIS = 10 * 60 * 1000L
    }
}
