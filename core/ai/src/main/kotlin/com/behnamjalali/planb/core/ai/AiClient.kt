package com.behnamjalali.planb.core.ai

import androidx.annotation.StringRes
import com.behnamjalali.planb.core.common.Dispatcher
import com.behnamjalali.planb.core.common.PlanBDispatcher
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Where and how to call: resolved from [AiSettings] plus the decrypted key. */
data class AiEndpoint(
    val wireFormat: WireFormat,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
) {
    /** The key is never part of a string representation (logs, crash reports). */
    override fun toString(): String = "AiEndpoint(wireFormat=$wireFormat, baseUrl=$baseUrl, model=$model)"
}

enum class AiRole { SYSTEM, USER, ASSISTANT }

data class AiMessage(val role: AiRole, val text: String)

/**
 * Neutral failure categories. The UI shows [messageRes], which only ever says to check the
 * key or the internet connection: it never speculates about why a connection failed.
 */
enum class AiError(@StringRes val messageRes: Int) {
    INVALID_KEY(R.string.ai_error_invalid_key),
    NETWORK_UNREACHABLE(R.string.ai_error_network),
    RATE_LIMITED(R.string.ai_error_rate_limited),
    PROVIDER_ERROR(R.string.ai_error_provider),

    /** The assistant is off, or the provider, model or key is missing. */
    NOT_CONFIGURED(R.string.ai_error_not_configured),
}

sealed interface AiResult<out T> {
    data class Success<T>(val value: T) : AiResult<T>
    data class Failure(val error: AiError) : AiResult<Nothing>
}

/** One piece of a streamed reply. */
sealed interface AiStreamEvent {
    /** More reply text, in order. */
    data class Delta(val text: String) : AiStreamEvent

    /** The reply is complete. */
    data object Done : AiStreamEvent

    /** The request or the stream failed; text already delivered stays valid. */
    data class Failed(val error: AiError) : AiStreamEvent
}

/** What the assistant needs from a provider client (the app uses [AiClient]; tests use fakes). */
interface AiApi {
    suspend fun chat(endpoint: AiEndpoint, messages: List<AiMessage>, maxTokens: Int = AiClient.DEFAULT_MAX_TOKENS): AiResult<String>

    /**
     * Streams the reply as it is written (server-sent events). Ends with [AiStreamEvent.Done]
     * or [AiStreamEvent.Failed]; cancelling the collector cancels the request.
     */
    fun stream(endpoint: AiEndpoint, messages: List<AiMessage>, maxTokens: Int = AiClient.DEFAULT_MAX_TOKENS): Flow<AiStreamEvent>

    /** A tiny request that proves the base URL, key and model work. */
    suspend fun testConnection(endpoint: AiEndpoint): AiResult<Unit>

    /** Model ids the key can use (for suggestions); the model field stays free text. */
    suspend fun listModels(endpoint: AiEndpoint): AiResult<List<String>>
}

/**
 * A minimal client for the two wire formats. Requests go directly from the device to the
 * provider the user configured; nothing passes through any Plan-B server, and neither the key
 * nor any text is logged. Replies can be streamed ([stream]) or read in one piece ([chat]).
 */
