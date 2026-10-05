package com.behnamjalali.planb.feature.notebooks.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.behnamjalali.planb.core.model.rich.Drawing
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/**
 * Text recognition in photos and scans (Plan-B Pro #17). Runs on the device only; returns
 * null when no engine could read anything.
 */
interface TextRecognition {
    suspend fun recognize(image: File): String?
}

/** Handwriting to text (Plan-B Pro #18) with downloadable models per language ("fa", "en"). */
interface HandwritingRecognition {
    suspend fun isModelReady(language: String): Boolean

    /** Downloads the model (needs the network; [wifiOnly] waits for Wi-Fi). Returns false on failure. */
    suspend fun downloadModel(language: String, wifiOnly: Boolean): Boolean

    suspend fun recognize(drawing: Drawing, language: String): String?
}

/** Speech to text for voice notes (Plan-B Pro #19), through the device's speech service. */
interface SpeechTranscription {
    /** The platform can transcribe a recorded file (Android 13+ with a speech service). */
    fun canTranscribeFiles(): Boolean

    /** Live dictation from the microphone is possible (any speech service). */
    fun canDictate(): Boolean

    suspend fun transcribeFile(file: File, languageTag: String): String?

    /** Listens to the microphone until the speaker stops; needs the microphone permission. */
    suspend fun dictate(languageTag: String): String?
}

/** Reads what the user picked (gallery, files, the document scanner). */
interface PickedContent {
    fun open(uri: Uri): InputStream
    fun displayName(uri: Uri): String
    fun mimeType(uri: Uri): String
}

class ContentResolverPickedContent @Inject constructor(@ApplicationContext private val context: Context) : PickedContent {
    override fun open(uri: Uri): InputStream = context.contentResolver.openInputStream(uri) ?: error("Cannot open the picked item")

    override fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()

    override fun mimeType(uri: Uri): String = context.contentResolver.getType(uri) ?: "application/octet-stream"
}

/** Merges recognizers' outputs: each distinct line once, in order. */
object RecognizedText {
    fun merge(vararg parts: String?): String? {
        val lines = parts.filterNotNull().flatMap { it.lines() }.map { it.trim() }.filter { it.isNotEmpty() }
        val seen = mutableSetOf<String>()
        return lines.filter { seen.add(it) }.joinToString("\n").ifBlank { null }
    }

    /** Share of letters that are Arabic-script (Persian) letters. */
    fun persianShare(text: String): Double {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return 0.0
        return letters.count { it.code in 0x0600..0x06FF || it.code in 0xFB50..0xFDFF || it.code in 0xFE70..0xFEFC }.toDouble() / letters.length
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NoteMediaModule {
    @Binds abstract fun text(impl: DeviceTextRecognition): TextRecognition
    @Binds abstract fun handwriting(impl: MlKitHandwritingRecognition): HandwritingRecognition
    @Binds abstract fun speech(impl: PlatformSpeechTranscription): SpeechTranscription
    @Binds abstract fun picked(impl: ContentResolverPickedContent): PickedContent
}
