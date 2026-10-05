package com.behnamjalali.planb.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Limits of attachment files. They match the backup format's limits (`BackupFormat`), so every
 * attachment the app stores also fits into a backup and restores again.
 */
object AttachmentLimits {
    /** Largest single file (also the largest image or recording before it is stored). */
    const val MAX_FILE_BYTES = 32L * 1024 * 1024

    /** All attachments together. */
    const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024

    const val MAX_COUNT = 10_000

    /** Photos and scans are scaled down so their longer side is at most this many pixels. */
    const val MAX_IMAGE_DIMENSION = 2560

    /** JPEG quality of stored photos and scans. */
    const val IMAGE_QUALITY = 85

    /** A picked photo may be larger than [MAX_FILE_BYTES] before it is scaled down. */
    const val MAX_SOURCE_IMAGE_BYTES = 64L * 1024 * 1024
}

/** Pure size arithmetic, kept apart from Android so it is unit-testable. */
object ImageSizing {
    /** The size that fits [maxDimension] on the longer side, never larger than the original. */
    fun fit(width: Int, height: Int, maxDimension: Int = AttachmentLimits.MAX_IMAGE_DIMENSION): Pair<Int, Int> {
        val longer = max(width, height)
        if (longer <= maxDimension || longer <= 0) return width to height
        val scale = maxDimension.toDouble() / longer
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    /** The largest power-of-two subsampling that still decodes at least [target] pixels on the longer side. */
    fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        val longer = max(width, height)
        while (longer / (sample * 2) >= target) sample *= 2
        return sample
    }
}

/** A stored image: its MIME type and pixel size. */
data class StoredImage(val mimeType: String, val width: Int, val height: Int)

class UnsupportedImageException : Exception("Not a readable image")

/** Decodes, rotates (EXIF), scales and re-encodes images. */
interface ImageProcessor {
    /**
     * Writes [source] to [target] scaled to [AttachmentLimits.MAX_IMAGE_DIMENSION] as JPEG
     * (PNG when the image has transparency). Throws [UnsupportedImageException].
     */
    fun store(source: File, target: File): StoredImage
}

class BitmapImageProcessor @Inject constructor() : ImageProcessor {
    override fun store(source: File, target: File): StoredImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw UnsupportedImageException()
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageSizing.sampleSize(bounds.outWidth, bounds.outHeight, AttachmentLimits.MAX_IMAGE_DIMENSION)
        }
        val decoded = BitmapFactory.decodeFile(source.path, options) ?: throw UnsupportedImageException()
        val (w, h) = ImageSizing.fit(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            if (w != decoded.width || h != decoded.height) postScale(w.toFloat() / decoded.width, h.toFloat() / decoded.height)
            val degrees = rotation(source)
            if (degrees != 0) postRotate(degrees.toFloat())
        }
        val bitmap = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        try {
            val png = bitmap.hasAlpha()
            target.outputStream().use { out ->
                val ok = if (png) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) else bitmap.compress(Bitmap.CompressFormat.JPEG, AttachmentLimits.IMAGE_QUALITY, out)
                if (!ok) throw UnsupportedImageException()
            }
            return StoredImage(if (png) "image/png" else "image/jpeg", bitmap.width, bitmap.height)
        } finally {
            if (bitmap !== decoded) bitmap.recycle()
            decoded.recycle()
        }
    }

    private fun rotation(file: File): Int = runCatching {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)
}
