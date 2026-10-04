package com.behnamjalali.planb

import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.model.UserSettings
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.behnamjalali.planb.feature.today.R as TodayR

/**
 * Core flows on a real device/emulator (CI runs these on an Android emulator). The broader
 * end-to-end suite runs on the JVM under Robolectric (app/src/test/.../e2e).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppFlowsTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var tasks: TaskRepository

    private var scenario: ActivityScenario<MainActivity>? = null
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        hilt.inject()
        // DataStore lives on disk; start every test from defaults.
        runBlocking { settings.update { UserSettings(language = it.language, animationsEnabled = false) } }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitFor(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        return compose.onAllNodes(matcher).onFirst()
    }

    @Test
    fun onboarding_canBeSkipped_andShowsToday() {
        launch()
        waitFor(hasTestTag("onboarding_skip")).performClick()
        waitFor(hasTestTag("nav_today"))
        assertThatOnboardingCompleted()
    }

    @Test
    fun topLevelTabs_allOpen() {
        runBlocking { settings.update { it.copy(onboardingCompleted = true) } }
        launch()
        for (tag in listOf("nav_calendar", "nav_tasks", "nav_notebooks", "nav_more", "nav_today")) {
            waitFor(hasTestTag(tag)).performClick()
            compose.waitForIdle()
        }
        waitFor(hasContentDescription(context.getString(TodayR.string.today_search)) and hasClickAction())
    }

    @Test
    fun quickCapture_createsTask() {
        runBlocking { settings.update { it.copy(onboardingCompleted = true) } }
        launch()
        waitFor(hasTestTag("quick_capture")).performClick()
        waitFor(hasSetTextAction()).performTextInput("Instrumented task")
        waitFor(hasText(context.getString(TodayR.string.capture_save)) and hasClickAction()).performClick()
        compose.waitUntil(15_000) {
            runBlocking { tasks.observeTasks(TaskFilter(view = TaskView.ALL, today = LocalDate.now())).first() }
                .any { it.title == "Instrumented task" }
        }
    }

    private fun assertThatOnboardingCompleted() {
        compose.waitUntil(15_000) { runBlocking { settings.current().onboardingCompleted } }
    }
}
