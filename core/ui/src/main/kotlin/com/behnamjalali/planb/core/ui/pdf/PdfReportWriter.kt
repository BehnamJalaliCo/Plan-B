package com.behnamjalali.planb.core.ui.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import java.io.OutputStream
import com.behnamjalali.planb.core.designsystem.R as DesignR

/** A printable report (statistics, "my year", weekly review). All texts are already localized. */
data class PdfReport(
    val title: String,
    val subtitle: String,
    val rtl: Boolean,
    val blocks: List<PdfBlock>,
    val footer: String,
    /** Page numbers in the report's digits (Persian or Latin). */
    val pageNumber: (Int) -> String = Int::toString,
)

sealed interface PdfBlock {
    data class Heading(val text: String) : PdfBlock
    data class Paragraph(val text: String) : PdfBlock

    /** Key numbers shown as tiles, two per row. */
    data class Metrics(val items: List<PdfMetric>) : PdfBlock

    /** A labelled horizontal bar chart; [PdfBar.fraction] is 0..1 of the longest bar. */
    data class Bars(val items: List<PdfBar>) : PdfBlock
}

data class PdfMetric(val label: String, val value: String)

data class PdfBar(val label: String, val fraction: Float, val value: String)

/**
 * Renders a [PdfReport] with [PdfDocument] on A4 pages. Text is laid out with [StaticLayout]
 * in the app's typeface, so Persian letters join and right-to-left paragraphs align and wrap
 * correctly; charts are mirrored for right-to-left reports.
 */
object PdfReportWriter {
    private const val PAGE_WIDTH = 595 // A4 at 72 dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

    // Print colors (the classic brand palette; reports are documents, not themed screens).
    private val INK = Color.rgb(0x1F, 0x1B, 0x26)
    private val MUTED = Color.rgb(0x6A, 0x64, 0x76)
    private val PRIMARY = Color.rgb(0x6B, 0x5B, 0xD2)
    private val TRACK = Color.rgb(0xEA, 0xE6, 0xFD)
    private val TILE = Color.rgb(0xF5, 0xEF, 0xE8)

    const val PAGE_WIDTH_POINTS = PAGE_WIDTH
    const val PAGE_HEIGHT_POINTS = PAGE_HEIGHT

    /** Where pages are drawn: a [PdfDocument] in the app, a bitmap canvas in JVM tests. */
    interface PageSink {
        fun startPage(number: Int, width: Int, height: Int): Canvas
        fun finishPage()
    }

