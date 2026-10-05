package com.behnamjalali.planb.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.behnamjalali.planb.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "fa-rIR-ldrtl-w411dp-h891dp-xxhdpi")
class AppSmokeTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launch_asksTheLanguageFirst_thenPersianOnboarding_thenToday() {
        // Bilingual language screen first.
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("زبان خود را انتخاب کنید")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Choose your language").assertIsDisplayed()
        compose.onNodeWithText("فارسی").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("شروع") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("شروع") and isEnabled()).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("رد شدن")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("رد شدن").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("کارهای امروز")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("کارهای امروز").assertIsDisplayed()
    }
}
