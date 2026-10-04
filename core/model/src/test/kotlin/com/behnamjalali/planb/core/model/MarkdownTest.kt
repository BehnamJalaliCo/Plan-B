package com.behnamjalali.planb.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MarkdownTest {
    private var counter = 0
    private val ids: () -> String = { "b${counter++}" }

    private val doc = NoteDocument(
        blocks = listOf(
            NoteBlock("1", BlockType.HEADING, "برنامهٔ هفته"),
            NoteBlock("2", BlockType.TEXT, "Mixed متن with English"),
            NoteBlock("3", BlockType.CHECKLIST, "خرید", checked = true),
            NoteBlock("4", BlockType.CHECKLIST, "Call Sara"),
            NoteBlock("5", BlockType.NUMBERED, "first"),
            NoteBlock("6", BlockType.NUMBERED, "second"),
            NoteBlock("7", BlockType.BULLET, "bullet"),
            NoteBlock("8", BlockType.QUOTE, "line one\nline two"),
            NoteBlock("9", BlockType.DIVIDER),
            NoteBlock("10", BlockType.CODE, "val x = 1\n\nprintln(x)"),
        ),
    )

    @Test
    fun export_producesExpectedMarkdown() {
        val md = Markdown.export("یادداشت", doc)
        assertThat(md).contains("# یادداشت")
        assertThat(md).contains("## برنامهٔ هفته")
        assertThat(md).contains("- [x] خرید\n- [ ] Call Sara")
        assertThat(md).contains("1. first\n2. second")
        assertThat(md).contains("> line one\n> line two")
        assertThat(md).contains("```\nval x = 1\n\nprintln(x)\n```")
    }

    @Test
    fun roundTrip_preservesBlockTypesAndText() {
        val imported = Markdown.import(Markdown.export("Title", doc), "fallback", ids)
        assertThat(imported.title).isEqualTo("Title")
        assertThat(imported.document.blocks.map { it.type to it.text })
            .isEqualTo(doc.blocks.map { it.type to it.text })
        assertThat(imported.document.blocks.filter { it.type == BlockType.CHECKLIST }.map { it.checked }).containsExactly(true, false)
    }

    @Test
    fun import_paragraphsAndFallbackTitle() {
        val imported = Markdown.import("first line\nsecond line\n\n* star bullet\n3) numbered\n***", "Untitled", ids)
        assertThat(imported.title).isEqualTo("Untitled")
        assertThat(imported.document.blocks.map { it.type }).containsExactly(
            BlockType.TEXT, BlockType.BULLET, BlockType.NUMBERED, BlockType.DIVIDER,
        ).inOrder()
        assertThat(imported.document.blocks.first().text).isEqualTo("first line\nsecond line")
    }

    @Test
    fun import_unclosedFence_keepsContent() {
        val imported = Markdown.import("```\ncode only", "t", ids)
        assertThat(imported.document.blocks.single().text).isEqualTo("code only")
    }

    @Test
    fun plainText_export() {
        val text = Markdown.plainText("T", doc)
        assertThat(text).contains("☑ خرید")
        assertThat(text).contains("2. second")
    }
}
