package com.behnamjalali.planb.core.model

import com.behnamjalali.planb.core.model.rich.ChartData
import com.behnamjalali.planb.core.model.rich.ChartMapping
import com.behnamjalali.planb.core.model.rich.ChartPoint
import com.behnamjalali.planb.core.model.rich.ChartSource
import com.behnamjalali.planb.core.model.rich.ColumnType
import com.behnamjalali.planb.core.model.rich.DatabaseData
import com.behnamjalali.planb.core.model.rich.DbColumn
import com.behnamjalali.planb.core.model.rich.DbRow
import com.behnamjalali.planb.core.model.rich.DbSort
import com.behnamjalali.planb.core.model.rich.Drawing
import com.behnamjalali.planb.core.model.rich.DrawingHistory
import com.behnamjalali.planb.core.model.rich.MathBigOperator
import com.behnamjalali.planb.core.model.rich.MathFenced
import com.behnamjalali.planb.core.model.rich.MathFraction
import com.behnamjalali.planb.core.model.rich.MathMatrix
import com.behnamjalali.planb.core.model.rich.MathParser
import com.behnamjalali.planb.core.model.rich.MathRoot
import com.behnamjalali.planb.core.model.rich.MathRow
import com.behnamjalali.planb.core.model.rich.MathScripts
import com.behnamjalali.planb.core.model.rich.MathSpeech
import com.behnamjalali.planb.core.model.rich.MathText
import com.behnamjalali.planb.core.model.rich.MathTextKind
import com.behnamjalali.planb.core.model.rich.RichBlocks
import com.behnamjalali.planb.core.model.rich.Stroke
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MathChartDrawingTest {
    @Test
    fun math_fractionsScriptsAndRoots() {
        assertThat(MathParser.parse("\\frac{a}{b}")).isEqualTo(MathFraction(MathText("a"), MathText("b")))
        assertThat(MathParser.parse("x^{2}")).isEqualTo(MathScripts(MathText("x"), sup = MathText("2", MathTextKind.NUMBER)))
        assertThat(MathParser.parse("a_i^2")).isEqualTo(MathScripts(MathText("a"), MathText("i"), MathText("2", MathTextKind.NUMBER)))
        assertThat(MathParser.parse("\\sqrt[3]{x+1}")).isEqualTo(
            MathRoot(MathRow(listOf(MathText("x"), MathText("+", MathTextKind.OPERATOR), MathText("1", MathTextKind.NUMBER))), MathText("3", MathTextKind.NUMBER)),
        )
        assertThat(MathParser.parse("12.5")).isEqualTo(MathText("12.5", MathTextKind.NUMBER))
    }

    @Test
    fun math_greekOperatorsAndLimits() {
        val sum = MathParser.parse("\\sum_{i=1}^{n} i") as MathRow
        val op = sum.items.first() as MathBigOperator
        assertThat(op.symbol).isEqualTo("∑")
        assertThat(op.upper).isEqualTo(MathText("n"))
        assertThat(op.limitsBeside).isFalse()
        assertThat((MathParser.parse("\\int_0^1") as MathBigOperator).limitsBeside).isTrue()
        assertThat(MathParser.parse("\\alpha")).isEqualTo(MathText("α"))
        assertThat(MathParser.parse("a \\leq b")).isEqualTo(MathRow(listOf(MathText("a"), MathText("≤", MathTextKind.OPERATOR), MathText("b"))))
        assertThat(MathParser.parse("\\sin")).isEqualTo(MathText("sin", MathTextKind.FUNCTION))
        assertThat(MathParser.parse("\\text{if } x")).isEqualTo(MathRow(listOf(MathText("if ", MathTextKind.TEXT), MathText("x"))))
    }

    @Test
    fun math_matricesAndFences() {
        val m = MathParser.parse("\\begin{pmatrix} 1 & 2 \\\\ 3 & 4 \\end{pmatrix}") as MathMatrix
        assertThat(m.open to m.close).isEqualTo("(" to ")")
        assertThat(m.rows).hasSize(2)
        assertThat(m.rows[1][1]).isEqualTo(MathText("4", MathTextKind.NUMBER))
        val fenced = MathParser.parse("\\left( \\frac{1}{2} \\right)") as MathFenced
        assertThat(fenced.open to fenced.close).isEqualTo("(" to ")")
        assertThat(fenced.content).isInstanceOf(MathFraction::class.java)
    }

    @Test
    fun math_neverFails_onBrokenInput() {
        listOf("\\frac{a", "}}{", "x^", "\\begin{matrix} 1 &", "\\unknown{z}", "\\left(", "", "\\", "^2", "\\sqrt[").forEach {
            MathParser.parse(it)
        }
        assertThat(MathParser.parse("\\unknown")).isEqualTo(MathText("unknown", MathTextKind.TEXT))
    }

    @Test
    fun math_speech_readsTheStructure() {
        assertThat(MathSpeech.describe(MathParser.parse("\\frac{a}{b}"))).isEqualTo("a over b")
        assertThat(MathSpeech.describe(MathParser.parse("\\sqrt{x}^2"))).isEqualTo("square root of x to the power 2")
        assertThat(MathSpeech.describe(MathParser.parse("\\sum_{i=1}^{n} i"))).isEqualTo("∑ from i = 1 to n i")
    }

    @Test
    fun chart_fromInlinePointsOrADatabase() {
        val db = DatabaseData(
            columns = listOf(DbColumn("m", "Month"), DbColumn("v", "Sales", ColumnType.NUMBER)),
            rows = listOf(DbRow("1", mapOf("m" to "فروردین", "v" to "۱۰")), DbRow("2", mapOf("m" to "اردیبهشت", "v" to "4")), DbRow("3", mapOf("v" to "x"))),
            sort = DbSort("v"),
        )
        val blocks = listOf(NoteBlock("db", BlockType.DATABASE, data = RichBlocks.encode(db)))
        val chart = ChartData(source = ChartSource("db", "m", "v"))
        assertThat(ChartMapping.resolve(chart, blocks)).containsExactly(
            ChartPoint("اردیبهشت", 4.0), ChartPoint("فروردین", 10.0), ChartPoint("3", 0.0),
        ).inOrder()
        // A missing source (deleted block, or not a number column) falls back to the inline points.
        assertThat(ChartMapping.resolve(chart, emptyList())).isEqualTo(chart.points)
        assertThat(ChartMapping.resolve(ChartData(source = ChartSource("db", null, "m")), blocks)).isEqualTo(ChartData().points)
    }

    @Test
    fun chart_scaleAndShares() {
        val scale = ChartMapping.scale(listOf(ChartPoint("a", 3.0), ChartPoint("b", 47.0)))
        assertThat(scale.min).isEqualTo(0.0)
        assertThat(scale.max).isAtLeast(47.0)
        assertThat(scale.ticks.first()).isEqualTo(0.0)
        assertThat(scale.fraction(scale.max)).isEqualTo(1.0)
        val negative = ChartMapping.scale(listOf(ChartPoint("a", -12.0), ChartPoint("b", 8.0)))
        assertThat(negative.min).isAtMost(-12.0)
        assertThat(ChartMapping.scale(emptyList()).max).isEqualTo(1.0)
        assertThat(ChartMapping.shares(listOf(ChartPoint("a", 1.0), ChartPoint("b", 3.0), ChartPoint("c", -2.0)))).containsExactly(0.25, 0.75, 0.0).inOrder()
        assertThat(ChartMapping.shares(listOf(ChartPoint("a", 0.0)))).containsExactly(0.0)
    }

    @Test
    fun drawing_eraserUndoRedoAndPointLimit() {
        val a = Stroke(0xFF000000, 4f, listOf(0, 0, 100, 100, 0, 100))
        val b = Stroke(0xFFFF0000, 4f, listOf(0, 500, 100, 100, 500, 100))
        val history = DrawingHistory(Drawing())
        history.push(history.current.plus(a))
        history.push(history.current.plus(b))
        assertThat(history.current.strokes).hasSize(2)
        history.push(history.current.eraseAt(50f, 3f, 10f))
        assertThat(history.current.strokes).containsExactly(b)
        assertThat(history.undo().strokes).hasSize(2)
        assertThat(history.canRedo).isTrue()
        assertThat(history.redo().strokes).containsExactly(b)
        history.undo()
        history.push(history.current.plus(a))
        assertThat(history.canRedo).isFalse()
        // Erasing far away changes nothing.
        assertThat(Drawing(strokes = listOf(a)).eraseAt(500f, 300f, 10f).strokes).hasSize(1)
        val huge = Stroke(0, 1f, List(Drawing.MAX_POINTS * 3) { 1 })
        assertThat(Drawing().plus(huge).plus(a).strokes).containsExactly(huge)
    }
}
