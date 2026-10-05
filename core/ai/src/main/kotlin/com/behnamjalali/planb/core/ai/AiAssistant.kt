package com.behnamjalali.planb.core.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The single way features talk to the user's provider: resolves the endpoint (only while the
 * assistant is on, with consent and a key) and streams replies. Neither the key nor any text
 * is logged or stored here.
 */
@Singleton
class AiAssistant @Inject constructor(
    private val settings: AiSettingsRepository,
    private val api: AiApi,
) {
    /** Whether requests can be made now. */
    val ready: Flow<Boolean> = settings.settings.map { it.isReady }

    /**
     * Streams a reply. Emits [AiStreamEvent.Failed] with [AiError.NOT_CONFIGURED] right away
     * when the assistant is off or incomplete.
     */
    fun stream(messages: List<AiMessage>, maxTokens: Int = AiClient.DEFAULT_MAX_TOKENS): Flow<AiStreamEvent> = flow {
        val endpoint = settings.endpoint()
        if (endpoint == null) {
            emit(AiStreamEvent.Failed(AiError.NOT_CONFIGURED))
        } else {
            emitAll(api.stream(endpoint, messages, maxTokens))
        }
    }

    /** The whole reply in one piece (structured answers such as a plan or a list). */
    suspend fun complete(messages: List<AiMessage>, maxTokens: Int = AiClient.DEFAULT_MAX_TOKENS): AiResult<String> {
        val text = StringBuilder()
        var error: AiError? = null
        stream(messages, maxTokens).collect { event ->
            when (event) {
                is AiStreamEvent.Delta -> text.append(event.text)
                is AiStreamEvent.Failed -> error = event.error
                AiStreamEvent.Done -> Unit
            }
        }
        // A structured answer cut off half-way is not usable: any failure fails the whole reply.
        return error?.let { AiResult.Failure(it) } ?: AiResult.Success(text.toString())
    }
}
