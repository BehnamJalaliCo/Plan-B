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
    fun roundTrip_textThatLooksLikeMarkup_staysText() {
        val tricky = NoteDocument(
            blocks = listOf(
                NoteBlock("1", BlockType.TEXT, "# not a title"),
                NoteBlock("2", BlockType.TEXT, "1. Buy milk\n> not a quote\n- not a bullet\n***\n```\n\\- literal backslash"),
                NoteBlock("3", BlockType.BULLET, "--"),
                NoteBlock("4", BlockType.BULLET, "[x] literal"),
                NoteBlock("5", BlockType.BULLET, "\\[x] keeps its backslash"),
                NoteBlock("6", BlockType.CODE, "before\n```\nstill code\n````\nafter"),
                NoteBlock("7", BlockType.TEXT, "۱. فهرست نیست"),
                NoteBlock("8", BlockType.TEXT, "\\plain backslash"),
            ),
        )
        val md = Markdown.export("", tricky)
        assertThat(md).startsWith("\\# not a title")
        assertThat(md).contains("- \\--")
        assertThat(md).contains("`````\nbefore")
        val imported = Markdown.import(md, "fallback", ids)
        assertThat(imported.title).isEqualTo("fallback")
        assertThat(imported.document.blocks.map { it.type to it.text })
            .isEqualTo(tricky.blocks.map { it.type to it.text })
    }

    @Test
    fun import_persianAndArabicDigitNumberedItems() {
        val imported = Markdown.import("۱. مورد اول\n٢) مورد دوم\n10. ten", "t", ids)
        assertThat(imported.document.blocks.map { it.type to it.text }).containsExactly(
            BlockType.NUMBERED to "مورد اول",
            BlockType.NUMBERED to "مورد دوم",
            BlockType.NUMBERED to "ten",
        ).inOrder()
    }

    @Test
    fun import_longerFence_keepsInnerBackticks() {
        val imported = Markdown.import("````\n```kotlin\nval x = 1\n```\n````\nafter", "t", ids)
        assertThat(imported.document.blocks.map { it.type to it.text }).containsExactly(
            BlockType.CODE to "```kotlin\nval x = 1\n```",
            BlockType.TEXT to "after",
        ).inOrder()
    }

    @Test
    fun plainText_export() {
        val text = Markdown.plainText("T", doc)
        assertThat(text).contains("☑ خرید")
        assertThat(text).contains("2. second")
    }
}
