package com.behnamjalali.planb.feature.notebooks.media

import com.behnamjalali.planb.core.model.rich.Drawing
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * ML Kit Digital Ink Recognition. The recognizer runs on the device; its language models
 * ("fa" Persian, "en-US" English, about 20 MB each) are downloaded from Google's servers on
 * first use, only after the user agreed in the drawing block. The drawing's strokes are never
 * sent anywhere.
 */
@Singleton
class MlKitHandwritingRecognition @Inject constructor() : HandwritingRecognition {
    private fun model(language: String): DigitalInkRecognitionModel? = runCatching {
        DigitalInkRecognitionModelIdentifier.fromLanguageTag(if (language == "fa") "fa" else "en-US")
    }.getOrNull()?.let { DigitalInkRecognitionModel.builder(it).build() }

    override suspend fun isModelReady(language: String): Boolean {
        val model = model(language) ?: return false
        return runCatching { RemoteModelManager.getInstance().isModelDownloaded(model).await() }.getOrDefault(false)
    }

    override suspend fun downloadModel(language: String, wifiOnly: Boolean): Boolean {
        val model = model(language) ?: return false
        val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        return runCatching { RemoteModelManager.getInstance().download(model, conditions).await() }.isSuccess
    }

    override suspend fun recognize(drawing: Drawing, language: String): String? {
        val model = model(language) ?: return null
        if (drawing.strokes.isEmpty()) return null
        val recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
        try {
            val ink = Ink.builder()
            var t = 0L
            drawing.strokes.forEach { stroke ->
                val builder = Ink.Stroke.builder()
                for (i in 0 until stroke.pointCount) {
                    // Strokes keep no timestamps; a steady 10 ms per point keeps their order.
                    builder.addPoint(Ink.Point.create(stroke.x(i).toFloat(), stroke.y(i).toFloat(), t))
                    t += POINT_MILLIS
                }
                t += STROKE_GAP_MILLIS
                ink.addStroke(builder.build())
            }
            return recognizer.recognize(ink.build()).await().candidates.firstOrNull()?.text?.takeIf { it.isNotBlank() }
        } finally {
            recognizer.close()
        }
    }

    private companion object {
        const val POINT_MILLIS = 10L
        const val STROKE_GAP_MILLIS = 200L
    }
}
