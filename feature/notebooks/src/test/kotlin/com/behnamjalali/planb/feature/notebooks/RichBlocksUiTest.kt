package com.behnamjalali.planb.feature.notebooks

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.rich.ChartData
import com.behnamjalali.planb.core.model.rich.ChartPoint
import com.behnamjalali.planb.core.model.rich.ColumnType
import com.behnamjalali.planb.core.model.rich.DatabaseData
import com.behnamjalali.planb.core.model.rich.DbColumn
import com.behnamjalali.planb.core.model.rich.DbRow
import com.behnamjalali.planb.core.model.rich.MathParser
import com.behnamjalali.planb.core.model.rich.RichBlocks
import com.behnamjalali.planb.core.model.rich.TableData
import com.behnamjalali.planb.core.ui.LocalDateFormatter
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.core.ui.LocalToday
import com.behnamjalali.planb.core.ui.ProAccess
import com.behnamjalali.planb.core.ui.rememberDateFormatter
import com.behnamjalali.planb.feature.notebooks.rich.ChartBlock
import com.behnamjalali.planb.feature.notebooks.rich.DatabaseBlock
import com.behnamjalali.planb.feature.notebooks.rich.MathFormula
import com.behnamjalali.planb.feature.notebooks.rich.RichEditor
import com.behnamjalali.planb.feature.notebooks.rich.RichMessage
import com.behnamjalali.planb.feature.notebooks.rich.RichUi
import com.behnamjalali.planb.feature.notebooks.rich.TableBlock
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Rendering and accessibility of rich blocks (English, Gregorian, Latin digits). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
class RichBlocksUiTest {
    @get:Rule val compose = createComposeRule()

    /** An editor state the blocks write into, like the ViewModel's. */
    private class Host(blocks: List<EditorBlock>) : RichEditor.Host {
        override val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        override var state = NoteEditorState(loading = false, blocks = blocks)
        override fun edit(ownId: String?, transform: (NoteEditorState) -> NoteEditorState) {
            state = transform(state)
        }
        override fun update(transform: (NoteEditorState) -> NoteEditorState) {
            state = transform(state)
        }
        override fun message(message: RichMessage) = Unit
    }

    private fun ui(host: Host) = RichUi(
        RichEditor(host, null, null, null, null, null) { UUID.randomUUID().toString() },
        ApplicationProvider.getApplicationContext(),
        host.scope,
    )

    @Composable
    private fun Themed(pro: Boolean = true, content: @Composable () -> Unit) {
        PlanBTheme(animationsEnabled = false) {
            val formatter = rememberDateFormatter(CalendarSystem.GREGORIAN, DayOfWeek.MONDAY, persianDigits = false)
            CompositionLocalProvider(
                LocalDateFormatter provides formatter,
                LocalToday provides LocalDate.of(2026, 10, 5),
                LocalProAccess provides ProAccess(isPro = pro) {},
            ) { content() }
        }
    }

