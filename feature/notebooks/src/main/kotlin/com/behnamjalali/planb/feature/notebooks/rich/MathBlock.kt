package com.behnamjalali.planb.feature.notebooks.rich

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.rich.MathBigOperator
import com.behnamjalali.planb.core.model.rich.MathFenced
import com.behnamjalali.planb.core.model.rich.MathFraction
import com.behnamjalali.planb.core.model.rich.MathMatrix
import com.behnamjalali.planb.core.model.rich.MathNode
import com.behnamjalali.planb.core.model.rich.MathParser
import com.behnamjalali.planb.core.model.rich.MathRoot
import com.behnamjalali.planb.core.model.rich.MathRow
import com.behnamjalali.planb.core.model.rich.MathScripts
import com.behnamjalali.planb.core.model.rich.MathSpeech
import com.behnamjalali.planb.core.model.rich.MathText
import com.behnamjalali.planb.core.model.rich.MathTextKind
import com.behnamjalali.planb.core.model.rich.MathWords
import com.behnamjalali.planb.feature.notebooks.EditorBlock
import com.behnamjalali.planb.feature.notebooks.R

/**
 * A formula (#23): LaTeX-like source, drawn natively with Compose text layout (no WebView).
 * The formula is always laid out left to right; TalkBack reads it in words.
 */
@Composable
internal fun MathBlock(block: EditorBlock, ui: RichUi?, editable: Boolean, focused: Boolean) {
    var source by remember(block.id, block.revision) { mutableStateOf(block.value.text) }
    var showSource by remember(block.id) { mutableStateOf(source.isBlank()) }
    val node = remember(source) { MathParser.parse(source) }
    val words = mathWords()
    val spoken = remember(node, words) { MathSpeech.describe(node, words) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(Radius.md), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .clearAndSetSemantics {
                        contentDescription = if (source.isBlank()) "" else spoken
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (source.isBlank()) {
                    Text(stringResource(R.string.rich_math_empty), color = MaterialTheme.colorScheme.outline)
                } else {
                    MathFormula(node, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (editable && ui != null && (showSource || focused)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val hint = stringResource(R.string.rich_math_hint)
                    val style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                    BasicTextField(
                        value = source,
                        onValueChange = {
                            source = it
                            ui.update(block.id, text = it)
                        },
                        textStyle = style,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
                        decorationBox = { inner ->
                            if (source.isEmpty()) Text(hint, style = style.copy(color = MaterialTheme.colorScheme.outline))
                            inner()
                        },
                    )
                }
            }
            if (editable && ui != null && !focused) {
                TextButton(onClick = { showSource = !showSource }) {
                    Text(stringResource(if (showSource) R.string.rich_math_preview else R.string.rich_math_source))
                }
            }
        }
    }
}

@Composable
private fun mathWords() = MathWords(
    over = stringResource(R.string.rich_math_over),
    power = stringResource(R.string.rich_math_power),
    sub = stringResource(R.string.rich_math_sub),
    squareRoot = stringResource(R.string.rich_math_sqrt),
    root = stringResource(R.string.rich_math_root),
    of = stringResource(R.string.rich_math_of),
    from = stringResource(R.string.rich_math_from),
    to = stringResource(R.string.rich_math_to),
    matrix = stringResource(R.string.rich_math_matrix),
    row = stringResource(R.string.rich_math_row),
)

/** Draws a parsed formula. Sizes shrink for scripts, limits and nested fractions (to 60%, never below 10 sp). */
@Composable
fun MathFormula(node: MathNode, fontSize: TextUnit, color: Color, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier) { MathNodeView(node, fontSize, color) }
    }
}

private fun TextUnit.scaled(factor: Float): TextUnit = (value * factor).coerceAtLeast(10f).sp