@Singleton
class AiClient internal constructor(
    private val http: OkHttpClient,
    private val io: CoroutineDispatcher,
    /** Tests talk to a local plain-HTTP server; the app only ever uses HTTPS. */
    private val allowCleartext: Boolean,
) : AiApi {
    @Inject constructor(@Dispatcher(PlanBDispatcher.IO) io: CoroutineDispatcher) : this(defaultHttpClient(), io, allowCleartext = false)

    private val json = Json { ignoreUnknownKeys = true }

    /** Sends [messages] and returns the reply text. */
    override suspend fun chat(endpoint: AiEndpoint, messages: List<AiMessage>, maxTokens: Int): AiResult<String> {
        val request = when (endpoint.wireFormat) {
            WireFormat.OPENAI_CHAT -> openAiRequest(endpoint, messages, maxTokens)
            WireFormat.ANTHROPIC_MESSAGES -> anthropicRequest(endpoint, messages, maxTokens)
        } ?: return AiResult.Failure(AiError.PROVIDER_ERROR)
        return execute(request) { body -> replyText(endpoint.wireFormat, body) }
    }

    override fun stream(endpoint: AiEndpoint, messages: List<AiMessage>, maxTokens: Int): Flow<AiStreamEvent> = callbackFlow {
        val request = when (endpoint.wireFormat) {
            WireFormat.OPENAI_CHAT -> openAiRequest(endpoint, messages, maxTokens, stream = true)
            WireFormat.ANTHROPIC_MESSAGES -> anthropicRequest(endpoint, messages, maxTokens, stream = true)
        }
        if (request == null) {
            trySend(AiStreamEvent.Failed(AiError.PROVIDER_ERROR))
            close()
            return@callbackFlow
        }
        val call = http.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                trySend(AiStreamEvent.Failed(AiError.NETWORK_UNREACHABLE))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { readStream(endpoint.wireFormat, it) { event -> trySendBlocking(event).isSuccess } }
                close()
            }
        })
        awaitClose { call.cancel() }
    }.buffer(Channel.UNLIMITED)

    /**
     * Reads a streamed response line by line and hands each event to [emit] (false = the
     * collector is gone). A provider that ignores `stream` and answers in one JSON piece is
     * read like [chat].
     */
    private fun readStream(format: WireFormat, response: Response, emit: (AiStreamEvent) -> Boolean) {
        if (!response.isSuccessful) {
            val body = runCatching { response.body.string() }.getOrDefault("")
            emit(AiStreamEvent.Failed(errorFor(response.code, body)))
            return
        }
        val contentType = response.header("Content-Type").orEmpty()
        if (!contentType.contains("event-stream", ignoreCase = true) && contentType.contains("json", ignoreCase = true)) {
            val text = try {
                replyText(format, response.body.string())
            } catch (e: IOException) {
                emit(AiStreamEvent.Failed(AiError.NETWORK_UNREACHABLE))
                return
            } catch (e: RuntimeException) {
                null
            }
            if (text == null) {
                emit(AiStreamEvent.Failed(AiError.PROVIDER_ERROR))
            } else if (emit(AiStreamEvent.Delta(text))) {
                emit(AiStreamEvent.Done)
            }
            return
        }
        val decoder = AiStreamDecoder(format)
        val parser = SseParser()
        try {
            val source = response.body.source()
            while (true) {
                val line = source.readUtf8Line() ?: break
                val event = parser.feed(line) ?: continue
                decoder.decode(event).forEach { if (!emit(it)) return }
                if (decoder.finished) return
            }
            parser.flush()?.let { event -> decoder.decode(event).forEach { if (!emit(it)) return } }
            if (!decoder.finished) emit(decoder.end())
        } catch (e: IOException) {
            emit(AiStreamEvent.Failed(AiError.NETWORK_UNREACHABLE))
        }
    }

    /** The reply text of a complete (non-streamed) response body. */
    private fun replyText(format: WireFormat, body: String): String {
        val root = json.parseToJsonElement(body).jsonObject
        return when (format) {
            WireFormat.OPENAI_CHAT -> root["choices"]!!.jsonArray.first().jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
            WireFormat.ANTHROPIC_MESSAGES -> root["content"]!!.jsonArray
                .map { it.jsonObject }
                .filter { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
                .joinToString("") { it["text"]!!.jsonPrimitive.content }
        }
    }

    override suspend fun testConnection(endpoint: AiEndpoint): AiResult<Unit> =
        when (val result = chat(endpoint, listOf(AiMessage(AiRole.USER, "ping")), maxTokens = 1)) {
            is AiResult.Success -> AiResult.Success(Unit)
            is AiResult.Failure -> result
        }

    override suspend fun listModels(endpoint: AiEndpoint): AiResult<List<String>> {
        val url = when (endpoint.wireFormat) {
            WireFormat.OPENAI_CHAT -> url(endpoint.baseUrl, "models")
            WireFormat.ANTHROPIC_MESSAGES -> url(endpoint.baseUrl, "v1/models")
        } ?: return AiResult.Failure(AiError.PROVIDER_ERROR)
        val request = authorized(Request.Builder().url(url).get(), endpoint).build()
        return execute(request) { body ->
            json.parseToJsonElement(body).jsonObject["data"]!!.jsonArray.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
        }
    }

    private fun openAiRequest(endpoint: AiEndpoint, messages: List<AiMessage>, maxTokens: Int, stream: Boolean = false): Request? {
        val url = url(endpoint.baseUrl, "chat/completions") ?: return null
        val body = buildJsonObject {
            put("model", endpoint.model)
            put("max_tokens", maxTokens)
            if (stream) put("stream", true)
            put("messages", buildJsonArray {
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role.name.lowercase())
                        put("content", m.text)
                    })
                }
            })
        }
        return authorized(Request.Builder().url(url).post(body.toString().toRequestBody(JSON)), endpoint).build()
    }

    private fun anthropicRequest(endpoint: AiEndpoint, messages: List<AiMessage>, maxTokens: Int, stream: Boolean = false): Request? {
        val url = url(endpoint.baseUrl, "v1/messages") ?: return null
        val system = messages.filter { it.role == AiRole.SYSTEM }.joinToString("\n\n") { it.text }
        val body = buildJsonObject {
            put("model", endpoint.model)
            put("max_tokens", maxTokens)
            if (stream) put("stream", true)
            if (system.isNotEmpty()) put("system", system)
            put("messages", JsonArray(messages.filter { it.role != AiRole.SYSTEM }.map { m ->
                buildJsonObject {
                    put("role", if (m.role == AiRole.ASSISTANT) "assistant" else "user")
                    put("content", m.text)
                }
            }))
        }
        return authorized(Request.Builder().url(url).post(body.toString().toRequestBody(JSON)), endpoint).build()
    }

    private fun authorized(builder: Request.Builder, endpoint: AiEndpoint): Request.Builder = when (endpoint.wireFormat) {
        WireFormat.OPENAI_CHAT -> builder.header("Authorization", "Bearer ${endpoint.apiKey}")
        WireFormat.ANTHROPIC_MESSAGES -> builder.header("x-api-key", endpoint.apiKey).header("anthropic-version", ANTHROPIC_VERSION)
    }

    /** `{base}/{path}`; only HTTPS (and nothing that is not a valid URL) is accepted. */
    internal fun url(baseUrl: String, path: String): HttpUrl? {
        val base = baseUrl.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
        if (!base.isHttps && !allowCleartext) return null
        return (base.toString().trimEnd('/') + "/" + path).toHttpUrlOrNull()
    }

    private suspend fun <T> execute(request: Request, parse: (String) -> T): AiResult<T> = withContext(io) {
        val response = try {
            http.newCall(request).await()
        } catch (e: IOException) {
            return@withContext AiResult.Failure(AiError.NETWORK_UNREACHABLE)
        }
        response.use {
            val body = try {
                it.body.string()
            } catch (e: IOException) {
                return@withContext AiResult.Failure(AiError.NETWORK_UNREACHABLE)
            }
            if (!it.isSuccessful) return@withContext AiResult.Failure(errorFor(it.code, body))
            try {
                AiResult.Success(parse(body))
            } catch (e: SerializationException) {
                AiResult.Failure(AiError.PROVIDER_ERROR)
            } catch (e: IllegalArgumentException) {
                AiResult.Failure(AiError.PROVIDER_ERROR)
            } catch (e: NullPointerException) {
                AiResult.Failure(AiError.PROVIDER_ERROR)
            } catch (e: NoSuchElementException) {
                AiResult.Failure(AiError.PROVIDER_ERROR)
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_TOKENS = 1024
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Some providers report a rejected key as 400 with a message instead of 401. */
        internal fun errorFor(code: Int, body: String): AiError = when {
            code == 401 || code == 403 -> AiError.INVALID_KEY
            code == 400 && body.contains("api key", ignoreCase = true) -> AiError.INVALID_KEY
            code == 400 && body.contains("api_key", ignoreCase = true) -> AiError.INVALID_KEY
            code == 429 -> AiError.RATE_LIMITED
            else -> AiError.PROVIDER_ERROR
        }

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // A streamed reply may pause between pieces; the whole call still has a limit.
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
    }
}

/** Suspends until the call completes; cancelling the coroutine cancels the call. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = continuation.resumeWith(Result.success(response))
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(e))
        }
    })
}
