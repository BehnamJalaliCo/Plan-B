package com.behnamjalali.planb.core.model.rich

import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.NoteBlock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Encoding of rich block payloads ([NoteBlock.data]) and the plain-text view of every block.
 * Payloads are versionless JSON objects read with defaults for anything missing or unknown,
 * so a damaged or newer payload never throws: it falls back to an empty table, chart, ….
 */
object RichBlocks {
    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun <T> decode(serializer: KSerializer<T>, data: JsonObject?, fallback: T): T =
        if (data == null) fallback else runCatching { json.decodeFromJsonElement(serializer, data) }.getOrDefault(fallback)

    fun <T> encode(serializer: KSerializer<T>, value: T): JsonObject = json.encodeToJsonElement(serializer, value).jsonObject

    fun table(block: NoteBlock): TableData = decode(TableData.serializer(), block.data, TableData()).normalized()
    fun database(block: NoteBlock): DatabaseData = decode(DatabaseData.serializer(), block.data, DatabaseData.empty()).normalized()
    fun chart(block: NoteBlock): ChartData = decode(ChartData.serializer(), block.data, ChartData())
    fun drawing(block: NoteBlock): DrawingRef = decode(DrawingRef.serializer(), block.data, DrawingRef())
    fun audio(block: NoteBlock): AudioData = decode(AudioData.serializer(), block.data, AudioData())
    fun encode(value: AudioData): JsonObject = encode(AudioData.serializer(), value)

    fun encode(value: TableData): JsonObject = encode(TableData.serializer(), value)
    fun encode(value: DatabaseData): JsonObject = encode(DatabaseData.serializer(), value)
    fun encode(value: ChartData): JsonObject = encode(ChartData.serializer(), value)
    fun encode(value: DrawingRef): JsonObject = encode(DrawingRef.serializer(), value)

    /** The searchable, readable text of a block (used for previews, search and plain exports). */
    fun plainText(block: NoteBlock): String = when (block.type) {
        BlockType.TABLE -> table(block).rows.joinToString("\n") { row -> row.filter { it.isNotBlank() }.joinToString(" ") }
        BlockType.DATABASE -> database(block).let { db ->
            (listOf(db.columns.joinToString(" ") { it.name }) + db.rows.map { row ->
                db.columns.filter { it.type != ColumnType.CHECKBOX }.mapNotNull { row.cells[it.id]?.takeIf(String::isNotBlank) }.joinToString(" ")
            }).filter { it.isNotBlank() }.joinToString("\n")
        }
        BlockType.CHART -> chart(block).let { c -> (listOf(c.title) + c.points.map { it.label }).filter { it.isNotBlank() }.joinToString(" ") }
        else -> block.text
    }

    /** Attachments a block refers to; a drawing has its preview image and its vector file. */
    fun attachmentIds(block: NoteBlock): Set<Long> = buildSet {
        block.attachmentId?.let(::add)
        if (block.type == BlockType.DRAWING) drawing(block).vectorId?.let(::add)
    }

    /** The block with every attachment id replaced through [map] (ids without a mapping stay). */
    fun remapAttachments(block: NoteBlock, map: Map<Long, Long>): NoteBlock {
        if (map.isEmpty()) return block
        var result = block.copy(attachmentId = block.attachmentId?.let { map[it] ?: it })
        if (block.type == BlockType.DRAWING) {
            val ref = drawing(block)
            ref.vectorId?.let { old -> map[old]?.let { result = result.copy(data = encode(ref.copy(vectorId = it))) } }
        }
        return result
    }
}
