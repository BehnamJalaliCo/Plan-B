package com.behnamjalali.planb.feature.notebooks.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition as MlKitTextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * On-device OCR in two engines:
 * - **Latin** text: ML Kit text recognition through Google Play services (the model lives in
 *   Play services, nothing is bundled). Skipped on devices without Play services.
 * - **Persian/Arabic** text: Tesseract (Tesseract4Android) with the `fas` model from
 *   tessdata_fast, bundled in the APK's assets (~0.4 MB) and copied to private storage once.
 *   ML Kit cannot read Arabic script.
 * Tesseract's output is kept only when it is confident and mostly Persian, so a Latin page does
 * not come back as Persian-looking noise. Nothing is sent anywhere.
 */
@Singleton
class DeviceTextRecognition @Inject constructor(@ApplicationContext private val context: Context) : TextRecognition {
    private val tesseractLock = Mutex()

    override suspend fun recognize(image: File): String? = withContext(Dispatchers.Default) {
        val bitmap = decode(image) ?: return@withContext null
        try {
            val latin = if (playServicesAvailable()) runCatching { latin(bitmap) }.getOrNull() else null
            val persian = runCatching { tesseractLock.withLock { persian(bitmap) } }.getOrNull()
            RecognizedText.merge(persian, latin)
        } finally {
            bitmap.recycle()
        }
    }

    private fun playServicesAvailable(): Boolean = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    private suspend fun latin(bitmap: Bitmap): String? {
        val client = MlKitTextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return client.process(InputImage.fromBitmap(bitmap, 0)).await().text.takeIf { it.isNotBlank() }
        } finally {
            client.close()
        }
    }

    private fun persian(bitmap: Bitmap): String? {
        val dataPath = tessData() ?: return null
        val api = TessBaseAPI()
        try {
            if (!api.init(dataPath.path, LANGUAGE)) return null
            api.setImage(bitmap)
            val text = api.utF8Text?.trim().orEmpty()
            val confident = api.meanConfidence() >= MIN_CONFIDENCE
            return text.takeIf { confident && RecognizedText.persianShare(it) >= MIN_PERSIAN_SHARE }
        } finally {
            api.recycle()
        }
    }

    /** The folder that holds `tessdata/fas.traineddata`, copied from the assets on first use. */
    private fun tessData(): File? = runCatching {
        val root = File(context.filesDir, "ocr")
        val model = File(root, "tessdata/$LANGUAGE.traineddata")
        if (!model.isFile || model.length() == 0L) {
            model.parentFile?.mkdirs()
            val temp = File(model.parentFile, "$LANGUAGE.tmp")
            context.assets.open("tessdata/$LANGUAGE.traineddata").use { input -> temp.outputStream().use { input.copyTo(it) } }
            if (!temp.renameTo(model)) error("Cannot install the OCR model")
        }
        root
    }.getOrNull()

    private fun decode(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_OCR_DIMENSION) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private companion object {
        const val LANGUAGE = "fas"
        const val MIN_CONFIDENCE = 55
        const val MIN_PERSIAN_SHARE = 0.6
        const val MAX_OCR_DIMENSION = 2000
    }
}
