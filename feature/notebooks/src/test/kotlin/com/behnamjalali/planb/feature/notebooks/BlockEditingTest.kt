package com.behnamjalali.planb.feature.notebooks

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
        assertThat(fileName("گزارش: هفته/۱", "md")).isEqualTo("گزارش هفته ۱.md")
        assertThat(fileName("  ", "txt")).isEqualTo("Plan-B note.txt")
    }
}