    fun write(context: Context, report: PdfReport, out: OutputStream) {
        val document = PdfDocument()
        try {
            var page: PdfDocument.Page? = null
            render(
                context,
                report,
                object : PageSink {
                    override fun startPage(number: Int, width: Int, height: Int): Canvas =
                        document.startPage(PdfDocument.PageInfo.Builder(width, height, number).create()).also { page = it }.canvas

                    override fun finishPage() {
                        page?.let(document::finishPage)
                        page = null
                    }
                },
            )
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    /** Lays out and draws [report] page by page into [sink]; returns the number of pages. */
    fun render(context: Context, report: PdfReport, sink: PageSink): Int {
        val regular = font(context, DesignR.font.planb_regular, Typeface.NORMAL)
        val bold = font(context, DesignR.font.planb_bold, Typeface.BOLD)
        return Renderer(sink, report.rtl, regular, bold).render(report)
    }

    private fun font(context: Context, id: Int, style: Int): Typeface =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: Typeface.create(Typeface.DEFAULT, style)

    private class Renderer(
        private val sink: PageSink,
        private val rtl: Boolean,
        private val regular: Typeface,
        private val bold: Typeface,
    ) {
        private var pageNumber = 0
        private var page: Canvas? = null
        private var y = MARGIN
        private val canvas: Canvas get() = page!!
        private lateinit var footer: String
        private var pageLabel: (Int) -> String = Int::toString

        private fun paint(typeface: Typeface, size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size
            this.color = color
        }

        private val titlePaint = paint(bold, 24f, INK)
        private val subtitlePaint = paint(regular, 12f, MUTED)
        private val headingPaint = paint(bold, 15f, PRIMARY)
        private val bodyPaint = paint(regular, 11f, INK)
        private val valuePaint = paint(bold, 18f, INK)
        private val labelPaint = paint(regular, 9.5f, MUTED)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        fun render(report: PdfReport): Int {
            footer = report.footer
            pageLabel = report.pageNumber
            newPage()
            text(report.title, titlePaint, CONTENT_WIDTH)
            y += 2f
            text(report.subtitle, subtitlePaint, CONTENT_WIDTH)
            y += 10f
            fill.color = TRACK
            canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 1.5f, fill)
            y += 14f
            report.blocks.forEach(::block)
            finishPage()
            return pageNumber
        }

        private fun block(block: PdfBlock) {
            when (block) {
                is PdfBlock.Heading -> {
                    val layout = layout(block.text, headingPaint, CONTENT_WIDTH)
                    // Keep a heading with at least a little of what follows.
                    ensure(layout.height + 60f)
                    y += 6f
                    draw(layout, MARGIN)
                    y += 6f
                }
                is PdfBlock.Paragraph -> text(block.text, bodyPaint, CONTENT_WIDTH, gap = 6f)
                is PdfBlock.Metrics -> metrics(block.items)
                is PdfBlock.Bars -> bars(block.items)
            }
        }

        private fun metrics(items: List<PdfMetric>) {
            val gap = 10f
            val width = (CONTENT_WIDTH - gap) / 2
            items.chunked(2).forEach { row ->
                val layouts = row.map { layout(it.value, valuePaint, width - 24f) to layout(it.label, labelPaint, width - 24f) }
                val height = layouts.maxOf { (v, l) -> v.height + l.height } + 20f
                ensure(height + gap)
                row.indices.forEach { i ->
                    val left = mirrored(MARGIN + i * (width + gap), width)
                    fill.color = TILE
                    canvas.drawRoundRect(RectF(left, y, left + width, y + height), 10f, 10f, fill)
                    val (value, label) = layouts[i]
                    val top = y
                    y = top + 10f
                    draw(value, left + 12f)
                    draw(label, left + 12f)
                    y = top
                }
                y += height + gap
            }
        }

        private fun bars(items: List<PdfBar>) {
            val labelWidth = CONTENT_WIDTH * 0.34f
            val valueWidth = CONTENT_WIDTH * 0.16f
            val barWidth = CONTENT_WIDTH - labelWidth - valueWidth - 16f
            items.forEach { item ->
                val label = layout(item.label, bodyPaint, labelWidth, maxLines = 2)
                val value = layout(item.value, bodyPaint, valueWidth, maxLines = 1)
                val height = maxOf(label.height.toFloat(), 16f) + 6f
                ensure(height)
                val top = y
                draw(label, mirrored(MARGIN, labelWidth))
                y = top
                val barLeft = MARGIN + labelWidth + 8f
                val barTop = top + (height - 6f) / 2 - 4f
                fill.color = TRACK
                canvas.drawRoundRect(RectF(mirrored(barLeft, barWidth), barTop, mirrored(barLeft, barWidth) + barWidth, barTop + 8f), 4f, 4f, fill)
                val length = barWidth * item.fraction.coerceIn(0f, 1f)
                if (length > 0f) {
                    // Bars grow from the reading start edge.
                    val start = if (rtl) mirrored(barLeft, barWidth) + barWidth - length else barLeft
                    fill.color = PRIMARY
                    canvas.drawRoundRect(RectF(start, barTop, start + length, barTop + 8f), 4f, 4f, fill)
                }
                draw(value, mirrored(barLeft + barWidth + 8f, valueWidth))
                y = top + height
            }
            y += 6f
        }

        /** The x of a box at [left] (in left-to-right terms) of [width], mirrored for right-to-left. */
        private fun mirrored(left: Float, width: Float): Float = if (rtl) PAGE_WIDTH - left - width else left

        private fun text(value: String, paint: TextPaint, width: Float, gap: Float = 0f) {
            val layout = layout(value, paint, width)
            if (layout.height > PAGE_HEIGHT - 2 * MARGIN - 30f) {
                // A paragraph taller than a page: draw it line by line.
                for (line in 0 until layout.lineCount) {
                    val start = layout.getLineStart(line)
                    val end = layout.getLineEnd(line)
                    text(value.substring(start, end).trimEnd(), paint, width)
                }
            } else {
                ensure(layout.height.toFloat())
                draw(layout, MARGIN)
            }
            y += gap
        }

        private fun layout(value: String, paint: TextPaint, width: Float, maxLines: Int = Int.MAX_VALUE): StaticLayout =
            StaticLayout.Builder.obtain(value, 0, value.length, paint, width.toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(if (rtl) TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setIncludePad(true)
                .setMaxLines(maxLines)
                .setEllipsize(if (maxLines == Int.MAX_VALUE) null else TextUtils.TruncateAt.END)
                .build()

        private fun draw(layout: StaticLayout, left: Float) {
            canvas.save()
            canvas.translate(left, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height
        }

        private fun ensure(height: Float) {
            if (y + height > PAGE_HEIGHT - MARGIN - 20f) {
                finishPage()
                newPage()
            }
        }

        private fun newPage() {
            pageNumber++
            page = sink.startPage(pageNumber, PAGE_WIDTH, PAGE_HEIGHT)
            y = MARGIN
        }

        private fun finishPage() {
            if (page == null) return
            val label = layout("$footer  ${pageLabel(pageNumber)}", labelPaint, CONTENT_WIDTH)
            canvas.save()
            canvas.translate(MARGIN, PAGE_HEIGHT - MARGIN + 4f)
            label.draw(canvas)
            canvas.restore()
            sink.finishPage()
            page = null
        }
    }
}
