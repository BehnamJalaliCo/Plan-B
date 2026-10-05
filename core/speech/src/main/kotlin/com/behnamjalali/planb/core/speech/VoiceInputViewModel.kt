package com.behnamjalali.planb.core.speech

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VoicePhase { IDLE, LISTENING, ERROR }

data class VoiceInputState(
    val phase: VoicePhase = VoicePhase.IDLE,
    /** What was heard so far while listening. */
    val partial: String = "",
    /** 0…1 loudness for the listening animation. */
    val level: Float = 0f,
    /** The service is ready and hearing (before that a short "starting" moment). */
    val ready: Boolean = false,
    val error: DictationError? = null,
)

/**
 * One voice-input field: listens, shows partial text and hands the final text over once
 * ([results], a one-shot channel). Nothing is stored; the audio never reaches Plan-B.
 */
@HiltViewModel
class VoiceInputViewModel @Inject constructor(private val dictation: VoiceDictation) : ViewModel() {
    private val _state = MutableStateFlow(VoiceInputState())
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private val _results = Channel<String>(Channel.BUFFERED)

    /** Final texts, each delivered once. */
    val results: Flow<String> = _results.receiveAsFlow()

    private var job: Job? = null

    fun isAvailable(): Boolean = dictation.isAvailable()

    fun start(languageTag: String) {
        if (job?.isActive == true) return
        _state.value = VoiceInputState(phase = VoicePhase.LISTENING)
        job = viewModelScope.launch {
            dictation.listen(languageTag).collect { event ->
                when (event) {
                    DictationEvent.Ready -> _state.update { it.copy(ready = true) }
                    is DictationEvent.Level -> _state.update { it.copy(level = event.level) }
                    is DictationEvent.Partial -> _state.update { it.copy(partial = event.text, ready = true) }
                    is DictationEvent.Final -> finish(event.text)
                    is DictationEvent.Error -> _state.value = VoiceInputState(phase = VoicePhase.ERROR, error = event.error)
                }
            }
            // The flow ended without a result (should not happen): back to idle.
            if (_state.value.phase == VoicePhase.LISTENING) _state.value = VoiceInputState()
        }
    }

    private fun finish(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) {
            _state.value = VoiceInputState(phase = VoicePhase.ERROR, error = DictationError.NO_SPEECH)
        } else {
            _state.value = VoiceInputState()
            _results.trySend(clean)
        }
    }

    /** "Done": keeps what was heard so far without waiting for the speaker to pause. */
    fun done() {
        val heard = _state.value.partial
        job?.cancel()
        job = null
        finish(heard)
    }

    /** Stops listening and drops what was heard. */
    fun cancel() {
        job?.cancel()
        job = null
        _state.value = VoiceInputState()
    }

    fun dismissError() {
        if (_state.value.phase == VoicePhase.ERROR) _state.value = VoiceInputState()
    }
}

/** Joins dictated text onto what a field already holds, with one space between. */
fun appendDictation(current: String, dictated: String): String {
    val text = dictated.trim()
    if (text.isEmpty()) return current
    val base = current.trimEnd()
    return if (base.isEmpty()) text else "$base $text"
}
