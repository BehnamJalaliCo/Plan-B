package com.behnamjalali.planb.feature.notebooks.rich

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.CompositionLocalProvider
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.AttachmentKind
import com.behnamjalali.planb.feature.notebooks.NoteEditorState
import com.behnamjalali.planb.feature.notebooks.R
import java.io.File
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The rich blocks' dialogs: drawing editor, recorder, page crop, microphone and model consent. */
@Composable
internal fun RichOverlays(state: NoteEditorState, ui: RichUi?) {
    if (ui == null) return
    val scope = rememberCoroutineScope()
    ui.drawingRequest?.let { request ->
        DrawingEditorDialog(
            request = request,
            onDismiss = { ui.drawingRequest = null },
            onDone = { drawing ->
                ui.drawingRequest = null
                if (drawing.strokes.isNotEmpty() || request.blockId != null) {
                    scope.launch {
                        val png = withContext(Dispatchers.Default) { DrawingRenderer.png(drawing) }
                        ui.editor.saveDrawing(request.blockId, drawing, png, drawing.width, drawing.height)
                    }
                }
            },
        )
    }
    if (ui.recorderOpen) RecorderDialog(ui) { ui.recorderOpen = false }
    ui.cropFile?.let { file ->
        CropDialog(
            file = file,
            onDismiss = {
                ui.cropFile = null
                file.delete()
            },
            onCropped = { cropped ->
                ui.cropFile = null
                file.delete()
                ui.editor.addCaptured(cropped, AttachmentKind.SCAN, cropped.name)
            },
        )
    }
    when (ui.micDialog) {
        MicDialog.RATIONALE -> PlannerDialog(
            title = stringResource(R.string.rich_audio_permission_title),
            message = stringResource(R.string.rich_audio_permission_message),
            onDismiss = { ui.micDialog = null },
            confirmLabel = stringResource(R.string.rich_audio_continue),
            onConfirm = {
                ui.micDialog = null
                ui.launchMicPermission()
            },
        )
        MicDialog.SETTINGS -> PlannerDialog(
            title = stringResource(R.string.rich_audio_permission_title),
            message = stringResource(R.string.rich_audio_permission_settings),
            onDismiss = { ui.micDialog = null },
            confirmLabel = stringResource(R.string.rich_audio_open_settings),
            onConfirm = {
                ui.micDialog = null
                ui.openAppSettings()
            },
        )
        null -> Unit
    }
    ui.dictation?.let { (blockId, language) ->
        PlannerDialog(
            title = stringResource(R.string.rich_audio_transcribe),
            message = stringResource(R.string.rich_audio_dictate_hint),
            onDismiss = { ui.dictation = null },
            confirmLabel = stringResource(R.string.rich_audio_continue),
            onConfirm = {
                ui.dictation = null
                ui.editor.transcribe(blockId, language)
            },
        )
    }
    state.handwritingConsent?.let { consent ->
        var wifiOnly by remember(consent) { mutableStateOf(true) }
        val language = stringResource(if (consent.language == "fa") R.string.rich_handwriting_persian else R.string.rich_handwriting_english)
        PlannerDialog(
            title = stringResource(R.string.rich_handwriting_consent_title),
            message = stringResource(R.string.rich_handwriting_consent_message, language),
            onDismiss = ui.editor::dismissConsent,
            confirmLabel = stringResource(R.string.rich_handwriting_download),
            onConfirm = { ui.editor.convertHandwriting(consent.blockId, consent.language, consented = true, wifiOnly = wifiOnly) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
                Text(stringResource(R.string.rich_handwriting_wifi_only))
            }
        }
    }
}

/**
 * Manual crop for scans taken with the camera when the document scanner is not available
 * (no Google Play services): drag the corners to the page's edges.
 */
