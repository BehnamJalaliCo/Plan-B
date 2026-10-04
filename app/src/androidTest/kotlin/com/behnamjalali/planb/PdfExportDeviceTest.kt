package com.behnamjalali.planb

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.behnamjalali.planb.core.ui.pdf.PdfBar
import com.behnamjalali.planb.core.ui.pdf.PdfBlock
import com.behnamjalali.planb.core.ui.pdf.PdfMetric
import com.behnamjalali.planb.core.ui.pdf.PdfReport
import com.behnamjalali.planb.core.ui.pdf.PdfReportWriter
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test of the real PDF output (Robolectric has no PDF backend): a long Persian report
 * becomes a valid, multi-page PDF that Android's own PdfRenderer opens.
 */
@RunWith(AndroidJUnit4::class)
class PdfExportDeviceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun persianReportIsAValidMultiPagePdf() {
        val report = PdfReport(
            title = "سال من ۱۴۰۵",
            subtitle = "۱ فروردین تا ۲۹ اسفند",
            rtl = true,
            blocks = buildList {
                add(PdfBlock.Metrics(List(6) { PdfMetric("کار انجام‌شده", "۱۲۳") }))
                repeat(8) { section ->
                    add(PdfBlock.Heading("بخش ${section + 1}"))
                    add(PdfBlock.Paragraph("این یک متن فارسی برای آزمایش چیدمان راست به چپ است. ".repeat(12)))
                    add(PdfBlock.Bars(List(12) { PdfBar("ماه ${it + 1}", it / 11f, "${it * 3}") }))
                }
            },
            footer = "Plan-B",
        )
        val file = File(context.cacheDir, "report-test.pdf")
        file.outputStream().use { PdfReportWriter.write(context, report, it) }
        try {
            val head = file.inputStream().use { input -> ByteArray(5).also { input.read(it) }.toString(Charsets.ISO_8859_1) }
            assertThat(head).isEqualTo("%PDF-")
            assertThat(file.length()).isGreaterThan(2_000L)
            // PdfRenderer owns the descriptor and closes it.
            val renderer = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
            try {
                assertThat(renderer.pageCount).isGreaterThan(1)
            } finally {
                renderer.close()
            }
        } finally {
            file.delete()
        }
    }
}
