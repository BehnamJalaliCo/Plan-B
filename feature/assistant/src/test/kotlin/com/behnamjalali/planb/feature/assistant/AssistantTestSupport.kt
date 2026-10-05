package com.behnamjalali.planb.feature.assistant

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.behnamjalali.planb.core.ai.AiApi
import com.behnamjalali.planb.core.ai.AiAssistant
import com.behnamjalali.planb.core.ai.AiEndpoint
import com.behnamjalali.planb.core.ai.AiError
import com.behnamjalali.planb.core.ai.AiMessage
import com.behnamjalali.planb.core.ai.AiProviders
import com.behnamjalali.planb.core.ai.AiResult
import com.behnamjalali.planb.core.ai.AiSettingsRepository
import com.behnamjalali.planb.core.ai.AiStreamEvent
import com.behnamjalali.planb.core.ai.SecretCipher
import com.behnamjalali.planb.core.common.TimeProvider
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** A provider that answers with [reply] (or fails with [error]) and records what it was sent. */
class ScriptedApi(var reply: (List<AiMessage>) -> String = { "ok" }, var error: AiError? = null) : AiApi {
    val requests = mutableListOf<List<AiMessage>>()
    var tested = 0

    override suspend fun chat(endpoint: AiEndpoint, messages: List<AiMessage>, maxTokens: Int): AiResult<String> =
        error?.let { AiResult.Failure(it) } ?: AiResult.Success(reply(messages))

    override fun stream(endpoint: AiEndpoint, messages: List<AiMessage>, maxTokens: Int): Flow<AiStreamEvent> = flow {
        requests += messages
        val failure = error
        if (failure != null) {
            emit(AiStreamEvent.Failed(failure))
        } else {
            reply(messages).chunked(5).forEach { emit(AiStreamEvent.Delta(it)) }
            emit(AiStreamEvent.Done)
        }
    }

    override suspend fun testConnection(endpoint: AiEndpoint): AiResult<Unit> {
        tested++
        return error?.let { AiResult.Failure(it) } ?: AiResult.Success(Unit)
    }

    override suspend fun listModels(endpoint: AiEndpoint): AiResult<List<String>> =
        error?.let { AiResult.Failure(it) } ?: AiResult.Success(listOf("model-b", "model-a", "model-a"))
}

/** Settings on a temporary file with a trivial (test-only) cipher. */
class TestAiSettings(time: TimeProvider) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir: File = Files.createTempDirectory("ai").toFile()
    val repository = AiSettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope) { File(dir, "ai.preferences_pb") },
        object : SecretCipher {
            override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
            override fun decrypt(sealed: ByteArray) = encrypt(sealed)
        },
        time,
    )

    suspend fun configure() {
        repository.setProvider(AiProviders.AVALAI)
        repository.setApiKey("sk-test")
        repository.enableWithConsent()
    }

    fun assistant(api: AiApi) = AiAssistant(repository, api)

    fun close() {
        scope.cancel()
        dir.deleteRecursively()
    }
}
