package com.behnamjalali.planb.core.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** One server-sent event: its `event:` name (null when absent) and its joined `data:` lines. */
data class SseEvent(val name: String?, val data: String)

/**
 * Incremental parser for `text/event-stream` (the subset providers use): `event:` and `data:`
 * fields, `:` comments, a blank line ends an event, several `data:` lines are joined with a
 * newline. Feed it one line at a time (without the line break).
 */
class SseParser {
    private var name: String? = null
    private val data = StringBuilder()
    private var hasData = false

    /** Returns the event this line completes, if any. */
    fun feed(line: String): SseEvent? {
        if (line.isEmpty()) return flush()
        if (line.startsWith(":")) return null
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "event" -> name = value
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
            // "id" and "retry" are not used by any provider for chat replies.
        }
        return null
    }

    /** Ends the current event (end of stream without a final blank line). */
    fun flush(): SseEvent? {
        val event = if (hasData) SseEvent(name, data.toString()) else null
        name = null
        data.setLength(0)
        hasData = false
        return event
    }
}

/**
 * Turns server-sent events of either wire format into [AiStreamEvent]s.
 *
 * - OpenAI-compatible: `data: {"choices":[{"delta":{"content":"…"}}]}` … `data: [DONE]`.
 * - Anthropic Messages: `content_block_delta` events with `text_delta`, ending with
 *   `message_stop`; `error` events carry a type.
 *
 * Unknown events and malformed JSON lines are skipped, so a gateway's extra fields or keep-alive
 * events never break a reply.
 */
class AiStreamDecoder(private val format: WireFormat) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    var finished: Boolean = false
        private set
    private var receivedText = false

    fun decode(event: SseEvent): List<AiStreamEvent> {
        if (finished) return emptyList()
        val data = event.data.trim()
        if (format == WireFormat.OPENAI_CHAT && data == "[DONE]") return listOf(finish())
        val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return emptyList()
        return when (format) {
            WireFormat.OPENAI_CHAT -> openAi(root)
            WireFormat.ANTHROPIC_MESSAGES -> anthropic(event.name, root)
        }
    }

    /** The stream ended without its end marker: complete if some text arrived. */
    fun end(): AiStreamEvent {
        finished = true
        return if (receivedText) AiStreamEvent.Done else AiStreamEvent.Failed(AiError.PROVIDER_ERROR)
    }

    private fun finish(): AiStreamEvent {
        finished = true
        return AiStreamEvent.Done
    }

    private fun fail(error: AiError): AiStreamEvent {
        finished = true
        return AiStreamEvent.Failed(error)
    }

    private fun delta(text: String?): List<AiStreamEvent> {
        if (text.isNullOrEmpty()) return emptyList()
        receivedText = true
        return listOf(AiStreamEvent.Delta(text))
    }

    private fun openAi(root: JsonObject): List<AiStreamEvent> {
        (root["error"] as? JsonObject)?.let { error ->
            return listOf(fail(errorOf(error.string("type") ?: error.string("code"), error.string("message"))))
        }
        val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return emptyList()
        val delta = (choice["delta"] as? JsonObject) ?: (choice["message"] as? JsonObject)
        val out = delta(delta?.string("content")).toMutableList()
        // Some gateways never send [DONE] but do say why the reply ended.
        if (choice.string("finish_reason") != null && out.isEmpty() && receivedText) out += finish()
        return out
    }

    private fun anthropic(name: String?, root: JsonObject): List<AiStreamEvent> {
        val type = name ?: root.string("type")
        return when (type) {
            "content_block_delta" -> {
                val delta = root["delta"] as? JsonObject
                if (delta?.string("type") == "text_delta") delta(delta.string("text")) else emptyList()
            }
            "message_stop" -> listOf(finish())
            "error" -> {
                val error = root["error"] as? JsonObject
                listOf(fail(errorOf(error?.string("type"), error?.string("message"))))
            }
            else -> emptyList()
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private companion object {
        /** Maps an error's type or code to the neutral categories (never its message text). */
        fun errorOf(type: String?, message: String?): AiError {
            val t = type.orEmpty().lowercase()
            val m = message.orEmpty().lowercase()
            return when {
                "auth" in t || "permission" in t || "invalid_api_key" in t || "api key" in m || "api_key" in m -> AiError.INVALID_KEY
                "rate" in t || "overloaded" in t || "quota" in t -> AiError.RATE_LIMITED
                else -> AiError.PROVIDER_ERROR
            }
        }
    }
}
