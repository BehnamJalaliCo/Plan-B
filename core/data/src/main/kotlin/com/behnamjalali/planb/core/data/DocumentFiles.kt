package com.behnamjalali.planb.core.data

import android.content.Context
import android.net.Uri
import com.behnamjalali.planb.core.common.Dispatcher
import com.behnamjalali.planb.core.common.PlanBDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Reads/writes user-selected documents through the Storage Access Framework.
 * All I/O runs off the main thread; no broad storage permission is needed.
 */
@Singleton
class DocumentFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(PlanBDispatcher.IO) private val io: CoroutineDispatcher,
) {
    suspend fun readText(uri: Uri, maxBytes: Int = MAX_TEXT_BYTES): String = withContext(io) {
        input(uri) { stream ->
            val bytes = stream.readNBytesCompat(maxBytes + 1)
            require(bytes.size <= maxBytes) { "File is too large" }
            bytes.toString(Charsets.UTF_8).removePrefix(BYTE_ORDER_MARK)
        }
    }

    suspend fun writeText(uri: Uri, text: String) = withContext(io) {
        output(uri) { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun <T> input(uri: Uri, block: suspend (InputStream) -> T): T = withContext(io) {
        val stream = context.contentResolver.openInputStream(uri) ?: error("Cannot open $uri")
        stream.use { block(it) }
    }

    suspend fun output(uri: Uri, block: suspend (OutputStream) -> Unit) = withContext(io) {
        // "wt" truncates existing content so a shorter export never leaves stale bytes.
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot write $uri")
        stream.use { block(it) }
    }

    private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (total < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - total))
            if (read < 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray()
    }

    companion object {
        const val MAX_TEXT_BYTES = 5 * 1024 * 1024
        private val BYTE_ORDER_MARK = 0xFEFF.toChar().toString()
    }
}
