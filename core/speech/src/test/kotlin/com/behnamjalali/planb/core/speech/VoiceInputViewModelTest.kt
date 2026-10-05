package com.behnamjalali.planb.core.speech

import android.speech.SpeechRecognizer
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.awaitItem
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test

class VoiceInputViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    @Test
    fun partialTextIsShown_thenTheFinalTextIsDeliveredOnce() = runBlocking<Unit> {
        val fake = FakeVoiceDictation(
            script = listOf(
                DictationEvent.Ready,
                DictationEvent.Level(0.6f),
                DictationEvent.Partial("فردا ساعت"),
                DictationEvent.Partial("فردا ساعت ۵ عصر"),
                DictationEvent.Final("فردا ساعت ۵ عصر جلسه با علی"),
            ),
        )
        val vm = main.track(VoiceInputViewModel(fake))
        vm.start("fa-IR")
        assertThat(withTimeout(10_000) { vm.results.first() }).isEqualTo("فردا ساعت ۵ عصر جلسه با علی")
        vm.state.awaitItem { it.phase == VoicePhase.IDLE }
        assertThat(fake.languages).containsExactly("fa-IR")
    }

    @Test
    fun done_keepsWhatWasHeardSoFar() = runBlocking<Unit> {
        val fake = FakeVoiceDictation(script = listOf(DictationEvent.Ready, DictationEvent.Partial("buy milk")), hold = true)
        val vm = main.track(VoiceInputViewModel(fake))
        vm.start("en-US")
        val listening = vm.state.awaitItem { it.partial == "buy milk" }
        assertThat(listening.phase).isEqualTo(VoicePhase.LISTENING)
        assertThat(listening.ready).isTrue()
        vm.done()
        assertThat(withTimeout(10_000) { vm.results.first() }).isEqualTo("buy milk")
        assertThat(vm.state.value.phase).isEqualTo(VoicePhase.IDLE)
    }

    @Test
    fun errorsAndEmptyResults_showAnErrorUntilDismissed() = runBlocking<Unit> {
        val fake = FakeVoiceDictation(script = listOf(DictationEvent.Error(DictationError.NETWORK)))
        val vm = main.track(VoiceInputViewModel(fake))
        vm.start("fa-IR")
        assertThat(vm.state.awaitItem { it.phase == VoicePhase.ERROR }.error).isEqualTo(DictationError.NETWORK)
        vm.dismissError()
        assertThat(vm.state.value.phase).isEqualTo(VoicePhase.IDLE)
        fake.script = listOf(DictationEvent.Final("  "))
        vm.start("fa-IR")
        assertThat(vm.state.awaitItem { it.phase == VoicePhase.ERROR }.error).isEqualTo(DictationError.NO_SPEECH)
    }

    @Test
    fun cancel_dropsTheText() = runBlocking<Unit> {
        val fake = FakeVoiceDictation(script = listOf(DictationEvent.Partial("half")), hold = true)
        val vm = main.track(VoiceInputViewModel(fake))
        vm.start("en-US")
        vm.state.awaitItem { it.partial == "half" }
        vm.cancel()
        assertThat(vm.state.value).isEqualTo(VoiceInputState())
    }
}

class SpeechIntentsTest {
    @Test
    fun languageTag_followsTheAppLanguage() {
        assertThat(speechLanguageTag(Locale.forLanguageTag("fa"))).isEqualTo("fa-IR")
        assertThat(speechLanguageTag(Locale.forLanguageTag("fa-IR"))).isEqualTo("fa-IR")
        assertThat(speechLanguageTag(Locale.ENGLISH)).isEqualTo("en-US")
    }

    @Test
    fun recognizerErrors_mapToNeutralReasons() {
        assertThat(SpeechIntents.errorOf(SpeechRecognizer.ERROR_NO_MATCH)).isEqualTo(DictationError.NO_SPEECH)
        assertThat(SpeechIntents.errorOf(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)).isEqualTo(DictationError.NO_SPEECH)
        assertThat(SpeechIntents.errorOf(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)).isEqualTo(DictationError.PERMISSION)
        assertThat(SpeechIntents.errorOf(SpeechRecognizer.ERROR_NETWORK)).isEqualTo(DictationError.NETWORK)
        assertThat(SpeechIntents.errorOf(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)).isEqualTo(DictationError.BUSY)
        assertThat(SpeechIntents.errorOf(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)).isEqualTo(DictationError.LANGUAGE)
        assertThat(SpeechIntents.errorOf(SpeechRecognizer.ERROR_CLIENT)).isEqualTo(DictationError.OTHER)
    }

    @Test
    fun levels_areClamped() {
        assertThat(SpeechIntents.level(-10f)).isEqualTo(0f)
        assertThat(SpeechIntents.level(4f)).isEqualTo(0.5f)
        assertThat(SpeechIntents.level(30f)).isEqualTo(1f)
    }

    @Test
    fun dictation_isAppendedWithOneSpace() {
        assertThat(appendDictation("", " جلسه ")).isEqualTo("جلسه")
        assertThat(appendDictation("خرید ", "نان")).isEqualTo("خرید نان")
        assertThat(appendDictation("title", "  ")).isEqualTo("title")
    }
}
