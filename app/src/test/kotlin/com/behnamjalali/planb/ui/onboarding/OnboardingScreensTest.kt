package com.behnamjalali.planb.ui.onboarding

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.R
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.model.AppLanguage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The first-run screens on their own: timing, reduced motion, choosing a language. */
@RunWith(RobolectricTestRunner::class)
class OnboardingScreensTest {
    @get:Rule val compose = createComposeRule()

    private fun welcome(animations: Boolean, onStart: () -> Unit = {}) {
        compose.mainClock.autoAdvance = false
        compose.setContent { PlanBTheme(darkTheme = false, animationsEnabled = animations) { WelcomeScreen(onStart = onStart) } }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun welcome_withMotion_revealsStartAfterTheAnimation() {
        var started = false
        welcome(animations = true) { started = true }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("onboarding_welcome_start").assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(2_400)
        compose.onNodeWithTag("onboarding_welcome_start").assertIsEnabled().performClick()
        assertThat(started).isTrue()
    }

    @Test
    fun welcome_tapAnywhere_skipsToTheEnd() {
        welcome(animations = true)
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("onboarding_welcome_start").assertIsNotEnabled()
        compose.onNodeWithTag("onboarding_welcome").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("onboarding_welcome_start").assertIsEnabled()
    }

    @Test
    fun welcome_reducedMotion_showsStartRightAway() {
        welcome(animations = false)
        compose.onNodeWithTag("onboarding_welcome_start").assertIsEnabled()
    }

    @Test
    fun languagePicker_suggestsTheDeviceLanguage_andReportsTheChoice() {
        var chosen: AppLanguage? = null
        compose.setContent {
            PlanBTheme(darkTheme = false, animationsEnabled = false) {
                LanguagePickerScreen(suggested = AppLanguage.ENGLISH, onChosen = { chosen = it })
            }
        }
        compose.onNodeWithTag("onboarding_language_en").assertIsSelected()
        compose.onNodeWithTag("onboarding_language_fa").assertIsNotSelected().performClick()
        compose.waitUntil(5_000) { chosen != null }
        assertThat(chosen).isEqualTo(AppLanguage.PERSIAN)
        compose.onNodeWithTag("onboarding_language_fa").assertIsSelected()
        // A second tap while fading out changes nothing.
        compose.onNodeWithTag("onboarding_language_en").assertIsNotEnabled()
    }

    @Test
    fun brandMark_layersComeFromTheLauncherArtwork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mark = ImageVector.vectorResource(null, context.resources, R.drawable.ic_launcher_foreground)
        // If the artwork changes shape, the layer split must be revisited.
        assertThat(mark.pathCount()).isEqualTo(BrandLayers.PATH_COUNT)
        val layers = BrandLayers.from(mark)
        assertThat(layers.shadow.pathCount()).isEqualTo(1)
        assertThat(layers.stem.pathCount()).isEqualTo(1)
        assertThat(layers.upperLobe.pathCount()).isEqualTo(2)
        assertThat(layers.lowerLobe.pathCount()).isEqualTo(2)
    }
}
