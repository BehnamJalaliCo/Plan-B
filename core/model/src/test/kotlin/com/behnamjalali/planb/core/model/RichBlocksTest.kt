package com.behnamjalali.planb.core.model

import com.behnamjalali.planb.core.model.rich.ChartData
import com.behnamjalali.planb.core.model.rich.ChartKind
import com.behnamjalali.planb.core.model.rich.ChartPoint
import com.behnamjalali.planb.core.model.rich.ColumnType
import com.behnamjalali.planb.core.model.rich.DatabaseData
import com.behnamjalali.planb.core.model.rich.DbColumn
import com.behnamjalali.planb.core.model.rich.DbRow
import com.behnamjalali.planb.core.model.rich.Drawing
import com.behnamjalali.planb.core.model.rich.DrawingRef
import com.behnamjalali.planb.core.model.rich.RichBlocks
import com.behnamjalali.planb.core.model.rich.Stroke
import com.behnamjalali.planb.core.model.rich.TableData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RichBlocksTest {
    private val table = TableData(listOf(listOf("نام", "Price"), listOf("کتاب", "120")))
    private val database = DatabaseData(
        columns = listOf(DbColumn("n", "Item"), DbColumn("p", "Price", ColumnType.NUMBER), DbColumn("d", "Done", ColumnType.CHECKBOX)),
        rows = listOf(DbRow("r1", mapOf("n" to "دفتر", "p" to "45", "d" to "true"))),
    )
    private val doc = NoteDocument(
        blocks = listOf(
            NoteBlock("t", BlockType.TEXT, "سلام"),
            NoteBlock("i", BlockType.IMAGE, "caption", attachmentId = 7),
            NoteBlock("f", BlockType.FILE, "report.pdf", attachmentId = 8),
            NoteBlock("tb", BlockType.TABLE, data = RichBlocks.encode(table)),
            NoteBlock("dr", BlockType.DRAWING, attachmentId = 9, data = RichBlocks.encode(DrawingRef(vectorId = 10))),
            NoteBlock("s", BlockType.SCAN, attachmentId = 11),
            NoteBlock("a", BlockType.AUDIO, attachmentId = 12),
            NoteBlock("db", BlockType.DATABASE, data = RichBlocks.encode(database)),
            NoteBlock("m", BlockType.MATH, "\\frac{a}{b}"),
            NoteBlock("c", BlockType.CHART, data = RichBlocks.encode(ChartData(ChartKind.PIE, "Budget", listOf(ChartPoint("rent", 5.0))))),
        ),
    )

    @Test
    fun everyBlockKind_roundTripsThroughJson() {
        val decoded = NoteDocument.decode(doc.encode())
        assertThat(decoded).isEqualTo(doc)
        assertThat(RichBlocks.table(decoded.blocks[3])).isEqualTo(table)
        assertThat(RichBlocks.database(decoded.blocks[7])).isEqualTo(database)
        assertThat(RichBlocks.chart(decoded.blocks[9]).kind).isEqualTo(ChartKind.PIE)
    }

    @Test
    fun oldNotes_decodeUnchanged_andUnknownKindsDegradeToText() {
        val old = """{"version":1,"blocks":[{"id":"1","type":"heading","text":"H"},{"id":"2","type":"checklist","text":"x","checked":true}]}"""
        assertThat(NoteDocument.decode(old).blocks.map { it.type }).containsExactly(BlockType.HEADING, BlockType.CHECKLIST).inOrder()
        // A note written by a newer app keeps its blocks; a kind this version does not know shows its text.
        val newer = """{"version":1,"blocks":[{"id":"1","type":"hologram","text":"keep me","future":{"x":1}},{"id":"2","type":"math","text":"x^2"}]}"""
        val decoded = NoteDocument.decode(newer)
        assertThat(decoded.blocks.map { it.type to it.text }).containsExactly(BlockType.TEXT to "keep me", BlockType.MATH to "x^2").inOrder()
    }

    @Test
    fun textBlocks_encodeWithoutRichFields() {
        val encoded = NoteDocument(blocks = listOf(NoteBlock("1", BlockType.TEXT, "a"))).encode()
        assertThat(encoded).isEqualTo("""{"blocks":[{"id":"1","text":"a"}]}""")
    }

    @Test
    fun plainText_includesSearchableRichText() {
        val text = doc.plainText()
        assertThat(text).contains("سلام")
        assertThat(text).contains("کتاب 120")
        assertThat(text).contains("دفتر 45")
        assertThat(text).contains("Budget rent")
        assertThat(text).contains("\\frac{a}{b}")
        assertThat(text).doesNotContain("true")
    }

    @Test
    fun richBlocks_areNeverBlank_andListTheirAttachments() {
        assertThat(NoteDocument(blocks = listOf(NoteBlock("1", BlockType.TABLE))).isBlank()).isFalse()
        assertThat(NoteDocument(blocks = listOf(NoteBlock("1", BlockType.TEXT, " "))).isBlank()).isTrue()
        assertThat(doc.attachmentIds()).containsExactly(7L, 8L, 9L, 10L, 11L, 12L)
    }

    @Test
    fun remapAttachments_coversImagesAndDrawingVectors() {
        val map = mapOf(9L to 90L, 10L to 100L, 7L to 70L)
        val remapped = doc.blocks.map { RichBlocks.remapAttachments(it, map) }
        assertThat(NoteDocument(blocks = remapped).attachmentIds()).containsExactly(70L, 8L, 90L, 100L, 11L, 12L)
    }

    @Test
    fun damagedPayloads_fallBackToEmptyContent() {
        val broken = NoteBlock("x", BlockType.TABLE, data = kotlinx.serialization.json.buildJsonObject { put("rows", kotlinx.serialization.json.JsonPrimitive("oops")) })
        assertThat(RichBlocks.table(broken).rowCount).isAtLeast(1)
        assertThat(Drawing.decode("not json")).isEqualTo(Drawing())
        assertThat(RichBlocks.database(NoteBlock("y", BlockType.DATABASE)).columns).hasSize(1)
    }

    @Test
    fun drawing_roundTripsCompactly() {
        val drawing = Drawing(strokes = listOf(Stroke(0xFF112233, 4f, listOf(1, 2, 50, 3, 4, 100))))
        val encoded = drawing.encode()
        assertThat(encoded).contains("\"p\":[1,2,50,3,4,100]")
        assertThat(Drawing.decode(encoded)).isEqualTo(drawing)
    }
}
