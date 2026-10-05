package com.behnamjalali.planb.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.UserSettings
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The first-run state machine and its ViewModel on the real settings store. */
@RunWith(RobolectricTestRunner::class)
class OnboardingFlowTest {
    @get:Rule val main = RealMainDispatcherRule()

    private val graph = TestDataGraph()

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = main.track(OnboardingViewModel(graph.settings, saved))

    // region pure flow

    @Test
    fun firstLaunch_startsWithTheLanguage_thenWelcomeIntroDone() {
        val fresh = UserSettings()
        assertThat(OnboardingFlow.resolve(fresh, OnboardingStep.WELCOME)).isEqualTo(OnboardingStep.LANGUAGE)
        // Transient progress can never skip the language question.
        assertThat(OnboardingFlow.resolve(fresh, OnboardingStep.INTRO)).isEqualTo(OnboardingStep.LANGUAGE)
        assertThat(OnboardingFlow.resolve(fresh, OnboardingStep.DONE)).isEqualTo(OnboardingStep.LANGUAGE)
        assertThat(OnboardingFlow.next(OnboardingStep.LANGUAGE)).isEqualTo(OnboardingStep.LANGUAGE)

        val chosen = OnboardingFlow.choose(fresh, AppLanguage.ENGLISH)
        assertThat(OnboardingFlow.resolve(chosen, OnboardingStep.LANGUAGE)).isEqualTo(OnboardingStep.WELCOME)
        assertThat(OnboardingFlow.resolve(chosen, OnboardingStep.WELCOME)).isEqualTo(OnboardingStep.WELCOME)
        assertThat(OnboardingFlow.next(OnboardingStep.WELCOME)).isEqualTo(OnboardingStep.INTRO)
        assertThat(OnboardingFlow.next(OnboardingStep.INTRO)).isEqualTo(OnboardingStep.DONE)
        assertThat(OnboardingFlow.next(OnboardingStep.DONE)).isEqualTo(OnboardingStep.DONE)
    }

    @Test
    fun existingUsers_areDone_whateverTheProgress() {
        val done = UserSettings(onboardingCompleted = true)
        for (progress in OnboardingStep.entries) {
            assertThat(OnboardingFlow.resolve(done, progress)).isEqualTo(OnboardingStep.DONE)
        }
    }

    @Test
    fun choosingALanguage_switchesCalendarDigitsAndWeekStartWithIt() {
        val english = OnboardingFlow.choose(UserSettings(), AppLanguage.ENGLISH)
        assertThat(english.language).isEqualTo(AppLanguage.ENGLISH)
        assertThat(english.languageChosen).isTrue()
        assertThat(english.calendarSystem).isEqualTo(CalendarSystem.GREGORIAN)
        assertThat(english.usePersianDigits).isFalse()
        assertThat(english.firstDayOfWeek).isEqualTo(DayOfWeek.MONDAY)

        val persian = OnboardingFlow.choose(english, AppLanguage.PERSIAN)
        assertThat(persian.calendarSystem).isEqualTo(CalendarSystem.JALALI)
        assertThat(persian.usePersianDigits).isTrue()
        assertThat(persian.firstDayOfWeek).isEqualTo(DayOfWeek.SATURDAY)
    }

    @Test
    fun suggestion_followsTheDeviceLanguage_andDefaultsToPersian() {
        assertThat(OnboardingFlow.suggestedLanguage("fa")).isEqualTo(AppLanguage.PERSIAN)
        assertThat(OnboardingFlow.suggestedLanguage("FA")).isEqualTo(AppLanguage.PERSIAN)
        assertThat(OnboardingFlow.suggestedLanguage("en")).isEqualTo(AppLanguage.ENGLISH)
        assertThat(OnboardingFlow.suggestedLanguage("de")).isEqualTo(AppLanguage.ENGLISH)
        assertThat(OnboardingFlow.suggestedLanguage(null)).isEqualTo(AppLanguage.PERSIAN)
        assertThat(OnboardingFlow.suggestedLanguage("")).isEqualTo(AppLanguage.PERSIAN)
        assertThat(OnboardingFlow.suggestedLanguage("und")).isEqualTo(AppLanguage.PERSIAN)
    }

    // endregion

    // region ViewModel

    @Test
    fun viewModel_firstLaunch_choosesLanguage_persistsIt_andWalksToDone() = runBlocking<Unit> {
        val vm = viewModel()
        vm.step.awaitItem { it == OnboardingStep.LANGUAGE }
        // Moving on is only possible by choosing.
        vm.advance()
        vm.skip()
        assertThat(vm.step.awaitItem { it != null }).isEqualTo(OnboardingStep.LANGUAGE)

        vm.chooseLanguage(AppLanguage.ENGLISH)
        vm.step.awaitItem { it == OnboardingStep.WELCOME }
        val stored = graph.settings.current()
        assertThat(stored.language).isEqualTo(AppLanguage.ENGLISH)
        assertThat(stored.languageChosen).isTrue()
        assertThat(stored.calendarSystem).isEqualTo(CalendarSystem.GREGORIAN)
        assertThat(stored.onboardingCompleted).isFalse()

        vm.advance()
        vm.step.awaitItem { it == OnboardingStep.INTRO }
        vm.advance()
        vm.step.awaitItem { it == OnboardingStep.DONE }
        // Completion itself is written by the app once the flow has faded out.
        assertThat(graph.settings.current().onboardingCompleted).isFalse()
    }

    @Test
    fun viewModel_skip_endsTheIntro() = runBlocking<Unit> {
        graph.settings.update { it.copy(languageChosen = true) }
        val vm = viewModel()
        vm.step.awaitItem { it == OnboardingStep.WELCOME }
        vm.advance()
        vm.step.awaitItem { it == OnboardingStep.INTRO }
        vm.skip()
        vm.step.awaitItem { it == OnboardingStep.DONE }
    }

    @Test
    fun viewModel_languageAlreadyChosen_skipsThePicker() = runBlocking<Unit> {
        // An upgrade: the stored choice (or a finished onboarding) means no language screen.
        graph.settings.update { it.copy(language = AppLanguage.ENGLISH, languageChosen = true) }
        assertThat(viewModel().step.awaitItem { it != null }).isEqualTo(OnboardingStep.WELCOME)
    }

    @Test
    fun viewModel_existingUsers_neverSeeOnboarding() = runBlocking<Unit> {
        graph.settings.update { it.copy(onboardingCompleted = true) }
        assertThat(viewModel().step.awaitItem { it != null }).isEqualTo(OnboardingStep.DONE)
    }

    @Test
    fun viewModel_progressSurvivesProcessDeath() = runBlocking<Unit> {
        graph.settings.update { it.copy(languageChosen = true) }
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        first.step.awaitItem { it == OnboardingStep.WELCOME }
        first.advance()
        first.step.awaitItem { it == OnboardingStep.INTRO }
        // A new ViewModel restored from the saved state continues on the slides.
        val restored = viewModel(SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        assertThat(restored.step.awaitItem { it != null }).isEqualTo(OnboardingStep.INTRO)
    }

    // endregion
}
