package com.behnamjalali.planb.feature.notebooks.media

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import com.behnamjalali.planb.core.speech.DictationEvent
import com.behnamjalali.planb.core.speech.RecognitionListenerAdapter
import com.behnamjalali.planb.core.speech.SpeechIntents
import com.behnamjalali.planb.core.speech.VoiceDictation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Transcription through Android's [SpeechRecognizer] (the speech service installed on the
 * device), preferring on-device recognition (`EXTRA_PREFER_OFFLINE`, and the on-device
 * recognizer where Android offers one). The recognizer plumbing is shared with voice input
 * (`core:speech`).
 *
 * **Recorded files** (Android 13+): the recording is decoded to 16-bit PCM and streamed to the
 * recognizer through `EXTRA_AUDIO_SOURCE` with a segmented session, so the whole file is
 * transcribed. **Older Android versions** cannot hand a file to the speech service, and the
 * microphone cannot record and feed the recognizer at the same time, so there the transcript is
 * dictated live ([VoiceDictation]): the user speaks (or replays) the content and the recognized
 * text is stored as the recording's transcript. Whether audio leaves the device is up to the
 * speech service the user installed; Plan-B itself never sends it.
 */
@Singleton
class PlatformSpeechTranscription @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dictation: VoiceDictation,
) : SpeechTranscription {
    override fun canTranscribeFiles(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && canDictate()

    override fun canDictate(): Boolean = dictation.isAvailable()

    override suspend fun transcribeFile(file: File, languageTag: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !file.isFile) return null
        val format = withContext(Dispatchers.IO) { PcmDecoder.format(file) } ?: return null
        // The on-device recognizer first; if it lacks the language, the regular service.
        for (onDevice in listOf(true, false)) {
            if (onDevice && !SpeechIntents.onDeviceAvailable(context)) continue
            val result = transcribeOnce(file, languageTag, format, onDevice)
            if (result != null) return result.ifBlank { null }
        }
        return null
    }

    /** Null when this recognizer could not be used (so the next one is tried). */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun transcribeOnce(file: File, languageTag: String, format: PcmDecoder.Format, onDevice: Boolean): String? =
        withContext(Dispatchers.Main) {
            val (read, write) = ParcelFileDescriptor.createPipe()
            val feeder: Job = CoroutineScope(Dispatchers.IO).launch {
                runCatching { ParcelFileDescriptor.AutoCloseOutputStream(write).use { PcmDecoder.decode(file, it) } }
            }
            val recognizer = if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
            try {
                suspendCancellableCoroutine { cont ->
                    val text = StringBuilder()
                    fun finish(value: String?) {
                        if (cont.isActive) cont.resume(value)
                    }
                    recognizer.setRecognitionListener(object : RecognitionListenerAdapter() {
                        override fun onSegmentResults(segmentResults: Bundle) = append(text, segmentResults)
                        override fun onEndOfSegmentedSession() = finish(text.toString().trim())
                        override fun onResults(results: Bundle?) {
                            results?.let { append(text, it) }
                            finish(text.toString().trim())
                        }
                        override fun onError(error: Int) = finish(
                            when {
                                text.isNotBlank() -> text.toString().trim()
                                error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> ""
                                else -> null
                            },
                        )
                    })
                    val intent = SpeechIntents.recognize(context, languageTag, partialResults = false)
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read)
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, format.sampleRate)
                        .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                    recognizer.startListening(intent)
                    cont.invokeOnCancellation { recognizer.cancel() }
                }
            } finally {
                recognizer.destroy()
                feeder.cancel()
                runCatching { read.close() }
            }
        }

    override suspend fun dictate(languageTag: String): String? =
        dictation.listen(languageTag)
            .filter { it is DictationEvent.Final || it is DictationEvent.Error }
            .firstOrNull()
            .let { (it as? DictationEvent.Final)?.text?.trim()?.ifBlank { null } }

    private fun append(text: StringBuilder, bundle: Bundle) {
        SpeechIntents.best(bundle)?.takeIf { it.isNotBlank() }?.let {
            if (text.isNotEmpty()) text.append(' ')
            text.append(it)
        }
    }
}

/** Decodes the first audio track of a recording to mono 16-bit little-endian PCM. */
internal object PcmDecoder {
    data class Format(val sampleRate: Int, val channels: Int)

    fun format(file: File): Format? = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: return null
            val f = extractor.getTrackFormat(track)
            Format(f.getInteger(MediaFormat.KEY_SAMPLE_RATE), f.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
        } finally {
            extractor.release()
        }
    }.getOrNull()

    fun decode(file: File, out: OutputStream) {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.path)
        val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            while (true) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    outIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outIndex)!!.order(ByteOrder.LITTLE_ENDIAN)
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val shorts = buffer.asShortBuffer()
                        val frames = shorts.remaining() / channels.coerceAtLeast(1)
                        val bytes = ByteArray(frames * 2)
                        for (i in 0 until frames) {
                            var sum = 0
                            for (c in 0 until channels) sum += shorts.get()
                            val sample = (sum / channels.coerceAtLeast(1)).toShort().toInt()
                            bytes[i * 2] = (sample and 0xFF).toByte()
                            bytes[i * 2 + 1] = (sample shr 8 and 0xFF).toByte()
                        }
                        out.write(bytes)
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
    }

    private const val TIMEOUT_US = 10_000L
}
