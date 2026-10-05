package com.behnamjalali.planb.core.model

import com.behnamjalali.planb.core.model.rich.ChartData
import com.behnamjalali.planb.core.model.rich.ChartPoint
import com.behnamjalali.planb.core.model.rich.ColumnType
import com.behnamjalali.planb.core.model.rich.DatabaseData
import com.behnamjalali.planb.core.model.rich.DbColumn
import com.behnamjalali.planb.core.model.rich.DbRow
import com.behnamjalali.planb.core.model.rich.RichBlocks
import com.behnamjalali.planb.core.model.rich.TableData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MarkdownRichTest {
    private var counter = 0
    private val ids: () -> String = { "b${counter++}" }

    private val table = TableData(listOf(listOf("کالا", "قیمت"), listOf("a|b", "line\nbreak")))
    private val doc = NoteDocument(
        blocks = listOf(
            NoteBlock("1", BlockType.TABLE, data = RichBlocks.encode(table)),
            NoteBlock("2", BlockType.MATH, "\\frac{a}{b}"),
            NoteBlock("3", BlockType.IMAGE, "Receipt photo", attachmentId = 5),
            NoteBlock("4", BlockType.AUDIO, attachmentId = 6),
            NoteBlock("5", BlockType.SCAN, attachmentId = 7),
            NoteBlock("6", BlockType.DATABASE, data = RichBlocks.encode(
                DatabaseData(
                    columns = listOf(DbColumn("n", "Item"), DbColumn("d", "Done", ColumnType.CHECKBOX)),
                    rows = listOf(DbRow("r", mapOf("n" to "milk", "d" to "true"))),
                ),
            )),
            NoteBlock("7", BlockType.CHART, data = RichBlocks.encode(ChartData(title = "Sales", points = listOf(ChartPoint("Q1", 2.0), ChartPoint("Q2", 2.5))))),
        ),
    )

    private val files = mapOf(
        5L to MarkdownAttachment("attachments/photo 1.jpg", "photo.jpg"),
        6L to MarkdownAttachment("attachments/voice.m4a", "Voice note", transcript = "سلام دنیا"),
        7L to MarkdownAttachment("attachments/scan.jpg", "scan", ocrText = "INVOICE 42"),
    )

    @Test
    fun export_richBlocks_asGfmMathAndLinks() {
        val md = Markdown.export("T", doc, attachments = { files[it] })
        assertThat(md).contains("| کالا | قیمت |\n| --- | --- |\n| a\\|b | line<br>break |")
        assertThat(md).contains("$$\n\\frac{a}{b}\n$$")
        assertThat(md).contains("![Receipt photo](attachments/photo%201.jpg)")
        assertThat(md).contains("[Voice note](attachments/voice.m4a)\n\n> سلام دنیا")
        assertThat(md).contains("![](attachments/scan.jpg)\n\n> INVOICE 42")
        assertThat(md).contains("| Item | Done |\n| --- | --- |\n| milk | [x] |")
        assertThat(md).contains("| Q1 | 2 |\n| Q2 | 2.5 |\n\n*Sales*")
    }

    @Test
    fun export_withoutFiles_namesThem() {
        val md = Markdown.export("T", doc, attachments = { id -> files[id]?.copy(path = null) })
        assertThat(md).contains("![Receipt photo](photo.jpg)")
    }

    @Test
    fun import_readsTablesAndFormulasBack() {
        val imported = Markdown.import(Markdown.export("T", doc, attachments = { files[it] }), "f", ids)
        val blocks = imported.document.blocks
        assertThat(blocks.first().type).isEqualTo(BlockType.TABLE)
        assertThat(RichBlocks.table(blocks.first())).isEqualTo(table)
        assertThat(blocks[1].type to blocks[1].text).isEqualTo(BlockType.MATH to "\\frac{a}{b}")
        assertThat(Markdown.import("$$ x^2 $$", "f", ids).document.blocks.single().text).isEqualTo("x^2")
        // A table without a header line keeps every row as data.
        val headless = Markdown.import("|  |  |\n|---|---|\n| 1 | 2 |", "f", ids).document.blocks.single()
        assertThat(RichBlocks.table(headless)).isEqualTo(TableData(listOf(listOf("1", "2")), header = false))
    }

    @Test
    fun paragraphsThatLookLikeTablesOrFormulas_roundTripAsText() {
        val text = NoteDocument(blocks = listOf(NoteBlock("1", BlockType.TEXT, "| not a table |\n|---|\n$$ money"))).let {
            Markdown.import(Markdown.export("", it), "f", ids).document.blocks
        }
        assertThat(text.map { it.type to it.text }).containsExactly(BlockType.TEXT to "| not a table |\n|---|\n$$ money")
    }

    @Test
    fun plainText_flattensRichBlocks() {
        val text = Markdown.plainText("T", doc)
        assertThat(text).contains("کالا\tقیمت")
        assertThat(text).contains("\\frac{a}{b}")
        assertThat(text).contains("Receipt photo")
    }
}
