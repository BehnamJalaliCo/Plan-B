package com.behnamjalali.planb.e2e

import android.content.Context
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.AppLocales
import com.behnamjalali.planb.MainActivity
import com.behnamjalali.planb.R
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.UserSettings
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The first-run flow through the real app: language → welcome → slides → Today. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "fa-rIR-ldrtl-w411dp-h891dp-xxhdpi")
class OnboardingEndToEndTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var settings: SettingsRepository

    private var scenario: ActivityScenario<MainActivity>? = null
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun s(id: Int) = context.getString(id)

    @Before
    fun setUp() = hilt.inject()

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(stored: UserSettings) {
        runBlocking { settings.update { stored } }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun exists(matcher: SemanticsMatcher) = compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(matcher: SemanticsMatcher, timeout: Long = 10_000): SemanticsNodeInteraction {
        try {
            compose.waitUntil(timeout) { exists(matcher) }
        } catch (e: ComposeTimeoutException) {
            val tree = runCatching { compose.onAllNodes(isRoot()).onFirst().printToString(maxDepth = 40) }.getOrDefault("")
            throw AssertionError("Not found: ${matcher.description}\n$tree", e)
        }
        return compose.onAllNodes(matcher, useUnmergedTree = true).onFirst()
    }

    /** Clicks through the semantics action (the control may still be fading in). */
    private fun tap(matcher: SemanticsMatcher) {
        waitFor(matcher and isEnabled()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun current() = runBlocking { settings.current() }

    private fun waitUntil(condition: () -> Boolean) {
        compose.waitUntil(10_000) {
            // Robolectric's paused looper does not run posted work on its own (DataStore writes resume there).
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            condition()
        }
    }

    @Test
    fun firstLaunch_choosingEnglish_appliesItAtOnce_thenWelcomeSlidesAndToday() {
        launch(UserSettings(animationsEnabled = false))
        // The very first screen offers both languages, before anything else.
        waitFor(hasTestTag("onboarding_language_fa"))
        tap(hasTestTag("onboarding_language_en"))

        waitUntil { current().language == AppLanguage.ENGLISH && current().languageChosen }
        // Applied: the per-app locale follows, and with it calendar and digits.
        waitUntil { AppLocales.platformLanguage(context) == AppLanguage.ENGLISH }
        assertThat(current().calendarSystem).isEqualTo(CalendarSystem.GREGORIAN)
        assertThat(current().usePersianDigits).isFalse()
        assertThat(current().onboardingCompleted).isFalse()

        // The platform now recreates the activity in English (Robolectric needs to be told).
        RuntimeEnvironment.setQualifiers("+en-rUS-ldltr")
        scenario!!.recreate()

        // The recreated activity continues with the English welcome; the question is not asked again.
        waitFor(hasText(s(R.string.onboarding_welcome_tagline)))
        assertThat(s(R.string.onboarding_welcome_start)).isEqualTo("Start")
        assertThat(exists(hasTestTag("onboarding_language"))).isFalse()
        tap(hasTestTag("onboarding_welcome_start"))

        waitFor(hasText(s(R.string.onboarding_plan_title))).assertIsDisplayed()
        tap(hasTestTag("onboarding_next"))
        waitFor(hasText(s(R.string.onboarding_grow_title))).assertIsDisplayed()
        tap(hasTestTag("onboarding_back"))
        waitFor(hasText(s(R.string.onboarding_plan_title))).assertIsDisplayed()
        tap(hasTestTag("onboarding_next"))
        tap(hasTestTag("onboarding_next"))
        waitFor(hasText(s(R.string.onboarding_private_title))).assertIsDisplayed()
        assertThat(current().onboardingCompleted).isFalse()
        waitFor(hasText(s(R.string.onboarding_start)))
        tap(hasTestTag("onboarding_next"))

        waitFor(hasTestTag("nav_today"))
        waitUntil { current().onboardingCompleted }
        assertThat(current().language).isEqualTo(AppLanguage.ENGLISH)
    }

    @Test
    fun languageAlreadyChosen_startsAtTheWelcome_andSkipLeadsToToday() {
        // An upgrade (or a restart after the language step), with full motion.
        launch(UserSettings(languageChosen = true, animationsEnabled = true))
        waitFor(hasTestTag("onboarding_welcome"))
        assertThat(exists(hasTestTag("onboarding_language"))).isFalse()
        tap(hasTestTag("onboarding_welcome_start"))
        tap(hasTestTag("onboarding_skip"))
        waitFor(hasTestTag("nav_today"))
        waitUntil { current().onboardingCompleted }
        assertThat(current().language).isEqualTo(AppLanguage.PERSIAN)
    }

    @Test
    fun existingUsers_neverSeeOnboarding() {
        launch(UserSettings(onboardingCompleted = true, animationsEnabled = false))
        waitFor(hasTestTag("nav_today"))
        for (tag in listOf("onboarding_language", "onboarding_welcome", "onboarding_intro")) {
            assertThat(exists(hasTestTag(tag))).isFalse()
        }
    }
}
