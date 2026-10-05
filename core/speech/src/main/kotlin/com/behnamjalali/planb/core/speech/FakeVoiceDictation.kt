package com.behnamjalali.planb.core.speech

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A scripted recognizer for tests and screenshots: emits [script] and then, when [hold] is
 * true, keeps "listening" until cancelled (a dialog caught mid-sentence).
 */
class FakeVoiceDictation(
    var available: Boolean = true,
    var script: List<DictationEvent> = emptyList(),
    var hold: Boolean = false,
) : VoiceDictation {
    /** The language tags of every listen, in order. */
    val languages = mutableListOf<String>()

    override fun isAvailable(): Boolean = available

    override fun listen(languageTag: String): Flow<DictationEvent> = flow {
        languages += languageTag
        script.forEach { emit(it) }
        if (hold) awaitCancellation()
    }
}