    @Test
    fun formulas_stackFractionsAndShrinkScripts() {
        compose.setContent {
            Themed {
                Column {
                    MathFormula(MathParser.parse("a"), 20.sp, Color.Black, Modifier.testTag("plain"))
                    MathFormula(MathParser.parse("\\frac{a}{b}"), 20.sp, Color.Black, Modifier.testTag("fraction"))
                    MathFormula(MathParser.parse("\\frac{\\frac{1}{x}}{y}"), 20.sp, Color.Black, Modifier.testTag("nested"))
                    MathFormula(MathParser.parse("\\begin{pmatrix} 1 & 2 \\\\ 3 & 4 \\\\ 5 & 6 \\end{pmatrix}"), 20.sp, Color.Black, Modifier.testTag("matrix"))
                }
            }
        }
        fun height(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().let { it.bottom - it.top }
        assertThat(height("fraction")).isGreaterThan(height("plain"))
        assertThat(height("nested")).isGreaterThan(height("fraction"))
        assertThat(height("matrix")).isGreaterThan(height("fraction"))
    }

    @Test
    fun tableCells_areAnnounced_andRowOperationsAreTalkBackActions() {
        val block = EditorBlock("t", BlockType.TABLE, TextFieldValue(""), data = RichBlocks.encode(TableData(listOf(listOf("Item", "Price"), listOf("Tea", "3")))))
        val host = Host(listOf(block))
        val ui = ui(host)
        compose.setContent { Themed { Column { TableBlock(block, ui, editable = true) } } }
        val cell = compose.onNodeWithContentDescription("Row 2, column 2")
        cell.assertExists()
        val actions = cell.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertThat(actions.map { it.label }).containsExactly("Insert row below", "Delete row", "Insert column after", "Delete column").inOrder()
        compose.runOnIdle { actions.first { it.label == "Insert row below" }.action() }
        compose.waitForIdle()
        val saved = RichBlocks.table(host.state.blocks.single().toNoteBlockForUiTest())
        assertThat(saved.rowCount).isEqualTo(3)
        assertThat(saved.rows[1]).containsExactly("Tea", "3").inOrder()
    }

    @Test
    fun freeUsers_seeTablesReadOnly() {
        val block = EditorBlock("t", BlockType.TABLE, TextFieldValue(""), data = RichBlocks.encode(TableData(listOf(listOf("Item"), listOf("Tea")))))
        compose.setContent { Themed(pro = false) { Column { TableBlock(block, null, editable = false) } } }
        compose.onNodeWithText("Tea").assertExists()
        val node = compose.onNodeWithContentDescription("Row 2, column 1").fetchSemanticsNode()
        assertThat(node.config.contains(SemanticsActions.CustomActions)).isFalse()
        compose.onNodeWithText("Add row").assertDoesNotExist()
    }

    @Test
    fun charts_haveASpokenSummaryWithEveryValue() {
        val chart = EditorBlock("c", BlockType.CHART, TextFieldValue(""), data = RichBlocks.encode(ChartData(title = "Sales", points = listOf(ChartPoint("Q1", 3.0), ChartPoint("Q2", 4.5)))))
        compose.setContent { Themed { Column { ChartBlock(chart, listOf(chart), null, editable = false) } } }
        compose.onNodeWithContentDescription("Bar chart Sales: Q1 3, Q2 4.5").assertExists()
    }

    @Test
    fun charts_drawADatabaseOfTheSameNote() {
        val db = EditorBlock(
            "db", BlockType.DATABASE, TextFieldValue(""),
            data = RichBlocks.encode(
                DatabaseData(
                    columns = listOf(DbColumn("m", "Month"), DbColumn("v", "Sales", ColumnType.NUMBER)),
                    rows = listOf(DbRow("1", mapOf("m" to "Jan", "v" to "10")), DbRow("2", mapOf("m" to "Feb", "v" to "12"))),
                ),
            ),
        )
        val chart = EditorBlock(
            "c", BlockType.CHART, TextFieldValue(""),
            data = RichBlocks.encode(ChartData(kind = com.behnamjalali.planb.core.model.rich.ChartKind.LINE, source = com.behnamjalali.planb.core.model.rich.ChartSource("db", "m", "v"))),
        )
        compose.setContent { Themed { Column { ChartBlock(chart, listOf(db, chart), null, editable = false) } } }
        compose.onNodeWithContentDescription("Line chart: Jan 10, Feb 12").assertExists()
    }

    @Test
    fun databases_showSumsAndRowCounts() {
        val db = EditorBlock(
            "db", BlockType.DATABASE, TextFieldValue(""),
            data = RichBlocks.encode(
                DatabaseData(
                    columns = listOf(DbColumn("n", "Item"), DbColumn("p", "Price", ColumnType.NUMBER), DbColumn("d", "Paid", ColumnType.CHECKBOX)),
                    rows = listOf(DbRow("1", mapOf("n" to "Tea", "p" to "3.5", "d" to "true")), DbRow("2", mapOf("n" to "Cake", "p" to "12"))),
                ),
            ),
        )
        compose.setContent { Themed { Column { DatabaseBlock(db, null, editable = false) } } }
        compose.onNodeWithText("Sum 15.5").assertExists()
        compose.onNodeWithText("2 rows").assertExists()
        compose.onNodeWithContentDescription("Paid: checked").assertExists()
    }

}

private fun EditorBlock.toNoteBlockForUiTest() = com.behnamjalali.planb.core.model.NoteBlock(id, type, value.text, checked, data, attachmentId)
