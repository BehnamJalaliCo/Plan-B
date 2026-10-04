package com.behnamjalali.planb.core.ui.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Layout of PDF reports on the JVM. Robolectric has no native PDF backend, so pages are drawn
 * into bitmaps here; the device test (app androidTest, PdfExportDeviceTest) checks the real
 * PdfDocument output.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfReportWriterTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class BitmapSink : PdfReportWriter.PageSink {
        val pages = mutableListOf<Bitmap>()
        var open = false

        override fun startPage(number: Int, width: Int, height: Int): Canvas {
            check(!open) { "previous page not finished" }
            open = true
            return Canvas(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE); pages += it })
        }

        override fun finishPage() {
            check(open)
            open = false
        }
    }

    private fun longReport(rtl: Boolean) = PdfReport(
        title = if (rtl) "سال من ۱۴۰۵" else "My year 2026",
        subtitle = if (rtl) "۱ فروردین تا ۲۹ اسفند" else "1 January – 31 December",
        rtl = rtl,
        blocks = buildList {
            add(PdfBlock.Metrics(List(6) { PdfMetric(if (rtl) "کار انجام‌شده" else "Tasks done", if (rtl) "۱۲۳" else "123") }))
            repeat(8) { section ->
                add(PdfBlock.Heading(if (rtl) "بخش ${section + 1}" else "Section ${section + 1}"))
                add(PdfBlock.Paragraph((if (rtl) "این یک متن فارسی برای آزمایش چیدمان راست به چپ است. " else "A paragraph to wrap. ").repeat(12)))
                add(PdfBlock.Bars(List(12) { PdfBar(if (rtl) "ماه ${it + 1}" else "Month ${it + 1}", it / 11f, "${it * 3}") }))
            }
        },
        footer = "Plan-B",
    )

    @Test
    fun longReportFlowsOverSeveralA4Pages() {
        val sink = BitmapSink()
        val pages = PdfReportWriter.render(context, longReport(rtl = true), sink)
        assertThat(pages).isGreaterThan(1)
        assertThat(sink.pages).hasSize(pages)
        assertThat(sink.open).isFalse()
        assertThat(sink.pages.first().width).isEqualTo(PdfReportWriter.PAGE_WIDTH_POINTS)
        assertThat(sink.pages.first().height).isEqualTo(PdfReportWriter.PAGE_HEIGHT_POINTS)
        sink.pages.forEach { assertThat(inkColumns(it, 0, it.height)).isNotEmpty() }
    }

    @Test
    fun titleStartsAtTheReadingEdge() {
        fun titleInk(rtl: Boolean): List<Int> {
            val sink = BitmapSink()
            PdfReportWriter.render(context, PdfReport(if (rtl) "سلام" else "Hello", "", rtl, emptyList(), ""), sink)
            return inkColumns(sink.pages.single(), 40, 70)
        }
        val width = PdfReportWriter.PAGE_WIDTH_POINTS
        val rtl = titleInk(rtl = true)
        val ltr = titleInk(rtl = false)
        assertThat(rtl.min()).isGreaterThan(width / 2)
        assertThat(ltr.max()).isLessThan(width / 2)
    }

    /** Columns (x) with any non-white pixel between rows [top] and [bottom]. */
    private fun inkColumns(bitmap: Bitmap, top: Int, bottom: Int): List<Int> {
        val w = bitmap.width
        val rows = bottom - top
        val pixels = IntArray(w * rows)
        bitmap.getPixels(pixels, 0, w, 0, top, w, rows)
        return (0 until w).filter { x -> (0 until rows).any { y -> pixels[y * w + x] != Color.WHITE } }
    }
}
