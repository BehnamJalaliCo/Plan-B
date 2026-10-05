package com.behnamjalali.planb.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class BlockType {
    @SerialName("text") TEXT,
    @SerialName("heading") HEADING,
    @SerialName("checklist") CHECKLIST,
    @SerialName("bullet") BULLET,
    @SerialName("numbered") NUMBERED,
    @SerialName("quote") QUOTE,
    @SerialName("divider") DIVIDER,
    @SerialName("code") CODE,
}

@Serializable
data class NoteBlock(
    val id: String,
    val type: BlockType = BlockType.TEXT,
    val text: String = "",
    val checked: Boolean = false,
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
    /** Plain text used for previews and the search index; links to notes read as their titles. */
    fun plainText(): String = blocks.filter { it.type != BlockType.DIVIDER }
        .joinToString("\n") { NoteLinks.plain(it.text) }
        .trim()

    fun isBlank(): Boolean = blocks.all { it.text.isBlank() }

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
