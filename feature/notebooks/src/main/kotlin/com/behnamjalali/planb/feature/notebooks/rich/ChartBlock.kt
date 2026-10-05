package com.behnamjalali.planb.feature.notebooks.rich

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.core.common.Digits
import com.behnamjalali.planb.core.common.NumberFormatter
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.rich.ChartData
import com.behnamjalali.planb.core.model.rich.ChartKind
import com.behnamjalali.planb.core.model.rich.ChartMapping
import com.behnamjalali.planb.core.model.rich.ChartPoint
import com.behnamjalali.planb.core.model.rich.ChartSource
import com.behnamjalali.planb.core.model.rich.ColumnType
import com.behnamjalali.planb.core.model.rich.DbValues
import com.behnamjalali.planb.core.model.rich.RichBlocks
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.feature.notebooks.EditorBlock
import com.behnamjalali.planb.feature.notebooks.R
import kotlin.math.abs

/**
 * A bar, line or pie chart (#23) drawn on a Canvas, from its own values or from a database
 * block of the same note. Bars and points run in reading order (mirrored in Persian); the
 * chart is announced as a sentence with every value, so nothing relies on color alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChartBlock(block: EditorBlock, blocks: List<EditorBlock>, ui: RichUi?, editable: Boolean) {
    var chart by remember(block.id, block.revision) { mutableStateOf(RichBlocks.chart(block.toNoteBlock())) }
    fun change(next: ChartData) {
        chart = next
        ui?.update(block.id, RichBlocks.encode(next))
    }
    var editing by remember { mutableStateOf(false) }
    val noteBlocks = remember(blocks) { blocks.map { it.toNoteBlock() } }
    val points = remember(chart, noteBlocks) { ChartMapping.resolve(chart, noteBlocks) }
    val numbers = PlannerLocals.numbers
    val values = points.joinToString("، ".takeIf { numbers.persianDigits } ?: ", ") { pointText(it, numbers) }
    val title = chart.title
    val kindName = stringResource(
        when (chart.kind) {
            ChartKind.BAR -> R.string.rich_chart_kind_bar
            ChartKind.LINE -> R.string.rich_chart_kind_line
            ChartKind.PIE -> R.string.rich_chart_kind_pie
        },
    )
    val summary = stringResource(
        R.string.rich_chart_summary,
        if (title.isBlank()) kindName else "$kindName $title",
        values.ifBlank { stringResource(R.string.rich_chart_empty) },
    )
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(Radius.md), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(Modifier.clearAndSetSemantics { contentDescription = summary }) {
                if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                when {
                    points.isEmpty() -> Text(stringResource(R.string.rich_chart_empty), color = MaterialTheme.colorScheme.outline)
                    chart.kind == ChartKind.PIE -> PieChart(points, numbers)
                    else -> AxisChart(points, chart.kind, numbers)
                }
            }
            if (editable && ui != null) {
                if (editing) {
                    ChartEditor(chart, blocks, ::change)
                    TextButton(onClick = { editing = false }) { Text(stringResource(R.string.rich_chart_done_editing)) }
                } else {
                    TextButton(onClick = { editing = true }) { Text(stringResource(R.string.rich_chart_edit)) }
                }
            }
        }
    }
}

private fun pointText(point: ChartPoint, numbers: NumberFormatter) = "${point.label} ${numbers.format(point.value, 2)}"

/** Series colors from the accent palette (light and dark tones). */
@Composable
private fun palette(): List<Color> = listOf(
    AccentColor.LAVENDER, AccentColor.MINT, AccentColor.PEACH, AccentColor.POWDER_BLUE,
    AccentColor.ROSE, AccentColor.SAND, AccentColor.SAGE, AccentColor.SLATE,
).map { PlanBTheme.colors.accent(it).strong }

