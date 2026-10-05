package com.behnamjalali.planb.feature.notebooks.rich

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.Attachment
import com.behnamjalali.planb.core.model.rich.AudioData
import com.behnamjalali.planb.core.model.rich.RichBlocks
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProBadge
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.rememberProGuard
import com.behnamjalali.planb.feature.notebooks.EditorBlock
import com.behnamjalali.planb.feature.notebooks.R
import com.behnamjalali.planb.feature.notebooks.media.VoiceRecorder
import kotlinx.coroutines.delay

/** A voice note: waveform, play/pause, seek, duration and its transcript (#19). */
@Composable
internal fun AudioBlock(block: EditorBlock, attachment: Attachment?, ui: RichUi?, editable: Boolean) {
    val guard = rememberProGuard()
    val formatter = PlannerLocals.formatter
    val waveform = remember(block.data) { RichBlocks.audio(block.toNoteBlock()).waveform }
    val file = attachment?.let { ui?.file(it) }
    val duration = attachment?.durationMillis ?: 0L
    var player by remember(file) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(file) { mutableStateOf(false) }
    var position by remember(file) { mutableLongStateOf(0L) }
    DisposableEffect(file) { onDispose { player?.release() } }
    LaunchedEffect(playing) {
        while (playing) {
            position = player?.currentPosition?.toLong() ?: 0L
            delay(100)
        }
    }
    fun toggle() {
        val f = file ?: return
        val p = player ?: runCatching {
            MediaPlayer().apply {
                setDataSource(f.path)
                prepare()
                setOnCompletionListener {
                    playing = false
                    position = 0
                }
            }
        }.getOrNull()?.also { player = it } ?: return
        if (playing) p.pause() else p.start()
        playing = !playing
    }
    val label = stringResource(R.string.rich_audio_cd, formatter.timer(duration))
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(Radius.md), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = false) { contentDescription = label }) {
                PlannerIconButton(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    stringResource(if (playing) R.string.rich_audio_pause else R.string.rich_audio_play),
                    ::toggle,
                    enabled = file != null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Waveform(waveform, if (duration > 0) position.toFloat() / duration else 0f, Modifier.fillMaxWidth().height(28.dp))
                    val seek = stringResource(R.string.rich_audio_seek)
                    Slider(
                        value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = { f ->
                            position = (f * duration).toLong()
                            player?.seekTo(position.toInt())
                        },
                        enabled = file != null && player != null,
                        modifier = Modifier.height(24.dp).semantics { contentDescription = seek },
                    )
                }
                Text(
                    "${formatter.timer(position)} / ${formatter.timer(duration)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
            if (attachment != null && ui != null) {
                val persian = androidx.compose.ui.text.intl.Locale.current.language == "fa"
                TextButton(onClick = {
                    guard.run(ProFeature.VOICE_NOTES) { ui.transcribe(block.id, if (persian) "fa-IR" else "en-US") }
                }) {
                    Text(stringResource(R.string.rich_audio_transcribe))
                    if (!editable) ProBadge(Modifier.padding(start = Spacing.xs))
                }
            }
            attachment?.transcript?.let { RecognizedTextCard(it, expandedByDefault = true, title = R.string.rich_audio_transcript) }
        }
    }
}

/** Loudness bars; the played part in the primary color, in reading order. */
@Composable
internal fun Waveform(bars: List<Int>, progress: Float, modifier: Modifier = Modifier) {
    val played = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.clearAndSetSemantics { }) {
        if (bars.isEmpty()) {
            drawLine(rest, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx())
            return@Canvas
        }
        val rtl = layoutDirection == LayoutDirection.Rtl
        val slot = size.width / bars.size
        val barWidth = (slot * 0.6f).coerceAtLeast(1f)
        bars.forEachIndexed { i, v ->
            val h = (size.height * (v.coerceIn(4, 100) / 100f))
            val index = if (rtl) bars.lastIndex - i else i
            val color = if (i.toFloat() / bars.size < progress) played else rest
            drawRoundRect(color, Offset(index * slot + (slot - barWidth) / 2, (size.height - h) / 2), Size(barWidth, h), CornerRadius(barWidth / 2))
        }
    }
}

/** Records a voice note (the microphone permission was granted before this opens). */
@Composable
internal fun RecorderDialog(ui: RichUi, onClose: () -> Unit) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context.applicationContext) }
    val samples = remember { mutableStateListOf<Float>() }
    var elapsed by remember { mutableLongStateOf(0L) }
    var level by remember { mutableFloatStateOf(0f) }
    val name = stringResource(R.string.rich_audio_name)
    LaunchedEffect(recorder) {
        if (!recorder.start()) {
            ui.toast(R.string.rich_error_microphone)
            onClose()
            return@LaunchedEffect
        }
        while (recorder.isRecording) {
            level = recorder.amplitude()
            samples += level
            elapsed = recorder.elapsedMillis
            delay(100)
        }
    }
    DisposableEffect(recorder) { onDispose { recorder.stopAndDiscard() } }
    // Tapping outside never throws a recording away: only "Discard" does.
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.rich_audio_recording)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Icon(Icons.Rounded.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(PlannerLocals.formatter.timer(elapsed), style = MaterialTheme.typography.headlineSmall)
                }
                Waveform(AudioData.bars(samples.takeLast(240), AudioData.BARS), 1f, Modifier.fillMaxWidth().height(48.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                recorder.stop()?.let { (file, duration) -> ui.editor.addRecording(file, duration, samples.toList(), name) }
                onClose()
            }) { Text(stringResource(R.string.rich_audio_stop)) }
        },
        dismissButton = {
            TextButton(onClick = {
                recorder.stopAndDiscard()
                onClose()
            }) { Text(stringResource(R.string.rich_audio_cancel)) }
        },
    )
}