@Composable
private fun CropDialog(file: File, onDismiss: () -> Unit, onCropped: (File) -> Unit) {
    val scope = rememberCoroutineScope()
    val bitmap by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { decodeForCrop(file) }
    }
    // Corners as fractions of the image: left, top, right, bottom.
    var rect by remember(file) { mutableStateOf(Rect(0.05f, 0.05f, 0.95f, 0.95f)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.rich_crop_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.rich_crop_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    val image = bitmap
                    if (image != null) {
                        // Image coordinates do not mirror, so the crop area is laid out left to right.
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Box(Modifier.aspectRatio(image.width.toFloat() / image.height)) {
                                Image(image.asImageBitmap(), stringResource(R.string.rich_crop_cd), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                val handle = MaterialTheme.colorScheme.primary
                                Canvas(
                                    Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            var corner = -1
                                            detectDragGestures(
                                                onDragStart = { start ->
                                                    val w = size.width.toFloat()
                                                    val h = size.height.toFloat()
                                                    val corners = listOf(
                                                        Offset(rect.left * w, rect.top * h), Offset(rect.right * w, rect.top * h),
                                                        Offset(rect.left * w, rect.bottom * h), Offset(rect.right * w, rect.bottom * h),
                                                    )
                                                    corner = corners.indices.minBy { hypot(corners[it].x - start.x, corners[it].y - start.y) }
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    val x = (change.position.x / size.width).coerceIn(0f, 1f)
                                                    val y = (change.position.y / size.height).coerceIn(0f, 1f)
                                                    rect = when (corner) {
                                                        0 -> rect.copy(left = minOf(x, rect.right - MIN_CROP), top = minOf(y, rect.bottom - MIN_CROP))
                                                        1 -> rect.copy(right = maxOf(x, rect.left + MIN_CROP), top = minOf(y, rect.bottom - MIN_CROP))
                                                        2 -> rect.copy(left = minOf(x, rect.right - MIN_CROP), bottom = maxOf(y, rect.top + MIN_CROP))
                                                        else -> rect.copy(right = maxOf(x, rect.left + MIN_CROP), bottom = maxOf(y, rect.top + MIN_CROP))
                                                    }
                                                },
                                            )
                                        },
                                ) {
                                    val r = Rect(rect.left * size.width, rect.top * size.height, rect.right * size.width, rect.bottom * size.height)
                                    val shade = Color.Black.copy(alpha = 0.45f)
                                    drawRect(shade, Offset.Zero, Size(size.width, r.top))
                                    drawRect(shade, Offset(0f, r.bottom), Size(size.width, size.height - r.bottom))
                                    drawRect(shade, Offset(0f, r.top), Size(r.left, r.height))
                                    drawRect(shade, Offset(r.right, r.top), Size(size.width - r.right, r.height))
                                    drawRect(handle, r.topLeft, r.size, style = Stroke(2.dp.toPx()))
                                    listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight).forEach { drawCircle(handle, 10.dp.toPx(), it) }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(com.behnamjalali.planb.core.ui.R.string.ui_cancel)) }
                    TextButton(
                        enabled = bitmap != null,
                        onClick = {
                            val source = bitmap ?: return@TextButton
                            scope.launch {
                                val out = withContext(Dispatchers.IO) { crop(source, rect, File(file.parentFile, "page-${System.currentTimeMillis()}.jpg")) }
                                if (out != null) onCropped(out) else onDismiss()
                            }
                        },
                    ) { Text(stringResource(R.string.rich_drawing_done)) }
                }
            }
        }
    }
}

private const val MIN_CROP = 0.1f
private const val MAX_CROP_SOURCE = 3000

private fun decodeForCrop(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_CROP_SOURCE) sample *= 2
    val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    // Camera photos are often stored sideways with an EXIF rotation; crop what the user saw.
    val degrees = runCatching {
        when (androidx.exifinterface.media.ExifInterface(file.path).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    if (degrees == 0f) return decoded
    val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, android.graphics.Matrix().apply { postRotate(degrees) }, true)
    if (rotated !== decoded) decoded.recycle()
    return rotated
}

private fun crop(source: Bitmap, rect: Rect, target: File): File? = runCatching {
    val x = (rect.left * source.width).toInt().coerceIn(0, source.width - 1)
    val y = (rect.top * source.height).toInt().coerceIn(0, source.height - 1)
    val w = ((rect.right - rect.left) * source.width).toInt().coerceIn(1, source.width - x)
    val h = ((rect.bottom - rect.top) * source.height).toInt().coerceIn(1, source.height - y)
    val cropped = Bitmap.createBitmap(source, x, y, w, h)
    target.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    if (cropped !== source) cropped.recycle()
    target
}.getOrNull()
