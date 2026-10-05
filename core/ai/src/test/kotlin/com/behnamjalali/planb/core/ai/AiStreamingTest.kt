package com.behnamjalali.planb.core.ai

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

class SseParserTest {
    @Test
    fun events_endAtBlankLines_andJoinDataLines() {
        val parser = SseParser()
        val lines = listOf(
            ": keep-alive comment",
            "event: content_block_delta",
            "data: {\"a\":1}",
            "",
            "data:first",
            "data: second",
            "id: 7",
            "",
            "",
            "data: [DONE]",
        )
        val events = lines.mapNotNull(parser::feed) + listOfNotNull(parser.flush())
        assertThat(events).containsExactly(
            SseEvent("content_block_delta", "{\"a\":1}"),
            SseEvent(null, "first\nsecond"),
            SseEvent(null, "[DONE]"),
        ).inOrder()
    }
}

class AiStreamDecoderTest {
    private fun openAi(vararg data: String): List<AiStreamEvent> {
        val decoder = AiStreamDecoder(WireFormat.OPENAI_CHAT)
        return data.flatMap { decoder.decode(SseEvent(null, it)) }
    }

    @Test
    fun openAiDeltas_untilDone() {
        val events = openAi(
            """{"choices":[{"delta":{"role":"assistant"}}]}""",
            """{"choices":[{"delta":{"content":"سلام"}}]}""",
            "not json — skipped",
            """{"choices":[{"delta":{"content":" دنیا"}}]}""",
            "[DONE]",
            """{"choices":[{"delta":{"content":"after done"}}]}""",
        )
        assertThat(events).containsExactly(AiStreamEvent.Delta("سلام"), AiStreamEvent.Delta(" دنیا"), AiStreamEvent.Done).inOrder()
    }

    @Test
    fun openAiErrorsInTheStream_mapToNeutralCategories() {
        assertThat(openAi("""{"error":{"message":"Incorrect API key provided","type":"invalid_request_error"}}"""))
            .containsExactly(AiStreamEvent.Failed(AiError.INVALID_KEY))
        assertThat(openAi("""{"error":{"message":"slow down","type":"rate_limit_exceeded"}}"""))
            .containsExactly(AiStreamEvent.Failed(AiError.RATE_LIMITED))
        assertThat(openAi("""{"error":{"message":"boom"}}""")).containsExactly(AiStreamEvent.Failed(AiError.PROVIDER_ERROR))
    }