@Composable
private fun AxisChart(points: List<ChartPoint>, kind: ChartKind, numbers: NumberFormatter) {
    val scale = remember(points) { ChartMapping.scale(points) }
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    Row {
        // The value axis on the reading start side: the top and bottom ticks.
        Column(Modifier.height(ChartHeight), verticalArrangement = Arrangement.SpaceBetween) {
            Text(numbers.format(scale.max, 2), style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(numbers.format(scale.min, 2), style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Spacing.xs))
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(ChartHeight)) {
                val rtl = layoutDirection == LayoutDirection.Rtl
                scale.ticks.forEach { tick ->
                    val y = size.height * (1f - scale.fraction(tick).toFloat())
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                val zeroY = size.height * (1f - scale.fraction(0.0).toFloat())
                val slot = size.width / points.size
                fun x(i: Int) = (if (rtl) points.lastIndex - i else i) * slot + slot / 2
                fun y(v: Double) = size.height * (1f - scale.fraction(v).toFloat())
                if (kind == ChartKind.BAR) {
                    val barWidth = (slot * 0.6f).coerceAtMost(36.dp.toPx())
                    points.forEachIndexed { i, p ->
                        val top = minOf(y(p.value), zeroY)
                        val h = abs(y(p.value) - zeroY)
                        if (h > 0f) drawRoundRect(color, Offset(x(i) - barWidth / 2, top), Size(barWidth, h), CornerRadius(4.dp.toPx()))
                    }
                } else {
                    val path = Path()
                    points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(i), y(p.value)) else path.lineTo(x(i), y(p.value)) }
                    drawPath(path, color, style = Stroke(width = 2.5.dp.toPx()))
                    points.forEachIndexed { i, p -> drawCircle(color, 4.dp.toPx(), Offset(x(i), y(p.value))) }
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Row(Modifier.fillMaxWidth()) {
                points.forEach { p ->
                    Text(
                        p.label,
                        style = labelStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PieChart(points: List<ChartPoint>, numbers: NumberFormatter) {
    val shares = remember(points) { ChartMapping.shares(points) }
    val colors = palette()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Canvas(Modifier.size(ChartHeight)) {
            var start = -90f
            shares.forEachIndexed { i, share ->
                val sweep = (share * 360).toFloat()
                if (sweep > 0f) drawArc(colors[i % colors.size], start, sweep, useCenter = true)
                start += sweep
            }
        }
        FlowRow(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            points.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(colors[i % colors.size], CircleShape))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        "${p.label} ${numbers.percent(shares[i].toFloat())}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private val ChartHeight = 160.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartEditor(chart: ChartData, blocks: List<EditorBlock>, change: (ChartData) -> Unit) {
    val numbers = PlannerLocals.numbers
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        listOf(ChartKind.BAR to R.string.rich_chart_bar, ChartKind.LINE to R.string.rich_chart_line, ChartKind.PIE to R.string.rich_chart_pie).forEach { (kind, label) ->
            FilterChip(selected = chart.kind == kind, onClick = { change(chart.copy(kind = kind)) }, label = { Text(stringResource(label)) })
        }
    }
    val fieldStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val titleHint = stringResource(R.string.rich_chart_title_hint)
    BasicTextField(
        value = chart.title,
        onValueChange = { change(chart.copy(title = it.replace('\n', ' '))) },
        textStyle = fieldStyle.copy(fontWeight = FontWeight.SemiBold),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = titleHint },
        decorationBox = { inner ->
            if (chart.title.isEmpty()) Text(titleHint, style = fieldStyle.copy(color = MaterialTheme.colorScheme.outline))
            inner()
        },
    )
    // Data: the inline values, or a database of this note with a number column.
    val databases = blocks.filter { it.type == BlockType.DATABASE }.mapNotNull { b ->
        val db = RichBlocks.database(b.toNoteBlock())
        val value = db.columns.firstOrNull { it.type == ColumnType.NUMBER } ?: return@mapNotNull null
        val label = db.columns.firstOrNull { it.type == ColumnType.TEXT || it.type == ColumnType.SELECT }
        Triple(b.id, ChartSource(b.id, label?.id, value.id), value.name)
    }
    var sourceMenu by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { sourceMenu = true }) {
            Text(
                stringResource(R.string.rich_chart_source) + ": " + (
                    chart.source?.let { s -> databases.firstOrNull { it.first == s.blockId }?.third?.let { stringResource(R.string.rich_chart_source_database, it) } }
                        ?: stringResource(R.string.rich_chart_source_inline)
                    ),
            )
        }
        DropdownMenu(sourceMenu, { sourceMenu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.rich_chart_source_inline)) }, onClick = {
                sourceMenu = false
                change(chart.copy(source = null))
            })
            databases.forEach { (_, source, name) ->
                DropdownMenuItem(text = { Text(stringResource(R.string.rich_chart_source_database, name)) }, onClick = {
                    sourceMenu = false
                    change(chart.copy(source = source))
                })
            }
        }
    }
    if (chart.source == null) {
        chart.points.forEachIndexed { i, p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val labelHint = stringResource(R.string.rich_chart_label)
                BasicTextField(
                    value = p.label,
                    onValueChange = { change(chart.setPoint(i, p.copy(label = it.replace('\n', ' ')))) },
                    textStyle = fieldStyle,
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).semantics { contentDescription = labelHint },
                )
                var raw by remember(i, chart.points.size) { mutableStateOf(numbers.localize(plain(p.value))) }
                val valueHint = stringResource(R.string.rich_chart_value)
                BasicTextField(
                    value = raw,
                    onValueChange = {
                        raw = it
                        DbValues.parseNumber(Digits.toLatin(it))?.let { v -> change(chart.setPoint(i, p.copy(value = v.toDouble()))) }
                    },
                    textStyle = fieldStyle,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.width(96.dp).semantics { contentDescription = valueHint },
                )
                PlannerIconButton(Icons.Rounded.Close, stringResource(R.string.rich_chart_remove_point, p.label), { change(chart.removePoint(i)) })
            }
        }
        TextButton(onClick = { change(chart.addPoint()) }) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(stringResource(R.string.rich_chart_add_point))
        }
    }
}

private fun plain(value: Double): String =
    if (value == kotlin.math.floor(value) && abs(value) < 1e15) value.toLong().toString() else value.toString()