@Composable
private fun MathNodeView(node: MathNode, size: TextUnit, color: Color) {
    when (node) {
        is MathRow -> Row(verticalAlignment = Alignment.CenterVertically) { node.items.forEach { MathNodeView(it, size, color) } }
        is MathText -> MathGlyphs(node, size, color)
        is MathFraction -> FractionLayout(color) {
            MathNodeView(node.numerator, size.scaled(FRACTION_SCALE), color)
            MathNodeView(node.denominator, size.scaled(FRACTION_SCALE), color)
        }
        is MathScripts -> Row(verticalAlignment = Alignment.CenterVertically) {
            MathNodeView(node.base, size, color)
            ScriptsLayout(
                sup = node.sup?.let { { MathNodeView(it, size.scaled(SCRIPT_SCALE), color) } },
                sub = node.sub?.let { { MathNodeView(it, size.scaled(SCRIPT_SCALE), color) } },
                gap = (size.value * 0.35f).dp,
            )
        }
        is MathRoot -> Row(verticalAlignment = Alignment.Bottom) {
            node.index?.let { Box(Modifier.padding(bottom = (size.value * 0.5f).dp)) { MathNodeView(it, size.scaled(INDEX_SCALE), color) } }
            Text("√", style = glyphStyle(size.scaled(1.15f), color))
            Box(
                Modifier
                    .drawBehind { drawLine(color, Offset(0f, 1f), Offset(this.size.width, 1f), strokeWidth = 1.2.dp.toPx()) }
                    .padding(top = 3.dp, start = 1.dp, end = 2.dp),
            ) { MathNodeView(node.radicand, size, color) }
        }
        is MathBigOperator -> if (node.limitsBeside) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(node.symbol, style = glyphStyle(size.scaled(BIG_SCALE), color))
                ScriptsLayout(
                    sup = node.upper?.let { { MathNodeView(it, size.scaled(SCRIPT_SCALE), color) } },
                    sub = node.lower?.let { { MathNodeView(it, size.scaled(SCRIPT_SCALE), color) } },
                    gap = (size.value * 0.9f).dp,
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
                node.upper?.let { MathNodeView(it, size.scaled(SCRIPT_SCALE), color) }
                Text(node.symbol, style = glyphStyle(size.scaled(BIG_SCALE), color))
                node.lower?.let { MathNodeView(it, size.scaled(SCRIPT_SCALE), color) }
            }
        }
        is MathFenced -> Row(verticalAlignment = Alignment.CenterVertically) {
            val grow = 1f + 0.45f * depth(node.content)
            if (node.open.isNotEmpty()) Text(node.open, style = glyphStyle(size.scaled(grow), color))
            MathNodeView(node.content, size, color)
            if (node.close.isNotEmpty()) Text(node.close, style = glyphStyle(size.scaled(grow), color))
        }
        is MathMatrix -> Row(verticalAlignment = Alignment.CenterVertically) {
            val grow = 1f + 0.8f * (node.rows.size - 1).coerceAtLeast(0)
            if (node.open.isNotEmpty()) Text(node.open, style = glyphStyle(size.scaled(grow), color))
            val columns = node.rows.maxOfOrNull { it.size } ?: 0
            MatrixLayout(node.rows.size, columns, gap = (size.value * 0.6f).dp) { r, c ->
                node.rows.getOrNull(r)?.getOrNull(c)?.let { MathNodeView(it, size, color) }
            }
            if (node.close.isNotEmpty()) Text(node.close, style = glyphStyle(size.scaled(grow), color))
        }
    }
}

/** Numerator over denominator, centered, with a bar as wide as the wider of the two. */
@Composable
private fun FractionLayout(color: Color, content: @Composable () -> Unit) {
    Layout(
        content = {
            content()
            Box(Modifier.background(color))
        },
        modifier = Modifier.padding(horizontal = 2.dp),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val top = measurables[0].measure(loose)
        val bottom = measurables[1].measure(loose)
        val width = maxOf(top.width, bottom.width) + 4.dp.roundToPx()
        val thickness = 1.2.dp.roundToPx().coerceAtLeast(1)
        val space = 2.dp.roundToPx()
        val bar = measurables[2].measure(Constraints.fixed(width, thickness))
        val height = top.height + space + thickness + space + bottom.height
        layout(width, height) {
            top.placeRelative((width - top.width) / 2, 0)
            bar.placeRelative(0, top.height + space)
            bottom.placeRelative((width - bottom.width) / 2, top.height + space * 2 + thickness)
        }
    }
}

