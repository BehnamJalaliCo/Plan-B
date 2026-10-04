package com.behnamjalali.planb.core.ai

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

class AiClientTest {
    private val server = MockWebServer()
    private val http = OkHttpClient.Builder().retryOnConnectionFailure(false).readTimeout(2, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build()
    private val client = AiClient(http, Dispatchers.IO, allowCleartext = true)

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun endpoint(format: WireFormat, base: String = server.url("/v1").toString()) =
        AiEndpoint(format, base, "test-model", "secret-key-123")

    private fun json(body: String, code: Int = 200) = MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build()

    private val messages = listOf(AiMessage(AiRole.SYSTEM, "Be brief."), AiMessage(AiRole.USER, "سلام! plan my day"))

    @Test
    fun openAiChat_sendsBearerKeyAndMessages_andReadsTheReply() = runBlocking {
        server.enqueue(json("""{"choices":[{"message":{"role":"assistant","content":"روز خوبی داشته باشی"}}]}"""))
        val result = client.chat(endpoint(WireFormat.OPENAI_CHAT, server.url("/v1/").toString()), messages, maxTokens = 50)
        assertThat(result).isEqualTo(AiResult.Success("روز خوبی داشته باشی"))
        val request = server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/v1/chat/completions")
        assertThat(request.headers["Authorization"]).isEqualTo("Bearer secret-key-123")
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo("test-model")
        assertThat(body["max_tokens"]!!.jsonPrimitive.content).isEqualTo("50")
        val sent = body["messages"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content to it.jsonObject["content"]!!.jsonPrimitive.content }
        assertThat(sent).containsExactly("system" to "Be brief.", "user" to "سلام! plan my day").inOrder()
    }

    @Test
    fun anthropicMessages_usesItsHeadersAndSystemField() = runBlocking {
        server.enqueue(json("""{"content":[{"type":"text","text":"Hello "},{"type":"text","text":"there"}],"stop_reason":"end_turn"}"""))
        val result = client.chat(endpoint(WireFormat.ANTHROPIC_MESSAGES, server.url("/").toString()), messages)
        assertThat(result).isEqualTo(AiResult.Success("Hello there"))
        val request = server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/v1/messages")
        assertThat(request.headers["x-api-key"]).isEqualTo("secret-key-123")
        assertThat(request.headers["anthropic-version"]).isEqualTo("2023-06-01")
        assertThat(request.headers["Authorization"]).isNull()
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertThat(body["system"]!!.jsonPrimitive.content).isEqualTo("Be brief.")
        assertThat(body["messages"]!!.jsonArray).hasSize(1)
    }

    @Test
    fun httpErrors_mapToNeutralCategories() = runBlocking {
        val cases = listOf(
            json("""{"error":{"message":"Incorrect API key"}}""", 401) to AiError.INVALID_KEY,
            json("{}", 403) to AiError.INVALID_KEY,
            json("""{"error":{"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}""", 400) to AiError.INVALID_KEY,
            json("""{"error":"slow down"}""", 429) to AiError.RATE_LIMITED,
            json("""{"error":"overloaded"}""", 529) to AiError.PROVIDER_ERROR,
            json("""{"error":"model not found"}""", 404) to AiError.PROVIDER_ERROR,
            json("not json at all") to AiError.PROVIDER_ERROR,
            json("""{"choices":[]}""") to AiError.PROVIDER_ERROR,
        )
        for ((response, expected) in cases) {
            server.enqueue(response)
            assertThat(client.chat(endpoint(WireFormat.OPENAI_CHAT), messages)).isEqualTo(AiResult.Failure(expected))
        }
        server.enqueue(json("{}", 401))
        assertThat(client.chat(endpoint(WireFormat.ANTHROPIC_MESSAGES), messages)).isEqualTo(AiResult.Failure(AiError.INVALID_KEY))
    }

    @Test
    fun unreachableServer_isANetworkError() = runBlocking {
        // No answer within the timeout.
        server.enqueue(MockResponse.Builder().headersDelay(4, TimeUnit.SECONDS).body("{}").build())
        assertThat(client.chat(endpoint(WireFormat.OPENAI_CHAT), messages)).isEqualTo(AiResult.Failure(AiError.NETWORK_UNREACHABLE))
        val closed = server.url("/v1").toString()
        server.close()
        assertThat(client.testConnection(endpoint(WireFormat.OPENAI_CHAT, closed))).isEqualTo(AiResult.Failure(AiError.NETWORK_UNREACHABLE))
    }

    @Test
    fun testConnection_andListModels() = runBlocking {
        server.enqueue(json("""{"choices":[{"message":{"content":"p"}}]}"""))
        assertThat(client.testConnection(endpoint(WireFormat.OPENAI_CHAT))).isEqualTo(AiResult.Success(Unit))
        assertThat(Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject["max_tokens"]!!.jsonPrimitive.content).isEqualTo("1")
        server.enqueue(json("""{"data":[{"id":"model-a"},{"id":"model-b"}]}"""))
        assertThat(client.listModels(endpoint(WireFormat.ANTHROPIC_MESSAGES, server.url("/").toString())))
            .isEqualTo(AiResult.Success(listOf("model-a", "model-b")))
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/v1/models")
    }

    @Test
    fun onlyHttpsBaseUrls_areUsedByTheApp() = runBlocking {
        val app = AiClient(http, Dispatchers.IO, allowCleartext = false)
        assertThat(app.url("http://example.com/v1", "chat/completions")).isNull()
        assertThat(app.url("not a url", "chat/completions")).isNull()
        assertThat(app.url("https://api.example.com/v1/", "chat/completions").toString()).isEqualTo("https://api.example.com/v1/chat/completions")
        assertThat(app.chat(AiEndpoint(WireFormat.OPENAI_CHAT, "http://example.com", "m", "k"), messages))
            .isEqualTo(AiResult.Failure(AiError.PROVIDER_ERROR))
    }

    @Test
    fun endpoint_neverPrintsTheKey() {
        assertThat(endpoint(WireFormat.OPENAI_CHAT).toString()).doesNotContain("secret-key-123")
    }

    @Test
    fun catalog_isConsistent() {
        assertThat(AiProviders.all.map { it.id }.toSet()).hasSize(AiProviders.all.size)
        assertThat(AiProviders.all.first()).isEqualTo(AiProviders.IRAN_GATEWAY)
        AiProviders.all.forEach { p ->
            assertThat(p.brandName != null || p.labelRes != null).isTrue()
            p.defaultBaseUrl?.let { assertThat(it).startsWith("https://") }
        }
        assertThat(AiProviders.ANTHROPIC.wireFormat).isEqualTo(WireFormat.ANTHROPIC_MESSAGES)
        assertThat(AiProviders.CUSTOM.requiresBaseUrl).isTrue()
    }
}

/** AES-GCM with an in-memory key, standing in for the Android Keystore on the JVM. */
private class TestCipher : SecretCipher {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    override fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        return c.iv + c.doFinal(plain)
    }
    override fun decrypt(sealed: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12)) }
        return c.doFinal(sealed, 12, sealed.size - 12)
    }
}

class AiSettingsRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir: File = Files.createTempDirectory("ai").toFile()
    private val file = File(dir, "ai.preferences_pb")
    private val time = FakeTimeProvider()
    private val repository = AiSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file }, TestCipher(), time)

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun offByDefault_andNoEndpointWithoutConsent() = runBlocking {
        assertThat(repository.current().enabled).isFalse()
        repository.setProvider(AiProviders.DEEPSEEK)
        repository.setApiKey("sk-very-secret")
        assertThat(repository.endpoint()).isNull()
        repository.enableWithConsent()
        val settings = repository.current()
        assertThat(settings.enabled).isTrue()
        assertThat(settings.consentAt).isEqualTo(time.now())
        val endpoint = repository.endpoint()!!
        assertThat(endpoint.apiKey).isEqualTo("sk-very-secret")
        assertThat(endpoint.baseUrl).isEqualTo("https://api.deepseek.com")
        assertThat(endpoint.model).isEqualTo("deepseek-chat")
        repository.disable()
        assertThat(repository.endpoint()).isNull()
    }

    @Test
    fun keyIsStoredEncrypted() = runBlocking {
        repository.setProvider(AiProviders.CUSTOM, baseUrl = "https://gateway.example/v1", model = "m")
        repository.setApiKey("  sk-very-secret  ")
        assertThat(repository.current().hasKey).isTrue()
        assertThat(file.readBytes().toString(Charsets.ISO_8859_1)).doesNotContain("sk-very-secret")
        repository.setApiKey("")
        assertThat(repository.current().hasKey).isFalse()
    }

    @Test
    fun customProvider_needsABaseUrl() = runBlocking {
        repository.setProvider(AiProviders.IRAN_GATEWAY, model = "m")
        repository.setApiKey("k")
        repository.enableWithConsent()
        assertThat(repository.endpoint()).isNull()
        repository.setBaseUrl("https://gateway.example/v1")
        assertThat(repository.endpoint()!!.baseUrl).isEqualTo("https://gateway.example/v1")
    }
}
