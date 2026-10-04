package com.behnamjalali.planb.feature.notebooks

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.behnamjalali.planb.core.model.BlockType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BlockEditingTest {
    private var n = 0
    private val ids: () -> String = { "n${n++}" }
    private fun block(id: String, type: BlockType, text: String) = EditorBlock(id, type, TextFieldValue(text))

    @Test
    fun enter_splitsTextBlock() {
        val blocks = listOf(block("a", BlockType.TEXT, "hello"))
        val r = BlockEditing.change(blocks, "a", TextFieldValue("hel\nlo"), ids)
        assertThat(r.blocks.map { it.value.text }).containsExactly("hel", "lo").inOrder()
        assertThat(r.focusId).isEqualTo(r.blocks[1].id)
        assertThat(r.cursor).isEqualTo(0)
    }

    @Test
    fun enter_inChecklist_continuesList_andEmptyItemEndsList() {
        val blocks = listOf(block("a", BlockType.CHECKLIST, "milk"))
        val r = BlockEditing.change(blocks, "a", TextFieldValue("milk\n"), ids)
        assertThat(r.blocks.map { it.type }).containsExactly(BlockType.CHECKLIST, BlockType.CHECKLIST)
        val second = r.blocks[1].id
        val exit = BlockEditing.change(r.blocks, second, TextFieldValue("\n"), ids)
        assertThat(exit.blocks.map { it.type }).containsExactly(BlockType.CHECKLIST, BlockType.TEXT).inOrder()
    }

    @Test
    fun heading_enter_createsText() {
        val r = BlockEditing.change(listOf(block("a", BlockType.HEADING, "Title")), "a", TextFieldValue("Title\n"), ids)
        assertThat(r.blocks[1].type).isEqualTo(BlockType.TEXT)
    }

    @Test
    fun codeBlock_keepsNewlines() {
        val r = BlockEditing.change(listOf(block("a", BlockType.CODE, "x")), "a", TextFieldValue("x\ny"), ids)
        assertThat(r.blocks.single().value.text).isEqualTo("x\ny")
    }

    @Test
    fun pasteMultiline_createsBlocksPerLine() {
        val r = BlockEditing.change(listOf(block("a", BlockType.BULLET, "")), "a", TextFieldValue("one\ntwo\nسه"), ids)
        assertThat(r.blocks.map { it.value.text }).containsExactly("one", "two", "سه").inOrder()
        assertThat(r.blocks.all { it.type == BlockType.BULLET }).isTrue()
    }

    @Test
    fun backspaceAtStart_mergesAndConverts() {
        val blocks = listOf(block("a", BlockType.TEXT, "foo"), block("b", BlockType.TEXT, "bar"), block("c", BlockType.BULLET, "x"))
        val converted = BlockEditing.backspaceAtStart(blocks, "c")
        assertThat(converted.blocks[2].type).isEqualTo(BlockType.TEXT)
        val merged = BlockEditing.backspaceAtStart(blocks, "b")
        assertThat(merged.blocks.map { it.value.text }).containsExactly("foobar", "x").inOrder()
        assertThat(merged.focusId).isEqualTo("a")
        assertThat(merged.cursor).isEqualTo(3)
    }

    @Test
    fun ensureNotEmpty_addsTextBlock() {
        assertThat(BlockEditing.ensureNotEmpty(emptyList(), ids)).hasSize(1)
        assertThat(BlockEditing.ensureNotEmpty(listOf(block("d", BlockType.DIVIDER, "")), ids)).hasSize(2)
    }

    @Test
    fun fileName_sanitizesTitle() {
        assertThat(fileName("گزارش: هفته/۱", "md", "Plan-B note")).isEqualTo("گزارش هفته ۱.md")
        assertThat(fileName("  ", "txt", "Plan-B note")).isEqualTo("Plan-B note.txt")
        assertThat(fileName("  ", "txt", "یادداشت")).isEqualTo("یادداشت.txt")
    }

    @Test
    fun typingInAMultiLineBlock_doesNotSplitIt() {
        val blocks = listOf(block("a", BlockType.QUOTE, "line one\nline two"))
        val r = BlockEditing.change(blocks, "a", TextFieldValue("line one\nline two!", TextRange(18)), ids)
        assertThat(r.blocks.map { it.value.text }).containsExactly("line one\nline two!")
        assertThat(r.focusId).isNull()
    }

    @Test
    fun enterInAMultiLineBlock_splitsOnlyAtTheCaret() {
        val blocks = listOf(block("a", BlockType.TEXT, "one\ntwo three"))
        val r = BlockEditing.change(blocks, "a", TextFieldValue("one\ntwo\n three", TextRange(8)), ids)
        assertThat(r.blocks.map { it.value.text }).containsExactly("one\ntwo", " three").inOrder()
        assertThat(r.focusId).isEqualTo(r.blocks[1].id)
        assertThat(r.cursor).isEqualTo(0)
    }

    @Test
    fun enterAtStartOfBlock_insertsEmptyBlockAbove_andKeepsThisOne() {
        val blocks = listOf(EditorBlock("a", BlockType.CHECKLIST, TextFieldValue("milk"), checked = true))
        val r = BlockEditing.change(blocks, "a", TextFieldValue("\nmilk", TextRange(1)), ids)
        assertThat(r.blocks.map { it.value.text }).containsExactly("", "milk").inOrder()
        assertThat(r.blocks[0].type).isEqualTo(BlockType.CHECKLIST)
        assertThat(r.blocks[0].checked).isFalse()
        assertThat(r.blocks[1]).isEqualTo(blocks[0].copy(value = TextFieldValue("milk", TextRange(0))))
        assertThat(r.focusId).isEqualTo("a")
        assertThat(r.cursor).isEqualTo(0)

        val heading = BlockEditing.change(listOf(block("h", BlockType.HEADING, "Title")), "h", TextFieldValue("\nTitle", TextRange(1)), ids)
        assertThat(heading.blocks.map { it.type }).containsExactly(BlockType.HEADING, BlockType.HEADING)
        assertThat(heading.blocks.map { it.value.text }).containsExactly("", "Title").inOrder()
    }

    @Test
    fun codeBlock_neverSplits_evenOnEnterAtStart() {
        val r = BlockEditing.change(listOf(block("a", BlockType.CODE, "x")), "a", TextFieldValue("\nx", TextRange(1)), ids)
        assertThat(r.blocks.single().value.text).isEqualTo("\nx")
    }

    @Test
    fun mergeWithPrevious_joinsAnyTypeAndKeepsThePreviousType() {
        val blocks = listOf(block("a", BlockType.HEADING, "Head"), block("b", BlockType.BULLET, "item"), block("d", BlockType.DIVIDER, ""), block("c", BlockType.TEXT, "c"))
        val merged = BlockEditing.mergeWithPrevious(blocks, "b")
        assertThat(merged.blocks.map { it.value.text }).containsExactly("Headitem", "", "c").inOrder()
        assertThat(merged.blocks[0].type).isEqualTo(BlockType.HEADING)
        assertThat(merged.focusId).isEqualTo("a")
        assertThat(merged.cursor).isEqualTo(4)
        assertThat(BlockEditing.mergeWithPrevious(blocks, "c").blocks.map { it.id }).containsExactly("a", "b", "c").inOrder()
        assertThat(BlockEditing.mergeWithPrevious(blocks, "a").focusId).isNull()
    }
}
