package com.behnamjalali.planb.feature.notebooks.rich

import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.UnsupportedImageException
import com.behnamjalali.planb.core.data.repository.AttachmentLimitException
import com.behnamjalali.planb.core.data.repository.AttachmentRepository
import com.behnamjalali.planb.core.model.Attachment
import com.behnamjalali.planb.core.model.AttachmentKind
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.rich.AudioData
import com.behnamjalali.planb.core.model.rich.Drawing
import com.behnamjalali.planb.core.model.rich.DrawingRef
import com.behnamjalali.planb.core.model.rich.RichBlocks
import com.behnamjalali.planb.feature.notebooks.EditorBlock
import com.behnamjalali.planb.feature.notebooks.NoteEditorState
import com.behnamjalali.planb.feature.notebooks.media.HandwritingRecognition
import com.behnamjalali.planb.feature.notebooks.media.PickedContent
import com.behnamjalali.planb.feature.notebooks.media.SpeechTranscription
import com.behnamjalali.planb.feature.notebooks.media.TextRecognition
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** Long-running work on a block, shown in place (a spinner and a label). */
enum class RichWork { IMPORTING, RECOGNIZING, TRANSCRIBING, DOWNLOADING_MODEL, CONVERTING }

/** Calm, plain-words outcomes shown as a snackbar. */
enum class RichMessage {
    FILE_TOO_LARGE,
    STORAGE_FULL,
    IMAGE_UNREADABLE,
    IMPORT_FAILED,
    NOTHING_RECOGNIZED,
    TRANSCRIPTION_UNAVAILABLE,
    HANDWRITING_UNAVAILABLE,
}

/** The user is asked once per model before a handwriting model is downloaded. */
data class HandwritingConsent(val blockId: String, val language: String)

/**
 * The note editor's Plan-B Pro rich blocks (#15, #17–#20, #23): inserting and changing them,
 * importing their files and running recognition. It works on the editor's state through
 * [Host], so every change goes through the editor's autosave and drafts.
 */
