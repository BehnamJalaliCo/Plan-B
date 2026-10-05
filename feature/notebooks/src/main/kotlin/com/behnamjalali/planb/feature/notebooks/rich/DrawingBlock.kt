package com.behnamjalali.planb.feature.notebooks.rich

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.Icon
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.Attachment
import com.behnamjalali.planb.core.model.rich.Drawing
import com.behnamjalali.planb.core.model.rich.DrawingHistory
import com.behnamjalali.planb.core.model.rich.RichBlocks
import com.behnamjalali.planb.core.model.rich.Stroke
import com.behnamjalali.planb.core.ui.ProBadge
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.rememberProGuard
import com.behnamjalali.planb.feature.notebooks.EditorBlock
import com.behnamjalali.planb.feature.notebooks.R
import java.io.ByteArrayOutputStream

/** The paper a drawing is shown on, in both themes (strokes keep their own colors). */
internal val Paper = Color(0xFFFFFDF8)

/** Pen colors: ink, and five clear colors that read on paper. */
internal val PenColors = listOf(0xFF1F1D2B, 0xFF3B74AE, 0xFFC0392B, 0xFF23866F, 0xFFA27A16, 0xFF6B5BD2)

/** A drawing's preview, with "edit" and "convert to text" (#15, #18). */
@Composable
internal fun DrawingBlock(block: EditorBlock, attachment: Attachment?, ui: RichUi?, editable: Boolean) {
    val guard = rememberProGuard()
    val ref = remember(block.data) { RichBlocks.drawing(block.toNoteBlock()) }
    val description = stringResource(R.string.rich_block_drawing)
    val edit = stringResource(R.string.rich_drawing_edit)
    val convert = stringResource(R.string.rich_handwriting_convert)
    var languageMenu by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = description
                if (ui != null) {
                    customActions = listOf(
                        CustomAccessibilityAction(edit) { guard.run(ProFeature.RICH_NOTES) { ui.editDrawing(block.id) }; true },
                        CustomAccessibilityAction(convert) { guard.run(ProFeature.HANDWRITING) { languageMenu = true }; true },
                    )
                }
            }
            .then(if (ui != null) Modifier.clickable { guard.run(ProFeature.RICH_NOTES) { ui.editDrawing(block.id) } } else Modifier),
    ) {
        AttachmentImage(
            file = attachment?.let { ui?.file(it) },
            aspectRatio = ref.width.toFloat() / ref.height.coerceAtLeast(1),
            contentDescription = description,
            background = Paper,
        )
    }
    if (ui != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { guard.run(ProFeature.RICH_NOTES) { ui.editDrawing(block.id) } }) {
                Icon(Icons.Rounded.Edit, contentDescription = null)
                Text(edit)
                if (!editable) ProBadge(Modifier.padding(start = Spacing.xs))
            }
            Box {
                TextButton(onClick = { guard.run(ProFeature.HANDWRITING) { languageMenu = true } }) {
                    Icon(Icons.Rounded.Draw, contentDescription = null)
                    Text(convert)
                }
                DropdownMenu(languageMenu, { languageMenu = false }) {
                    listOf("fa" to R.string.rich_handwriting_persian, "en" to R.string.rich_handwriting_english).forEach { (language, label) ->
                        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                            languageMenu = false
                            ui.editor.convertHandwriting(block.id, language)
                        })
                    }
                }
            }
        }
    }
}

