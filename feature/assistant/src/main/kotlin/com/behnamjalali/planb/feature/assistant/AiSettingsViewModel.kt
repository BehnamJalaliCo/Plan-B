package com.behnamjalali.planb.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.ai.AiApi
import com.behnamjalali.planb.core.ai.AiError
import com.behnamjalali.planb.core.ai.AiProvider
import com.behnamjalali.planb.core.ai.AiProviders
import com.behnamjalali.planb.core.ai.AiResult
import com.behnamjalali.planb.core.ai.AiSettings
import com.behnamjalali.planb.core.ai.AiSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ConnectionCheck {
    data object Running : ConnectionCheck
    data object Ok : ConnectionCheck
    data class Failed(val error: AiError) : ConnectionCheck
}

data class AiSettingsUiState(
    val loaded: Boolean = false,
    val settings: AiSettings = AiSettings(),
    /** Editable copies of the stored values (written through as the user types). */
    val baseUrl: String = "",
    val model: String = "",
    /** A new key being typed; never pre-filled with the stored one. */
    val keyInput: String = "",
    val check: ConnectionCheck? = null,
    val models: List<String> = emptyList(),
    val loadingModels: Boolean = false,
    val modelsError: AiError? = null,
    val consentDialog: Boolean = false,
) {
    val provider: AiProvider? get() = settings.provider

    /** The host the text would be sent to (for the consent text). */
    val host: String get() = runCatching { URI(settings.effectiveBaseUrl).host }.getOrNull().orEmpty()
}

/**
 * Settings of the AI assistant (Plan-B Pro #39): provider (Iranian gateways first), address,
 * key (stored encrypted, never shown again), model (suggestions or the provider's list), a
 * connection test, and turning the assistant on only after explicit consent.
 */
@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val repository: AiSettingsRepository,
    private val api: AiApi,
) : ViewModel() {
    private val _state = MutableStateFlow(AiSettingsUiState())
    val state: StateFlow<AiSettingsUiState> = _state.asStateFlow()

    val providers: List<AiProvider> = AiProviders.all

    private var checkJob: Job? = null

    init {
        viewModelScope.launch {
            repository.settings.collect { s ->
                _state.update { current ->
                    if (!current.loaded) {
                        current.copy(loaded = true, settings = s, baseUrl = s.baseUrl, model = s.model)
                    } else {
                        current.copy(settings = s)
                    }
                }
            }
        }
    }

    fun selectProvider(provider: AiProvider) {
        if (provider.id == _state.value.settings.providerId) return
        val model = provider.suggestedModels.firstOrNull().orEmpty()
        _state.update { it.copy(baseUrl = "", model = model, check = null, models = emptyList(), modelsError = null) }
        viewModelScope.launch { repository.setProvider(provider, baseUrl = "", model = model) }
    }

    fun setBaseUrl(value: String) {
        _state.update { it.copy(baseUrl = value, check = null) }
        viewModelScope.launch { repository.setBaseUrl(value) }
    }

    /** A model picked automatically from the provider's list keeps the connection result. */
    private fun chooseModel(value: String) {
        _state.update { it.copy(model = value) }
        viewModelScope.launch { repository.setModel(value) }
    }

    fun setModel(value: String) {
        _state.update { it.copy(model = value, check = null) }
        viewModelScope.launch { repository.setModel(value) }
    }

    fun setKeyInput(value: String) = _state.update { it.copy(keyInput = value) }

    /** Stores the typed key (encrypted) and checks it right away. */
    fun saveKey() {
        val key = _state.value.keyInput.trim()
        if (key.isEmpty()) return
        _state.update { it.copy(keyInput = "") }
        viewModelScope.launch {
            repository.setApiKey(key)
            test()
        }
    }

    fun removeKey() {
        viewModelScope.launch {
            repository.clearApiKey()
            repository.disable()
        }
        _state.update { it.copy(check = null) }
    }

    /** A tiny request with the stored key; loads the model list when the provider has no suggestions. */
    fun test() {
        checkJob?.cancel()
        _state.update { it.copy(check = ConnectionCheck.Running) }
        checkJob = viewModelScope.launch {
            val endpoint = repository.endpoint(requireEnabled = false)
            if (endpoint == null) {
                // No model chosen yet (providers without suggestions): listing the models checks the key.
                val partial = repository.endpoint(requireEnabled = false, requireModel = false)
                if (partial == null) {
                    _state.update { it.copy(check = ConnectionCheck.Failed(AiError.NOT_CONFIGURED)) }
                    return@launch
                }
                when (val listed = api.listModels(partial)) {
                    is AiResult.Failure -> _state.update { it.copy(check = ConnectionCheck.Failed(listed.error)) }
                    is AiResult.Success -> {
                        val models = listed.value.distinct().sorted().take(MAX_MODELS)
                        _state.update { it.copy(check = ConnectionCheck.Ok, models = models) }
                        models.firstOrNull()?.let(::chooseModel)
                    }
                }
                return@launch
            }
            val result = api.testConnection(endpoint)
            _state.update { it.copy(check = if (result is AiResult.Failure) ConnectionCheck.Failed(result.error) else ConnectionCheck.Ok) }
            if (result is AiResult.Success && _state.value.provider?.suggestedModels.isNullOrEmpty() && _state.value.models.isEmpty()) loadModels()
        }
    }

    fun loadModels() {
        if (_state.value.loadingModels) return
        _state.update { it.copy(loadingModels = true, modelsError = null) }
        viewModelScope.launch {
            // Listing models needs no model yet.
            val endpoint = repository.endpoint(requireEnabled = false, requireModel = false)
            if (endpoint == null) {
                _state.update { it.copy(loadingModels = false, modelsError = AiError.NOT_CONFIGURED) }
                return@launch
            }
            when (val result = api.listModels(endpoint)) {
                is AiResult.Success -> {
                    val models = result.value.distinct().sorted().take(MAX_MODELS)
                    _state.update { it.copy(loadingModels = false, models = models) }
                    if (_state.value.model.isBlank()) models.firstOrNull()?.let(::chooseModel)
                }
                is AiResult.Failure -> _state.update { it.copy(loadingModels = false, modelsError = result.error) }
            }
        }
    }

    /** "Use the assistant" asks for consent first; turning it off needs none. */
    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            if (_state.value.settings.isComplete) _state.update { it.copy(consentDialog = true) }
        } else {
            viewModelScope.launch { repository.disable() }
        }
    }

    fun confirmConsent() {
        _state.update { it.copy(consentDialog = false) }
        viewModelScope.launch { repository.enableWithConsent() }
    }

    fun dismissConsent() = _state.update { it.copy(consentDialog = false) }

    /** Removes provider, address, model, key and consent. */
    fun forget() {
        checkJob?.cancel()
        viewModelScope.launch { repository.clearAll() }
        _state.update { AiSettingsUiState(loaded = true) }
    }

    private companion object {
        const val MAX_MODELS = 60
    }
}
