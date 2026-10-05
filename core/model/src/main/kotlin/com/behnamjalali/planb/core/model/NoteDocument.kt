package com.behnamjalali.planb.core.model

import com.behnamjalali.planb.core.model.rich.RichBlocks
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Block kinds. [key] is persisted in note JSON and must never change. Kinds after [CODE] are
 * the Plan-B Pro rich blocks (#15, #17, #19, #20, #23); a key this version does not know (a
 * note written by a newer app) is read as [TEXT], so the block's text still shows.
 */
@Serializable(with = BlockTypeSerializer::class)
enum class BlockType(val key: String) {
    TEXT("text"),
    HEADING("heading"),
    CHECKLIST("checklist"),
    BULLET("bullet"),
    NUMBERED("numbered"),
    QUOTE("quote"),
    DIVIDER("divider"),
    CODE("code"),

    /** A photo ([NoteBlock.attachmentId]); [NoteBlock.text] is its caption. */
    IMAGE("image"),

    /** Any file ([NoteBlock.attachmentId]); [NoteBlock.text] is its caption. */
    FILE("file"),

    /** An editable grid ([NoteBlock.data] = `TableData`). */
    TABLE("table"),

    /** A pen drawing: vector strokes in an attachment plus a PNG preview (`DrawingRef`). */
    DRAWING("drawing"),

    /** A scanned document page ([NoteBlock.attachmentId]) with recognized text. */
    SCAN("scan"),

    /** A voice recording ([NoteBlock.attachmentId]); its transcript lives on the attachment. */
    AUDIO("audio"),

    /** A typed database table ([NoteBlock.data] = `DatabaseData`). */
    DATABASE("database"),

    /** A formula; [NoteBlock.text] is its LaTeX-like source. */
    MATH("math"),

    /** A bar, line or pie chart ([NoteBlock.data] = `ChartData`). */
    CHART("chart"),
    ;

    /** Not a text block: edited through its own UI rather than a text field. */
    val isRich: Boolean get() = ordinal > CODE.ordinal

    companion object {
        fun fromKey(key: String?): BlockType = entries.firstOrNull { it.key == key } ?: TEXT
    }
}

/** Reads unknown keys as [BlockType.TEXT] instead of failing the whole document. */
internal object BlockTypeSerializer : KSerializer<BlockType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BlockType", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BlockType) = encoder.encodeString(value.key)
    override fun deserialize(decoder: Decoder): BlockType = BlockType.fromKey(decoder.decodeString())
}

@Serializable
data class NoteBlock(
    val id: String,
    val type: BlockType = BlockType.TEXT,
    val text: String = "",
    val checked: Boolean = false,
    /** Structured content of a rich block (see `core.model.rich`); absent for text blocks. */
    val data: JsonObject? = null,
    /** The `attachments` row (owner: this note) of an image, file, scan or recording block. */
    val attachmentId: Long? = null,
)

/**
 * Block-based note content. Blocks are stored as JSON so that each block's
 * semantics survive editing without lossy rich-text parsing.
 */
@Serializable
data class NoteDocument(
    val version: Int = CURRENT_VERSION,
    val blocks: List<NoteBlock> = emptyList(),
) {
    /**
     * Plain text used for previews and the search index: text blocks, captions, table and
     * database cells, chart labels and formulas (divider blocks excluded).
     */
    fun plainText(): String = blocks.filter { it.type != BlockType.DIVIDER }
        .joinToString("\n") { RichBlocks.plainText(it) }
        .trim()

    /** True when nothing was written; a rich block (an image, a table, …) always counts. */
    fun isBlank(): Boolean = blocks.all { !it.type.isRich && it.text.isBlank() }

    /** Every attachment the blocks refer to (images, files, recordings, drawings and scans). */
    fun attachmentIds(): Set<Long> = blocks.flatMapTo(mutableSetOf()) { RichBlocks.attachmentIds(it) }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val CURRENT_VERSION = 1
        val EMPTY = NoteDocument()

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        /** Never throws: malformed content is preserved as a single text block. */
        fun decode(raw: String?): NoteDocument {
            if (raw.isNullOrBlank()) return EMPTY
            return runCatching { json.decodeFromString(serializer(), raw) }
                .getOrElse { NoteDocument(blocks = listOf(NoteBlock(id = "recovered", text = raw))) }
        }
    }
}
