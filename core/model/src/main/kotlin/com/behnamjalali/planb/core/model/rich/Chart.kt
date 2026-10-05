package com.behnamjalali.planb.core.model.rich

import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.NoteBlock
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ChartKind {
    @SerialName("bar") BAR,
    @SerialName("line") LINE,
    @SerialName("pie") PIE,
}

@Serializable
data class ChartPoint(val label: String = "", val value: Double = 0.0)

/**
 * Takes the chart's data from a database block of the same note: labels from [labelColumnId]
 * (or the row number), values from the number column [valueColumnId], rows as that database
 * currently shows them (filtered and sorted).
 */
@Serializable
data class ChartSource(val blockId: String, val labelColumnId: String? = null, val valueColumnId: String)

/** A chart (Plan-B Pro #23): inline [points], or the rows of a database block ([source]). */
@Serializable
data class ChartData(
    val kind: ChartKind = ChartKind.BAR,
    val title: String = "",
    val points: List<ChartPoint> = listOf(ChartPoint("A", 3.0), ChartPoint("B", 5.0), ChartPoint("C", 2.0)),
    val source: ChartSource? = null,
) {
    fun setPoint(index: Int, point: ChartPoint): ChartData =
        if (index !in points.indices) this else copy(points = points.mapIndexed { i, p -> if (i == index) point else p })

    fun addPoint(point: ChartPoint = ChartPoint()): ChartData = if (points.size >= MAX_POINTS) this else copy(points = points + point)

    fun removePoint(index: Int): ChartData = copy(points = points.filterIndexed { i, _ -> i != index })

    companion object {
        const val MAX_POINTS = 60
    }
}

/** A value axis: from [min] to [max] in steps of [step] (all "nice" numbers). */
data class ChartScale(val min: Double, val max: Double, val step: Double) {
    val ticks: List<Double> get() = generateSequence(min) { it + step }.takeWhile { it <= max + step / 2 }.toList()
    fun fraction(value: Double): Double = if (max == min) 0.0 else (value - min) / (max - min)
}

object ChartMapping {
    /** The points to draw: from the source database when it exists, otherwise the inline ones. */
    fun resolve(chart: ChartData, blocks: List<NoteBlock>): List<ChartPoint> {
        val source = chart.source ?: return chart.points
        val block = blocks.firstOrNull { it.id == source.blockId && it.type == BlockType.DATABASE } ?: return chart.points
        val db = RichBlocks.database(block)
        val value = db.column(source.valueColumnId)?.takeIf { it.type == ColumnType.NUMBER } ?: return chart.points
        val label = source.labelColumnId?.let(db::column)
        return db.visibleRows().take(ChartData.MAX_POINTS).mapIndexed { i, row ->
            ChartPoint(
                label = label?.let { row.cells[it.id] }.orEmpty().ifBlank { (i + 1).toString() },
                value = DbValues.parseNumber(row.cells[value.id])?.toDouble() ?: 0.0,
            )
        }
    }

    /** Pie slices as fractions of the total; negative values count as zero. */
    fun shares(points: List<ChartPoint>): List<Double> {
        val total = points.sumOf { it.value.coerceAtLeast(0.0) }
        return points.map { if (total <= 0.0) 0.0 else it.value.coerceAtLeast(0.0) / total }
    }

    /** A scale that includes zero and every value, with about [targetTicks] nice steps. */
    fun scale(points: List<ChartPoint>, targetTicks: Int = 4): ChartScale {
        val lo = minOf(0.0, points.minOfOrNull { it.value } ?: 0.0)
        val hi = maxOf(0.0, points.maxOfOrNull { it.value } ?: 0.0)
        if (hi == lo) return ChartScale(0.0, 1.0, 0.25)
        val step = niceStep((hi - lo) / targetTicks)
        return ChartScale(floor(lo / step) * step, ceil(hi / step) * step, step)
    }

    private fun niceStep(raw: Double): Double {
        val exponent = floor(log10(abs(raw)))
        val base = 10.0.pow(exponent)
        val fraction = raw / base
        val nice = when {
            fraction <= 1.0 -> 1.0
            fraction <= 2.0 -> 2.0
            fraction <= 2.5 -> 2.5
            fraction <= 5.0 -> 5.0
            else -> 10.0
        }
        return nice * base
    }
}
