package com.behnamjalali.planb.core.speech

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/** What live dictation reports, in order: ready, levels and partial text, then a final text or an error. */
sealed interface DictationEvent {
    data object Ready : DictationEvent

    /** Loudness from 0 (silence) to 1, for the listening animation. */
    data class Level(val level: Float) : DictationEvent

    /** What was heard so far (replaced, not appended, by the next one). */
    data class Partial(val text: String) : DictationEvent

    /** The recognized text (may be empty when nothing was understood). */
    data class Final(val text: String) : DictationEvent

    data class Error(val error: DictationError) : DictationEvent
}

/** Neutral reasons dictation stopped; each has its own calm message. */
enum class DictationError { NO_SPEECH, UNAVAILABLE, PERMISSION, NETWORK, BUSY, LANGUAGE, OTHER }

/** Live speech to text from the microphone (Plan-B Pro #40). Needs `RECORD_AUDIO`. */
interface VoiceDictation {
    /** A speech service exists on this device. */
    fun isAvailable(): Boolean

    /**
     * Listens until the speaker stops. Cold: collecting starts the microphone, cancelling stops
     * it. The flow ends after [DictationEvent.Final] or [DictationEvent.Error].
     */
    fun listen(languageTag: String): Flow<DictationEvent>
}

/** The recognizer language for the app language: Persian (Iran) or English (US). */
fun speechLanguageTag(locale: Locale): String = if (locale.language == "fa") "fa-IR" else "en-US"

/** The recognizer intent shared by dictation and voice-note transcription. */
object SpeechIntents {
    fun recognize(context: Context, languageTag: String, partialResults: Boolean): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            // On-device recognition when the speech service has the language.
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partialResults)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

    /** Android offers a separate on-device recognizer (Android 12+), used first when present. */
    fun onDeviceAvailable(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

    fun isAvailable(context: Context): Boolean = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    /** The first (best) hypothesis of a results bundle. */
    fun best(bundle: Bundle?): String? = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()

    /** Codes added in newer Android versions are only compared, never passed to the platform. */
    @SuppressLint("InlinedApi")
    fun errorOf(code: Int): DictationError = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> DictationError.NO_SPEECH
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> DictationError.PERMISSION
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        -> DictationError.NETWORK
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> DictationError.BUSY
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> DictationError.LANGUAGE
        else -> DictationError.OTHER
    }

    /** Maps the recognizer's RMS dB (about −2…10) to 0…1. */
    fun level(rmsDb: Float): Float = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
}

/** A [RecognitionListener] whose callbacks do nothing unless overridden. */
abstract class RecognitionListenerAdapter : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onError(error: Int) = Unit
    override fun onResults(results: Bundle?) = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}

/**
 * Dictation through Android's [SpeechRecognizer], on the main thread as it requires. The
 * on-device recognizer is tried first; if it lacks the language the regular speech service
 * takes over (still asked to prefer offline recognition).
 */
@Singleton
class PlatformVoiceDictation @Inject constructor(@ApplicationContext private val context: Context) : VoiceDictation {
    override fun isAvailable(): Boolean = SpeechIntents.isAvailable(context)

    override fun listen(languageTag: String): Flow<DictationEvent> = callbackFlow {
        if (!isAvailable()) {
            trySend(DictationEvent.Error(DictationError.UNAVAILABLE))
            close()
            awaitClose()
            return@callbackFlow
        }
        var recognizer: SpeechRecognizer? = null
        var heard = ""

        fun start(onDevice: Boolean) {
            recognizer?.destroy()
            val created = if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            recognizer = created
            created.setRecognitionListener(object : RecognitionListenerAdapter() {
                override fun onReadyForSpeech(params: Bundle?) {
                    trySend(DictationEvent.Ready)
                }

                override fun onRmsChanged(rmsdB: Float) {
                    trySend(DictationEvent.Level(SpeechIntents.level(rmsdB)))
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    SpeechIntents.best(partialResults)?.takeIf { it.isNotEmpty() }?.let {
                        heard = it
                        trySend(DictationEvent.Partial(it))
                    }
                }

                override fun onResults(results: Bundle?) {
                    trySend(DictationEvent.Final(SpeechIntents.best(results)?.ifEmpty { null } ?: heard))
                    close()
                }

                override fun onError(error: Int) {
                    val reason = SpeechIntents.errorOf(error)
                    when {
                        // The on-device recognizer may not have Persian: use the speech service.
                        onDevice && heard.isEmpty() && reason != DictationError.PERMISSION && reason != DictationError.NO_SPEECH -> start(onDevice = false)
                        heard.isNotEmpty() -> {
                            trySend(DictationEvent.Final(heard))
                            close()
                        }
                        else -> {
                            trySend(DictationEvent.Error(reason))
                            close()
                        }
                    }
                }
            })
            created.startListening(SpeechIntents.recognize(context, languageTag, partialResults = true))
        }

        try {
            start(onDevice = SpeechIntents.onDeviceAvailable(context))
        } catch (e: RuntimeException) {
            // A broken speech service (SecurityException, IllegalStateException…).
            trySend(DictationEvent.Error(DictationError.UNAVAILABLE))
            close()
        }
        awaitClose {
            recognizer?.let {
                runCatching { it.cancel() }
                it.destroy()
            }
        }
    }.flowOn(Dispatchers.Main.immediate)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SpeechModule {
    @Binds abstract fun dictation(impl: PlatformVoiceDictation): VoiceDictation
}
