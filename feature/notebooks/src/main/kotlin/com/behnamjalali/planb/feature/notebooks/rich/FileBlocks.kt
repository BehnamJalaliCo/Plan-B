package com.behnamjalali.planb.feature.notebooks.rich

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.Attachment
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.rememberProGuard
import com.behnamjalali.planb.feature.notebooks.EditorBlock
import com.behnamjalali.planb.feature.notebooks.R

/** A photo or a scanned page with its caption, recognized text and actions (#15, #17). */
@Composable
internal fun ImageBlock(block: EditorBlock, attachment: Attachment?, ui: RichUi?, editable: Boolean) {
    val guard = rememberProGuard()
    val scan = block.type == BlockType.SCAN
    val caption = block.value.text
    val description = if (scan) stringResource(R.string.rich_scan_cd) else if (caption.isBlank()) stringResource(R.string.rich_image_cd_plain) else stringResource(R.string.rich_image_cd, caption)
    AttachmentImage(
        file = attachment?.let { ui?.file(it) ?: null },
        aspectRatio = attachment?.aspect() ?: (4f / 3f),
        contentDescription = description,
        background = if (scan) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
    )
    if (!scan) CaptionField(block, ui, editable)
    if (attachment != null && ui != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlannerIconButton(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.rich_open), { ui.open(attachment) })
            PlannerIconButton(Icons.Rounded.Share, stringResource(R.string.rich_share), { ui.share(attachment) })
            TextButton(onClick = { guard.run(ProFeature.DOCUMENT_SCAN) { ui.editor.recognizeText(block.id) } }) {
                Text(stringResource(if (attachment.ocrText == null) R.string.rich_recognize_text else R.string.rich_recognize_again))
            }
        }
    }
    attachment?.ocrText?.let { RecognizedTextCard(it, expandedByDefault = scan) }
}

/** The text recognized in an image; selectable so it can be copied. */
@Composable
internal fun RecognizedTextCard(text: String, expandedByDefault: Boolean, title: Int = R.string.rich_recognized_text) {
    var expanded by rememberSaveable { mutableStateOf(expandedByDefault) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(Radius.sm), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.TextSnippet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(title),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm),
                )
                PlannerIconButton(
                    if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    stringResource(title),
                    { expanded = !expanded },
                )
            }
            if (expanded) {
                SelectionContainer {
                    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = Spacing.sm))
                }
            }
        }
    }
}

@Composable
internal fun CaptionField(block: EditorBlock, ui: RichUi?, editable: Boolean) {
    val style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (!editable || ui == null) {
        if (block.value.text.isNotBlank()) Text(block.value.text, style = style)
        return
    }
    var text by remember(block.id, block.revision) { mutableStateOf(block.value.text) }
    val hint = stringResource(R.string.rich_caption_hint)
    BasicTextField(
        value = text,
        onValueChange = {
            text = it.replace('\n', ' ')
            ui.update(block.id, text = text)
        },
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
        decorationBox = { inner ->
            if (text.isEmpty()) Text(hint, style = style.copy(color = MaterialTheme.colorScheme.outline))
            inner()
        },
    )
}

/** Any file: its name and size, opened or shared with another app (#15). */
@Composable
internal fun FileBlock(block: EditorBlock, attachment: Attachment?, ui: RichUi?, editable: Boolean) {
    val context = LocalContext.current
    val numbers = PlannerLocals.numbers
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(Radius.md), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(
                    attachment?.displayName?.ifBlank { null } ?: stringResource(if (attachment == null) R.string.rich_missing_file else R.string.rich_block_file),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (attachment != null) {
                    val size = numbers.localize(Formatter.formatShortFileSize(context, attachment.sizeBytes))
                    val type = attachment.displayName.substringAfterLast('.', "").uppercase().ifBlank { attachment.mimeType.substringAfter('/') }
                    Text(stringResource(R.string.rich_file_size, type, size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (attachment != null && ui != null) {
                PlannerIconButton(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.rich_open), { ui.open(attachment) })
                PlannerIconButton(Icons.Rounded.Share, stringResource(R.string.rich_share), { ui.share(attachment) })
            }
        }
    }
    CaptionField(block, ui, editable)
}
