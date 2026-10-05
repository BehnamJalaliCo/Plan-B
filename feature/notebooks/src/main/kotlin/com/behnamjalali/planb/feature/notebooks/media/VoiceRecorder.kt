package com.behnamjalali.planb.feature.notebooks.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/**
 * Records a voice note as AAC in an MPEG-4 container (`.m4a`), mono 16 kHz — the format the
 * speech service accepts for transcription — into the app's cache until it is attached.
 * [amplitude] (0..1) feeds the live waveform.
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var startedAt = 0L
    var file: File? = null
        private set

    val isRecording: Boolean get() = recorder != null

    val elapsedMillis: Long get() = if (recorder == null) 0 else SystemClock.elapsedRealtime() - startedAt

    /** Starts recording; returns false when the microphone could not be opened. */
    fun start(): Boolean {
        stopAndDiscard()
        val output = File(File(context.cacheDir, "recordings").apply { mkdirs() }, "voice-${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(SAMPLE_RATE)
            r.setAudioEncodingBitRate(BIT_RATE)
            // Stops before the file would exceed the attachment limit (32 MB ≈ 90 minutes).
            r.setMaxFileSize(MAX_BYTES)
            r.setOutputFile(output.path)
            r.prepare()
            r.start()
            recorder = r
            file = output
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (e: Exception) {
            r.release()
            output.delete()
            false
        }
    }

    /** 0..1, the loudness since the last call. */
    fun amplitude(): Float = runCatching { (recorder?.maxAmplitude ?: 0) / MAX_AMPLITUDE }.getOrDefault(0f).coerceIn(0f, 1f)

    /** Stops and returns the recording and its length, or null when nothing usable was recorded. */
    fun stop(): Pair<File, Long>? {
        val r = recorder ?: return null
        val duration = elapsedMillis
        recorder = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        val output = file
        file = null
        if (!ok || output == null || !output.isFile || output.length() == 0L) {
            output?.delete()
            return null
        }
        return output to duration
    }

    fun stopAndDiscard() {
        recorder?.let { r ->
            runCatching { r.stop() }
            r.release()
        }
        recorder = null
        file?.delete()
        file = null
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val BIT_RATE = 48_000
        const val MAX_BYTES = 31L * 1024 * 1024
        const val MAX_AMPLITUDE = 20_000f
    }
}