internal class RichEditor(
    private val host: Host,
    private val attachments: AttachmentRepository?,
    private val textRecognition: TextRecognition?,
    private val handwriting: HandwritingRecognition?,
    private val speech: SpeechTranscription?,
    private val picked: PickedContent?,
    private val newId: () -> String,
) {
    interface Host {
        val scope: CoroutineScope
        val state: NoteEditorState

        /** A content change (saved): like typing. [ownId] keeps that block's revision. */
        fun edit(ownId: String? = null, transform: (NoteEditorState) -> NoteEditorState)

        /** A view-only change (not saved). */
        fun update(transform: (NoteEditorState) -> NoteEditorState)

        fun message(message: RichMessage)
    }

    private var observing: Job? = null

    /** Keeps the note's attachments in the state (for previews, players and recognized text). */
    fun observe(noteId: EntityId) {
        val repo = attachments ?: return
        observing?.cancel()
        observing = host.scope.launch {
            repo.observeForNote(noteId).collectLatest { list -> host.update { s -> s.copy(attachments = list.associateBy { it.id }) } }
        }
    }

    fun file(attachment: Attachment): File? = attachments?.file(attachment)

    /** The platform can transcribe recorded files (otherwise transcripts are dictated live). */
    fun canTranscribeFiles(): Boolean = speech?.canTranscribeFiles() == true

    /**
     * Inserts a block after the focused one (or at the end) and focuses it. A rich block never
     * ends the note: an empty text block follows it so writing can go on.
     */
    fun insert(type: BlockType, data: JsonObject? = null, text: String = "", attachmentId: Long? = null): String {
        val block = EditorBlock(newId(), type, TextFieldValue(text), data = data, attachmentId = attachmentId)
        host.edit { s ->
            val focused = s.blocks.indexOfFirst { it.id == s.focusId }
            val at = if (focused >= 0) focused + 1 else s.blocks.size
            val list = s.blocks.toMutableList()
            // An empty text block that has the focus is replaced instead of left above.
            val replace = focused >= 0 && list[focused].type == BlockType.TEXT && list[focused].value.text.isEmpty()
            if (replace) list[focused] = block else list.add(at, block)
            val index = list.indexOf(block)
            if (type.isRich && index == list.lastIndex) list.add(EditorBlock(newId(), BlockType.TEXT))
            s.copy(blocks = list, focusId = block.id, focusVersion = s.focusVersion + 1)
        }
        return block.id
    }

    /** Changes a rich block's payload and/or text (caption, formula). */
    fun update(id: String, data: JsonObject? = null, text: String? = null) = host.edit(ownId = id) { s ->
        s.copy(
            blocks = s.blocks.map { b ->
                if (b.id != id) b else b.copy(data = data ?: b.data, value = if (text == null) b.value else b.value.copy(text = text))
            },
        )
    }

    private fun work(id: String, work: RichWork?) = host.update { s ->
        s.copy(richWork = if (work == null) s.richWork - id else s.richWork + (id to work))
    }

    private fun failed(e: Throwable) = host.message(
        when (e) {
            is AttachmentLimitException -> if (e.reason == AttachmentLimitException.Reason.FILE_TOO_LARGE) RichMessage.FILE_TOO_LARGE else RichMessage.STORAGE_FULL
            is UnsupportedImageException -> RichMessage.IMAGE_UNREADABLE
            else -> RichMessage.IMPORT_FAILED
        },
    )

    /** Photos from the gallery or pages from the scanner; each becomes a block and is read for text. */
    fun addImages(uris: List<Uri>, kind: AttachmentKind) {
        val repo = attachments ?: return
        val reader = picked ?: return
        val noteId = host.state.noteId
        uris.forEach { uri ->
            host.scope.launch {
                runCatchingSafely {
                    withContext(Dispatchers.IO) { repo.addImage(noteId, kind, reader.displayName(uri)) { reader.open(uri) } }
                }.onSuccess { attachment -> afterImage(attachment, kind) }.onFailure(::failed)
            }
        }
    }

    /** A photo taken with the camera (or a cropped page): [file] is a private temporary file. */
    fun addCaptured(file: File, kind: AttachmentKind, displayName: String) {
        val repo = attachments ?: return
        val noteId = host.state.noteId
        host.scope.launch {
            runCatchingSafely {
                withContext(Dispatchers.IO) { repo.addImage(noteId, kind, displayName) { file.inputStream() } }
            }.onSuccess { afterImage(it, kind) }.onFailure(::failed)
            withContext(Dispatchers.IO) { file.delete() }
        }
    }

    private fun afterImage(attachment: Attachment, kind: AttachmentKind) {
        val id = insert(if (kind == AttachmentKind.SCAN) BlockType.SCAN else BlockType.IMAGE, attachmentId = attachment.id)
        // Text in photos and scans becomes searchable (#17); a photo without text stays silent.
        recognizeText(id, quiet = kind != AttachmentKind.SCAN)
    }

    fun addFile(uri: Uri) {
        val repo = attachments ?: return
        val reader = picked ?: return
        val noteId = host.state.noteId
        host.scope.launch {
            runCatchingSafely {
                withContext(Dispatchers.IO) { repo.addFile(noteId, reader.displayName(uri), reader.mimeType(uri)) { reader.open(uri) } }
            }.onSuccess { insert(BlockType.FILE, attachmentId = it.id) }.onFailure(::failed)
        }
    }

    fun addRecording(file: File, durationMillis: Long, samples: List<Float>, displayName: String) {
        val repo = attachments ?: return
        val noteId = host.state.noteId
        host.scope.launch {
            runCatchingSafely {
                withContext(Dispatchers.IO) { repo.addRecording(noteId, file, durationMillis, displayName) }
            }.onSuccess { insert(BlockType.AUDIO, data = RichBlocks.encode(AudioData(AudioData.bars(samples))), attachmentId = it.id) }
                .onFailure(::failed)
        }
    }

    /** Saves a drawing; a new one ([blockId] null) is inserted as a block. */
    fun saveDrawing(blockId: String?, drawing: Drawing, png: ByteArray, width: Int, height: Int) {
        val repo = attachments ?: return
        val noteId = host.state.noteId
        val block = blockId?.let { id -> host.state.blocks.firstOrNull { it.id == id } }
        val ref = block?.let { RichBlocks.drawing(it.toNoteBlock()) }
        host.scope.launch {
            runCatchingSafely {
                withContext(Dispatchers.IO) { repo.saveDrawing(noteId, block?.attachmentId, ref?.vectorId, drawing, png, width, height) }
            }.onSuccess { (preview, vector) ->
                val data = RichBlocks.encode(DrawingRef(vector.id, drawing.width, drawing.height))
                if (block == null) {
                    insert(BlockType.DRAWING, data = data, attachmentId = preview.id)
                } else {
                    host.edit(ownId = block.id) { s -> s.copy(blocks = s.blocks.map { if (it.id == block.id) it.copy(data = data, attachmentId = preview.id) else it }) }
                }
            }.onFailure(::failed)
        }
    }

    suspend fun readDrawing(blockId: String): Drawing {
        val block = host.state.blocks.firstOrNull { it.id == blockId } ?: return Drawing()
        val vector = RichBlocks.drawing(block.toNoteBlock()).vectorId ?: return Drawing()
        return attachments?.readDrawing(vector) ?: Drawing()
    }

    private fun attachmentOf(blockId: String): Attachment? {
        val s = host.state
        val id = s.blocks.firstOrNull { it.id == blockId }?.attachmentId ?: return null
        return s.attachments[id]
    }

    /** OCR of a photo or scan (#17). [quiet] skips the "nothing found" message. */
    fun recognizeText(blockId: String, quiet: Boolean = false) {
        val repo = attachments ?: return
        val engine = textRecognition ?: return
        host.scope.launch {
            work(blockId, RichWork.RECOGNIZING)
            try {
                val attachment = attachmentOf(blockId) ?: blockAttachment(blockId) ?: return@launch
                val text = runCatchingSafely { engine.recognize(repo.file(attachment)) }.getOrNull()
                if (text != null) repo.setOcrText(attachment.id, text) else if (!quiet) host.message(RichMessage.NOTHING_RECOGNIZED)
            } finally {
                work(blockId, null)
            }
        }
    }

    /** The attachment of a block that was just inserted (before the observed list caught up). */
    private suspend fun blockAttachment(blockId: String): Attachment? {
        val id = host.state.blocks.firstOrNull { it.id == blockId }?.attachmentId ?: return null
        return attachments?.get(id)
    }

    /** Transcribes a recording (#19): the file on Android 13+, otherwise live dictation. */
    fun transcribe(blockId: String, languageTag: String) {
        val repo = attachments ?: return
        val service = speech ?: return
        host.scope.launch {
            val attachment = attachmentOf(blockId) ?: blockAttachment(blockId) ?: return@launch
            if (!service.canTranscribeFiles() && !service.canDictate()) {
                host.message(RichMessage.TRANSCRIPTION_UNAVAILABLE)
                return@launch
            }
            work(blockId, RichWork.TRANSCRIBING)
            try {
                val text = runCatchingSafely {
                    if (service.canTranscribeFiles()) service.transcribeFile(repo.file(attachment), languageTag) else service.dictate(languageTag)
                }.getOrNull()
                if (text.isNullOrBlank()) host.message(RichMessage.NOTHING_RECOGNIZED) else repo.setTranscript(attachment.id, text)
            } finally {
                work(blockId, null)
            }
        }
    }

    fun setTranscript(blockId: String, text: String) {
        val repo = attachments ?: return
        host.scope.launch {
            val attachment = attachmentOf(blockId) ?: return@launch
            runCatchingSafely { repo.setTranscript(attachment.id, text) }
        }
    }

    fun setRecognizedText(blockId: String, text: String) {
        val repo = attachments ?: return
        host.scope.launch {
            val attachment = attachmentOf(blockId) ?: return@launch
            runCatchingSafely { repo.setOcrText(attachment.id, text) }
        }
    }

    /**
     * Handwriting to text (#18). A missing model is downloaded only after the user agreed
     * ([consented]); the recognized text goes into a new text block below the drawing.
     */
    fun convertHandwriting(blockId: String, language: String, consented: Boolean = false, wifiOnly: Boolean = false) {
        val engine = handwriting ?: return
        host.scope.launch {
            host.update { it.copy(handwritingConsent = null) }
            val ready = runCatchingSafely { engine.isModelReady(language) }.getOrDefault(false)
            if (!ready) {
                if (!consented) {
                    host.update { it.copy(handwritingConsent = HandwritingConsent(blockId, language)) }
                    return@launch
                }
                work(blockId, RichWork.DOWNLOADING_MODEL)
                val ok = runCatchingSafely { engine.downloadModel(language, wifiOnly) }.getOrDefault(false)
                if (!ok) {
                    work(blockId, null)
                    host.message(RichMessage.HANDWRITING_UNAVAILABLE)
                    return@launch
                }
            }
            work(blockId, RichWork.CONVERTING)
            try {
                val drawing = readDrawing(blockId)
                val text = runCatchingSafely { engine.recognize(drawing, language) }.getOrNull()
                if (text.isNullOrBlank()) {
                    host.message(RichMessage.NOTHING_RECOGNIZED)
                } else {
                    host.update { it.copy(focusId = blockId) }
                    insert(BlockType.TEXT, text = text)
                }
            } finally {
                work(blockId, null)
            }
        }
    }

    fun dismissConsent() = host.update { it.copy(handwritingConsent = null) }
}

/** The stored form of an editor block. */
internal fun EditorBlock.toNoteBlock() =
    com.behnamjalali.planb.core.model.NoteBlock(id, type, value.text, checked, data, attachmentId)
