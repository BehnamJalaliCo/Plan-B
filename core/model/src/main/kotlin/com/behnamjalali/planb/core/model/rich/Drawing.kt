package com.behnamjalali.planb.core.model.rich

import kotlin.math.hypot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The drawing block's payload: its strokes live in a vector attachment ([vectorId], JSON
 * [Drawing]) and its preview in the block's PNG attachment, so large drawings never bloat the
 * note text, its versions or the backup's database file.
 */
@Serializable
data class DrawingRef(
    val vectorId: Long? = null,
    val width: Int = Drawing.DEFAULT_WIDTH,
    val height: Int = Drawing.DEFAULT_HEIGHT,
)

/**
 * A stroke: [color] as ARGB, base [width] in drawing units, and its points as a flat list of
 * `x, y, pressure` triples (integers: drawing units and pressure in percent), which keeps the
 * JSON compact.
 */
@Serializable
data class Stroke(
    @SerialName("c") val color: Long,
    @SerialName("w") val width: Float,
    @SerialName("p") val points: List<Int>,
) {
    val pointCount: Int get() = points.size / 3
    fun x(i: Int) = points[i * 3]
    fun y(i: Int) = points[i * 3 + 1]

    /** Pressure 0..1 (1 when the device reports none). */
    fun pressure(i: Int) = (points[i * 3 + 2] / 100f).coerceIn(0.05f, 1f)
}

/** A drawing in its own coordinate space ([width] × [height] units, scaled to the screen). */
@Serializable
data class Drawing(
    @SerialName("v") val version: Int = 1,
    @SerialName("w") val width: Int = DEFAULT_WIDTH,
    @SerialName("h") val height: Int = DEFAULT_HEIGHT,
    @SerialName("s") val strokes: List<Stroke> = emptyList(),
) {
    val pointCount: Int get() = strokes.sumOf { it.pointCount }

    fun encode(): String = json.encodeToString(serializer(), this)

    /** Adds a stroke unless the drawing already holds [MAX_POINTS] points. */
    fun plus(stroke: Stroke): Drawing =
        if (stroke.pointCount == 0 || pointCount + stroke.pointCount > MAX_POINTS) this else copy(strokes = strokes + stroke)

    /** The stroke eraser: removes every stroke that passes within [radius] of (x, y). */
    fun eraseAt(x: Float, y: Float, radius: Float): Drawing {
        val kept = strokes.filterNot { s -> touches(s, x, y, radius + s.width / 2) }
        return if (kept.size == strokes.size) this else copy(strokes = kept)
    }

    private fun touches(s: Stroke, x: Float, y: Float, radius: Float): Boolean {
        if (s.pointCount == 1) return hypot(s.x(0) - x, s.y(0) - y) <= radius
        for (i in 1 until s.pointCount) {
            if (segmentDistance(x, y, s.x(i - 1).toFloat(), s.y(i - 1).toFloat(), s.x(i).toFloat(), s.y(i).toFloat()) <= radius) return true
        }
        return false
    }

    private fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len = dx * dx + dy * dy
        val t = if (len == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    companion object {
        const val DEFAULT_WIDTH = 1000
        const val DEFAULT_HEIGHT = 625
        const val MAX_POINTS = 60_000

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Never throws: unreadable data is an empty drawing. */
        fun decode(raw: String?): Drawing =
            if (raw.isNullOrBlank()) Drawing() else runCatching { json.decodeFromString(serializer(), raw) }.getOrDefault(Drawing())
    }
}

/** Undo/redo over whole drawings (strokes are immutable, so snapshots are cheap). */
class DrawingHistory(initial: Drawing, private val limit: Int = 100) {
    private val undo = ArrayDeque<Drawing>()
    private val redo = ArrayDeque<Drawing>()
    var current: Drawing = initial
        private set

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun push(next: Drawing) {
        if (next == current) return
        undo.addLast(current)
        if (undo.size > limit) undo.removeFirst()
        redo.clear()
        current = next
    }

    fun undo(): Drawing {
        if (undo.isEmpty()) return current
        redo.addLast(current)
        current = undo.removeLast()
        return current
    }

    fun redo(): Drawing {
        if (redo.isEmpty()) return current
        undo.addLast(current)
        current = redo.removeLast()
        return current
    }
}
