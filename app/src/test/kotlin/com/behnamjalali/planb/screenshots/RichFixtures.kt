package com.behnamjalali.planb.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.rich.ChartData
import com.behnamjalali.planb.core.model.rich.ChartPoint
import com.behnamjalali.planb.core.model.rich.ColumnType
import com.behnamjalali.planb.core.model.rich.DatabaseData
import com.behnamjalali.planb.core.model.rich.DbColumn
import com.behnamjalali.planb.core.model.rich.DbRow
import com.behnamjalali.planb.core.model.rich.DbSort
import com.behnamjalali.planb.core.model.rich.Drawing
import com.behnamjalali.planb.core.model.rich.Stroke
import com.behnamjalali.planb.core.model.rich.TableData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Deterministic content for the rich-notes screenshots (pictures are drawn, never loaded). */
object RichFixtures {
    private fun jpeg(bitmap: Bitmap): InputStream = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bitmap.recycle()
        ByteArrayInputStream(out.toByteArray())
    }

    /** A wide "photo" of a whiteboard with sticky notes. */
    fun photo(): InputStream {
        val bitmap = Bitmap.createBitmap(1200, 450, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 1200f, 450f, 0xFF8FA9C4.toInt(), 0xFF4E6A88.toInt(), Shader.TileMode.CLAMP) })
        val board = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7F6F1.toInt() }
        canvas.drawRoundRect(80f, 50f, 1120f, 400f, 18f, 18f, board)
        val notes = listOf(0xFFFFE27A, 0xFFFFB3C7, 0xFFB5E8C8, 0xFFB8D6FF, 0xFFFFD0A6)
        notes.forEachIndexed { i, c ->
            val x = 130f + i * 195f
            val y = if (i % 2 == 0) 100f else 170f
            canvas.drawRect(x, y, x + 150f, y + 150f, Paint().apply { color = c.toInt() })
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3B3F4A.toInt(); strokeWidth = 6f }
            repeat(3) { r -> canvas.drawLine(x + 20f, y + 40f + r * 35f, x + 130f - r * 20f, y + 40f + r * 35f, line) }
        }
        return jpeg(bitmap)
    }

    /** A scanned receipt: a white page with printed lines. */
    fun receipt(): InputStream {
        val bitmap = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFFFFFF.toInt())
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF222222.toInt(); strokeWidth = 10f }
        canvas.drawRect(80f, 80f, 420f, 130f, ink)
        for (r in 0 until 9) {
            val y = 230f + r * 70f
            canvas.drawLine(80f, y, 80f + 300f + (r * 37 % 160), y, ink.apply { strokeWidth = 8f })
            canvas.drawLine(560f, y, 720f, y, ink)
        }
        canvas.drawLine(80f, 900f, 720f, 900f, ink.apply { strokeWidth = 14f })
        return jpeg(bitmap)
    }

    /** A doodle: a house, a sun and a wavy underline, drawn with pressure. */
    fun drawing(): Drawing {
        fun stroke(color: Long, width: Float, points: List<Pair<Int, Int>>) =
            Stroke(color, width, points.flatMapIndexed { i, (x, y) -> listOf(x, y, 60 + (i * 7 % 40)) })
        val house = listOf(200 to 480, 200 to 300, 330 to 180, 460 to 300, 460 to 480, 200 to 480)
        val door = listOf(300 to 480, 300 to 380, 360 to 380, 360 to 480)
        val sun = (0..36).map { i -> (760 + 70 * cos(i * PI / 18)).toInt() to (170 + 70 * sin(i * PI / 18)).toInt() }
        val wave = (0..60).map { i -> (120 + i * 13) to (545 + (18 * sin(i / 4.0)).toInt()) }
        return Drawing(
            strokes = listOf(
                stroke(0xFF1F1D2B, 7f, house),
                stroke(0xFFC0392B, 6f, door),
                stroke(0xFFA27A16, 8f, sun),
                stroke(0xFF3B74AE, 5f, wave),
            ),
        )
    }

    /** The drawing's preview on paper, as the editor renders it. */
    fun png(drawing: Drawing): ByteArray {
        val bitmap = Bitmap.createBitmap(drawing.width, drawing.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFFFDF8.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        drawing.strokes.forEach { s ->
            paint.color = s.color.toInt()
            for (i in 1 until s.pointCount) {
                paint.strokeWidth = s.width * s.pressure(i)
                canvas.drawLine(s.x(i - 1).toFloat(), s.y(i - 1).toFloat(), s.x(i).toFloat(), s.y(i).toFloat(), paint)
            }
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    fun waveform(): List<Int> = List(48) { i -> (30 + 60 * sin(i / 3.0).let { it * it }).toInt() }

    private fun fa(language: AppLanguage, fa: String, en: String) = if (language == AppLanguage.PERSIAN) fa else en

    fun table(language: AppLanguage) = TableData(
        listOf(
            listOf(fa(language, "کالا", "Item"), fa(language, "تعداد", "Qty"), fa(language, "فروشگاه", "Shop")),
            listOf(fa(language, "دفترچه", "Notebook"), fa(language, "۳", "3"), fa(language, "کتاب‌فروشی", "Bookshop")),
            listOf(fa(language, "خودکار", "Pens"), fa(language, "۱۰", "10"), fa(language, "لوازم‌التحریر", "Stationer")),
        ),
    )

    fun database(language: AppLanguage) = DatabaseData(
        columns = listOf(
            DbColumn("n", fa(language, "هزینه", "Expense")),
            DbColumn("a", fa(language, "مبلغ", "Amount"), ColumnType.NUMBER),
            DbColumn("p", fa(language, "پرداخت", "Paid"), ColumnType.CHECKBOX),
        ),
        rows = listOf(
            DbRow("1", mapOf("n" to fa(language, "کاغذ", "Paper"), "a" to "180000", "p" to "true")),
            DbRow("2", mapOf("n" to fa(language, "جوهر", "Ink"), "a" to "420000")),
            DbRow("3", mapOf("n" to fa(language, "پوشه", "Folders"), "a" to "95000", "p" to "true")),
        ),
        sort = DbSort("a", descending = true),
    )

    fun chart(language: AppLanguage) = ChartData(
        title = fa(language, "کارهای انجام‌شده", "Tasks done"),
        points = listOf(
            ChartPoint(fa(language, "مهر", "Oct"), 34.0),
            ChartPoint(fa(language, "آبان", "Nov"), 41.0),
            ChartPoint(fa(language, "آذر", "Dec"), 28.0),
            ChartPoint(fa(language, "دی", "Jan"), 52.0),
        ),
    )
}