    @Test
    fun anthropicTextDeltas_untilMessageStop() {
        val decoder = AiStreamDecoder(WireFormat.ANTHROPIC_MESSAGES)
        val events = listOf(
            SseEvent("message_start", """{"type":"message_start","message":{"id":"m"}}"""),
            SseEvent("content_block_start", """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}"""),
            SseEvent("ping", """{"type":"ping"}"""),
            SseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}"""),
            SseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{"}}"""),
            SseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" there"}}"""),
            SseEvent("message_delta", """{"type":"message_delta","delta":{"stop_reason":"end_turn"}}"""),
            SseEvent("message_stop", """{"type":"message_stop"}"""),
        ).flatMap(decoder::decode)
        assertThat(events).containsExactly(AiStreamEvent.Delta("Hello"), AiStreamEvent.Delta(" there"), AiStreamEvent.Done).inOrder()
    }

    @Test
    fun anthropicErrorEvents() {
        val overloaded = AiStreamDecoder(WireFormat.ANTHROPIC_MESSAGES)
            .decode(SseEvent("error", """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""))
        assertThat(overloaded).containsExactly(AiStreamEvent.Failed(AiError.RATE_LIMITED))
        val auth = AiStreamDecoder(WireFormat.ANTHROPIC_MESSAGES)
            .decode(SseEvent("error", """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""))
        assertThat(auth).containsExactly(AiStreamEvent.Failed(AiError.INVALID_KEY))
    }

    @Test
    fun streamWithoutEndMarker_endsWithDoneOnlyAfterText() {
        val decoder = AiStreamDecoder(WireFormat.OPENAI_CHAT)
        decoder.decode(SseEvent(null, """{"choices":[{"delta":{"content":"x"}}]}"""))
        assertThat(decoder.end()).isEqualTo(AiStreamEvent.Done)
        assertThat(AiStreamDecoder(WireFormat.OPENAI_CHAT).end()).isEqualTo(AiStreamEvent.Failed(AiError.PROVIDER_ERROR))
    }
}

class AiClientStreamTest {
    private val server = MockWebServer()
    private val http = OkHttpClient.Builder().retryOnConnectionFailure(false).readTimeout(2, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build()
    private val client = AiClient(http, Dispatchers.IO, allowCleartext = true)
    private val messages = listOf(AiMessage(AiRole.USER, "plan my day"))

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun endpoint(format: WireFormat) = AiEndpoint(format, server.url(if (format == WireFormat.OPENAI_CHAT) "/v1" else "/").toString(), "m", "k")

    private fun sse(body: String) = MockResponse.Builder().body(body).addHeader("Content-Type", "text/event-stream").build()

    @Test
    fun openAiStream_deliversPiecesInOrder_andAsksForAStream() = runBlocking<Unit> {
        server.enqueue(
            sse(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Plan\"}}]}\n\n" +
                    "data: {\"choices\":[{\"delta\":{\"content\":\" ready\"}}]}\n\n" +
                    "data: [DONE]\n\n",
            ),
        )
        val events = withTimeout(10_000) { client.stream(endpoint(WireFormat.OPENAI_CHAT), messages).toList() }
        assertThat(events).containsExactly(AiStreamEvent.Delta("Plan"), AiStreamEvent.Delta(" ready"), AiStreamEvent.Done).inOrder()
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertThat(body["stream"]!!.jsonPrimitive.content).isEqualTo("true")
    }

    @Test
    fun anthropicStream() = runBlocking<Unit> {
        server.enqueue(
            sse(
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}\n\n" +
                    "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n",
            ),
        )
        val events = withTimeout(10_000) { client.stream(endpoint(WireFormat.ANTHROPIC_MESSAGES), messages).toList() }
        assertThat(events).containsExactly(AiStreamEvent.Delta("Hi"), AiStreamEvent.Done).inOrder()
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/v1/messages")
    }

    @Test
    fun aGatewayThatIgnoresStream_isReadInOnePiece() = runBlocking<Unit> {
        server.enqueue(MockResponse.Builder().body("""{"choices":[{"message":{"content":"whole"}}]}""").addHeader("Content-Type", "application/json").build())
        val events = withTimeout(10_000) { client.stream(endpoint(WireFormat.OPENAI_CHAT), messages).toList() }
        assertThat(events).containsExactly(AiStreamEvent.Delta("whole"), AiStreamEvent.Done).inOrder()
    }

    @Test
    fun httpErrors_andUnreachableServers_areNeutral() = runBlocking<Unit> {
        server.enqueue(MockResponse.Builder().code(401).body("{}").build())
        assertThat(withTimeout(10_000) { client.stream(endpoint(WireFormat.OPENAI_CHAT), messages).toList() })
            .containsExactly(AiStreamEvent.Failed(AiError.INVALID_KEY))
        server.enqueue(MockResponse.Builder().code(429).body("{}").build())
        assertThat(withTimeout(10_000) { client.stream(endpoint(WireFormat.OPENAI_CHAT), messages).toList() })
            .containsExactly(AiStreamEvent.Failed(AiError.RATE_LIMITED))
        val closed = endpoint(WireFormat.OPENAI_CHAT)
        server.close()
        assertThat(withTimeout(10_000) { client.stream(closed, messages).toList() })
            .containsExactly(AiStreamEvent.Failed(AiError.NETWORK_UNREACHABLE))
    }

    @Test
    fun cancellingTheCollector_endsTheRequest() = runBlocking<Unit> {
        // The first piece arrives, the rest would take long; taking one item cancels the call.
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "text/event-stream")
                .body("data: {\"choices\":[{\"delta\":{\"content\":\"first\"}}]}\n\n" + "data: {\"choices\":[{\"delta\":{\"content\":\"x\"}}]}\n\n".repeat(50))
                .throttleBody(64, 1, TimeUnit.SECONDS)
                .build(),
        )
        val first = withTimeout(10_000) { client.stream(endpoint(WireFormat.OPENAI_CHAT), messages).first() }
        assertThat(first).isEqualTo(AiStreamEvent.Delta("first"))
    }
}