@Composable
private fun MathGlyphs(node: MathText, size: TextUnit, color: Color) {
    val text = when (node.kind) {
        MathTextKind.OPERATOR -> if (node.text in SPACED) " ${node.text} " else node.text
        MathTextKind.FUNCTION -> node.text + " "
        else -> node.text
    }
    Text(
        text,
        style = glyphStyle(size, color).copy(fontStyle = if (node.kind == MathTextKind.VARIABLE && node.text.all { it in 'a'..'z' || it in 'A'..'Z' }) FontStyle.Italic else FontStyle.Normal),
    )
}

private fun glyphStyle(size: TextUnit, color: Color) = TextStyle(fontSize = size, color = color, textAlign = TextAlign.Center, lineHeight = 1.15.em)

/** Superscript on top, subscript at the bottom, both at the end of the base. */
@Composable
private fun ScriptsLayout(sup: (@Composable () -> Unit)?, sub: (@Composable () -> Unit)?, gap: Dp) {
    Layout(
        content = {
            Box { sup?.invoke() }
            Box { sub?.invoke() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val top = measurables[0].measure(loose)
        val bottom = measurables[1].measure(loose)
        val gapPx = gap.roundToPx()
        val height = top.height + bottom.height + gapPx
        layout(maxOf(top.width, bottom.width), height) {
            top.placeRelative(0, 0)
            bottom.placeRelative(0, height - bottom.height)
        }
    }
}

/** Cells of a matrix in a grid with centered columns. */
@Composable
private fun MatrixLayout(rows: Int, columns: Int, gap: Dp, cell: @Composable (Int, Int) -> Unit) {
    Layout(
        content = {
            for (r in 0 until rows) for (c in 0 until columns) Box { cell(r, c) }
        },
        modifier = Modifier.padding(horizontal = 4.dp),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val gapPx = gap.roundToPx()
        val widths = (0 until columns).map { c -> (0 until rows).maxOfOrNull { r -> placeables[r * columns + c].width } ?: 0 }
        val heights = (0 until rows).map { r -> (0 until columns).maxOfOrNull { c -> placeables[r * columns + c].height } ?: 0 }
        val width = widths.sum() + gapPx * (columns - 1).coerceAtLeast(0)
        val height = heights.sum() + gapPx / 2 * (rows - 1).coerceAtLeast(0)
        layout(width, height) {
            var y = 0
            for (r in 0 until rows) {
                var x = 0
                for (c in 0 until columns) {
                    val p = placeables[r * columns + c]
                    p.placeRelative(x + (widths[c] - p.width) / 2, y + (heights[r] - p.height) / 2)
                    x += widths[c] + gapPx
                }
                y += heights[r] + gapPx / 2
            }
        }
    }
}

/** How many fraction or matrix levels a node stacks (to size its delimiters). */
private fun depth(node: MathNode): Int = when (node) {
    is MathRow -> node.items.maxOfOrNull(::depth) ?: 0
    is MathFraction -> 1 + maxOf(depth(node.numerator), depth(node.denominator))
    is MathMatrix -> node.rows.size.coerceAtLeast(1)
    is MathScripts -> depth(node.base)
    is MathRoot -> depth(node.radicand)
    is MathFenced -> depth(node.content)
    is MathBigOperator -> 1
    is MathText -> 0
}

private const val FRACTION_SCALE = 0.85f
private const val SCRIPT_SCALE = 0.65f
private const val INDEX_SCALE = 0.55f
private const val BIG_SCALE = 1.5f
private val SPACED = setOf("=", "+", "−", "±", "×", "÷", "≤", "≥", "≠", "≈", "→", "⇒", "<", ">", "∈", "≡", "·")