/** Renders strokes to a PNG (on paper) for the note, exports and backups. */
internal object DrawingRenderer {
    fun png(drawing: Drawing, widthPx: Int = drawing.width): ByteArray {
        val scale = widthPx.toFloat() / drawing.width
        val heightPx = (drawing.height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        try {
            val canvas = AndroidCanvas(bitmap)
            canvas.drawColor(Paper.toArgb())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            drawing.strokes.forEach { s ->
                paint.color = s.color.toInt()
                if (s.pointCount == 1) {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(s.x(0) * scale, s.y(0) * scale, s.width * s.pressure(0) * scale / 2, paint)
                    paint.style = Paint.Style.STROKE
                }
                for (i in 1 until s.pointCount) {
                    paint.strokeWidth = (s.width * (s.pressure(i - 1) + s.pressure(i)) / 2 * scale).coerceAtLeast(1f)
                    canvas.drawLine(s.x(i - 1) * scale, s.y(i - 1) * scale, s.x(i) * scale, s.y(i) * scale, paint)
                }
            }
            return ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}

/** Draws strokes scaled from drawing units to this canvas. */
internal fun DrawScope.drawStrokes(strokes: List<Stroke>, drawingWidth: Int) {
    val scale = size.width / drawingWidth
    strokes.forEach { s ->
        val color = Color(s.color.toInt())
        if (s.pointCount == 1) drawCircle(color, s.width * s.pressure(0) * scale / 2, Offset(s.x(0) * scale, s.y(0) * scale))
        for (i in 1 until s.pointCount) {
            drawLine(
                color,
                Offset(s.x(i - 1) * scale, s.y(i - 1) * scale),
                Offset(s.x(i) * scale, s.y(i) * scale),
                strokeWidth = (s.width * (s.pressure(i - 1) + s.pressure(i)) / 2 * scale).coerceAtLeast(1f),
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * The full-screen pen canvas: pen with pressure and width, six colors, a stroke eraser, undo,
 * redo and clear. TalkBack users get undo/redo/clear as actions of the canvas.
 */
@Composable
internal fun DrawingEditorDialog(request: DrawingRequest, onDismiss: () -> Unit, onDone: (Drawing) -> Unit) {
    val history = remember(request) { DrawingHistory(request.drawing) }
    var drawing by remember(request) { mutableStateOf(request.drawing) }
    var color by remember { mutableStateOf(PenColors.first()) }
    var width by remember { mutableFloatStateOf(6f) }
    var eraser by remember { mutableStateOf(false) }
    val current = remember { mutableStateListOf<Int>() }
    var version by remember { mutableIntStateOf(0) }
    var full by remember { mutableStateOf(false) }
    fun commit(next: Drawing) {
        history.push(next)
        drawing = history.current
        version++
    }
    val undo = stringResource(R.string.rich_drawing_undo)
    val redo = stringResource(R.string.rich_drawing_redo)
    val clear = stringResource(R.string.rich_drawing_clear)
    val canvasLabel = stringResource(R.string.rich_drawing_canvas_cd)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlannerIconButton(Icons.Rounded.Close, stringResource(com.behnamjalali.planb.core.ui.R.string.ui_cancel), onDismiss)
                    Text(stringResource(R.string.rich_block_drawing), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    PlannerIconButton(Icons.AutoMirrored.Rounded.Undo, undo, { drawing = history.undo(); version++ }, enabled = version >= 0 && history.canUndo)
                    PlannerIconButton(Icons.AutoMirrored.Rounded.Redo, redo, { drawing = history.redo(); version++ }, enabled = version >= 0 && history.canRedo)
                    PlannerIconButton(Icons.Rounded.Delete, clear, { commit(drawing.copy(strokes = emptyList())) }, enabled = drawing.strokes.isNotEmpty())
                    PlannerIconButton(Icons.Rounded.Check, stringResource(R.string.rich_drawing_done), { onDone(drawing) }, tint = MaterialTheme.colorScheme.primary)
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    ToolButton(Icons.Rounded.Edit, stringResource(R.string.rich_drawing_pen), !eraser) { eraser = false }
                    ToolButton(Icons.Rounded.CleaningServices, stringResource(R.string.rich_drawing_eraser), eraser) { eraser = true }
                    PenColors.forEachIndexed { i, c ->
                        val label = stringResource(R.string.rich_drawing_color, PlannerLocals.numbers.format(i + 1))
                        Box(
                            Modifier
                                .size(36.dp)
                                .padding(4.dp)
                                .background(Color(c.toInt()), CircleShape)
                                .border(if (c == color && !eraser) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .semantics {
                                    contentDescription = label
                                    role = Role.RadioButton
                                    selected = c == color && !eraser
                                }
                                .clickable {
                                    color = c
                                    eraser = false
                                },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.rich_drawing_width), style = MaterialTheme.typography.labelMedium)
                    Slider(value = width, onValueChange = { width = it }, valueRange = 2f..24f, modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm))
                }
                if (full) Text(stringResource(R.string.rich_drawing_full), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    val aspect = drawing.width.toFloat() / drawing.height
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect)
                            .background(Paper, RoundedCornerShape(Radius.sm))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.sm))
                            .semantics {
                                contentDescription = canvasLabel
                                customActions = listOf(
                                    CustomAccessibilityAction(undo) { drawing = history.undo(); version++; true },
                                    CustomAccessibilityAction(redo) { drawing = history.redo(); version++; true },
                                    CustomAccessibilityAction(clear) { commit(drawing.copy(strokes = emptyList())); true },
                                )
                            }
                            .pointerInput(eraser, color, width) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val scale = drawing.width / size.width.toFloat()
                                    fun point(p: Offset, pressure: Float) = listOf((p.x * scale).toInt(), (p.y * scale).toInt(), (pressure.coerceIn(0f, 1f) * 100).toInt().let { if (it == 0) 100 else it })
                                    var erased = drawing
                                    current.clear()
                                    if (eraser) erased = erased.eraseAt(down.position.x * scale, down.position.y * scale, ERASER_RADIUS) else current.addAll(point(down.position, down.pressure))
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        change.consume()
                                        if (eraser) {
                                            erased = erased.eraseAt(change.position.x * scale, change.position.y * scale, ERASER_RADIUS)
                                            drawing = erased
                                        } else {
                                            current.addAll(point(change.position, change.pressure))
                                        }
                                    }
                                    if (eraser) {
                                        drawing = history.current
                                        commit(erased)
                                    } else if (current.isNotEmpty()) {
                                        val next = drawing.plus(Stroke(color, width, current.toList()))
                                        full = next === drawing
                                        current.clear()
                                        commit(next)
                                    }
                                }
                            },
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawStrokes(drawing.strokes, drawing.width)
                            if (current.size >= 3) drawStrokes(listOf(Stroke(color, width, current.toList())), drawing.width)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    PlannerIconButton(
        icon,
        label,
        onClick,
        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.semantics { this.selected = selected },
    )
}

private const val ERASER_RADIUS = 18f
