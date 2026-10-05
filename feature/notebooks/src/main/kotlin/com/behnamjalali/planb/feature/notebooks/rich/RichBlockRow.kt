package com.behnamjalali.planb.feature.notebooks.rich

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.Attachment
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.feature.notebooks.EditorBlock
import com.behnamjalali.planb.feature.notebooks.NoteEditorState
import com.behnamjalali.planb.feature.notebooks.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A rich block in the note editor. Everyone can read every block; editing it is Plan-B Pro
 * ([editable]): without Pro, blocks are shown read-only and their edit buttons open the Pro
 * screen. The block's frame is focusable, so the toolbar's move and delete act on it.
 */
@Composable
internal fun RichBlockRow(
    block: EditorBlock,
    state: NoteEditorState,
    ui: RichUi?,
    focusRequester: FocusRequester,
    onFocus: (String) -> Unit,
) {
    val editable = LocalProAccess.current.isPro && ui != null
    var focused by remember { mutableStateOf(false) }
    val attachment = block.attachmentId?.let { state.attachments[it] }
    val work = state.richWork[block.id]
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .border(
                BorderStroke(1.dp, if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent),
                RoundedCornerShape(Radius.md),
            )
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.hasFocus
                if (it.hasFocus) onFocus(block.id)
            }
            .focusable()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focusRequester.requestFocus() }
            .padding(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        when (block.type) {
            BlockType.IMAGE, BlockType.SCAN -> ImageBlock(block, attachment, ui, editable)
            BlockType.FILE -> FileBlock(block, attachment, ui, editable)
            BlockType.TABLE -> TableBlock(block, ui, editable)
            BlockType.DATABASE -> DatabaseBlock(block, ui, editable)
            BlockType.DRAWING -> DrawingBlock(block, attachment, ui, editable)
            BlockType.AUDIO -> AudioBlock(block, attachment, ui, editable)
            BlockType.MATH -> MathBlock(block, ui, editable, focused)
            BlockType.CHART -> ChartBlock(block, state.blocks, ui, editable)
            else -> Unit
        }
        if (work != null) WorkIndicator(work)
    }
}

@Composable
internal fun WorkIndicator(work: RichWork) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            stringResource(
                when (work) {
                    RichWork.IMPORTING -> R.string.rich_importing
                    RichWork.RECOGNIZING -> R.string.rich_recognizing
                    RichWork.TRANSCRIBING -> R.string.rich_transcribing
                    RichWork.DOWNLOADING_MODEL -> R.string.rich_downloading_model
                    RichWork.CONVERTING -> R.string.rich_converting
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Decoded attachment previews, kept in memory (about an eighth of the app's heap at most). */
internal object AttachmentBitmaps {
    private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt().coerceAtLeast(1024)) {
        override fun sizeOf(key: String, value: ImageBitmap) = (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

    private fun key(file: File, width: Int) = "${file.path}@$width"

    fun cached(file: File, width: Int): ImageBitmap? = cache.get(key(file, width))

    /** Decodes [file] subsampled to about [width] pixels wide (powers of two). */
    fun load(file: File, width: Int): ImageBitmap? {
        cached(file, width)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return bitmap.asImageBitmap().also { cache.put(key(file, width), it) }
    }
}

/** An attachment image that fills the width; a placeholder while it loads or when the file is gone. */
@Composable
internal fun AttachmentImage(
    file: File?,
    aspectRatio: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Widths are bucketed so a resize does not decode again for every pixel.
        val width = with(LocalDensity.current) { maxWidth.toPx().toInt() }.coerceAtLeast(1).let { ((it + 255) / 256) * 256 }
        val bitmap by produceState(file?.let { AttachmentBitmaps.cached(it, width) }, file, width) {
            if (value == null && file != null) value = withContext(Dispatchers.IO) { runCatching { AttachmentBitmaps.load(file, width) }.getOrNull() }
        }
        val shape = RoundedCornerShape(Radius.md)
        val boxModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceIn(0.3f, 4f))
            .clip(shape)
            .background(background, shape)
        val current = bitmap
        if (current != null) {
            Image(current, contentDescription, boxModifier.testTag(LOADED_TAG), contentScale = ContentScale.Fit)
        } else {
            Box(boxModifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                if (file == null) Icon(Icons.Rounded.BrokenImage, stringResource(R.string.rich_missing_file), tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/** Screenshot tests wait for this tag before capturing a note with images. */
const val LOADED_TAG = "rich_image_loaded"

internal fun Attachment.aspect(): Float = if ((width ?: 0) > 0 && (height ?: 0) > 0) width!!.toFloat() / height!! else 4f / 3f
