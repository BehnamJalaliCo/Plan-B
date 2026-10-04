package com.behnamjalali.planb.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.launcher.LauncherIconSwitcher
import com.behnamjalali.planb.core.model.AppIcon
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Glance widget content (states are built by WidgetStateLoader) and the launcher icon switcher. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WidgetContentTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val action = actionRunCallback<CompleteTaskAction>()

    @Test
    fun todayShowsProgressAndCompletableTasks() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 180.dp))
        setContext(context)
        val state = TodayWidgetState(
            title = "امروز",
            date = "یکشنبه ۱۲ مهر ۱۴۰۵",
            progress = 0.5f,
            progressLabel = "۲ از ۴ انجام شد",
            tasks = listOf(WidgetTask(1, "خرید شیر", "۰۹:۰۰"), WidgetTask(2, "Call Sara", null)),
            empty = "",
            completeLabel = "انجام شد",
        )
        provideComposable {
            PlanBGlanceTheme {
                TodayContent(state, action) { id -> actionRunCallback<CompleteTaskAction>(actionParametersOf(WidgetKeys.ID to id)) }
            }
        }
        onNode(hasText("۲ از ۴ انجام شد")).assertExists()
        onNode(hasText("خرید شیر")).assertExists()
        onNode(hasContentDescription("انجام شد: Call Sara") and hasClickAction()).assertExists()
    }

    @Test
    fun todayEmptyState() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable {
            PlanBGlanceTheme {
                TodayContent(TodayWidgetState("Today", "Sunday", 1f, "3 of 3 done", emptyList(), "Nothing left for today.", "Complete"), action) { action }
            }
        }
        onNode(hasText("Nothing left for today.")).assertExists()
    }

    @Test
    fun lockedWidgetOpensTheProScreen() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable { PlanBGlanceTheme { LockedContent(WidgetUi.Locked("Plan-B Pro", "This widget is part of Plan-B Pro.", "Learn more"), action) } }
        onNode(hasText("This widget is part of Plan-B Pro.")).assertExists()
        onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun habitsShowDoneStateAndCheckIn() = runGlanceAppWidgetUnitTest {
        setContext(context)
        val state = HabitsWidgetState(
            "Habits",
            "Oct 4",
            listOf(WidgetHabit(1, "Read", done = true, detail = "1/1"), WidgetHabit(2, "Walk", done = false, detail = "0/1")),
            "",
            "Check in",
        )
        provideComposable { PlanBGlanceTheme { HabitsContent(state, action) { action } } }
        onNode(hasContentDescription("Check in: Walk, 0/1") and hasClickAction()).assertExists()
        onNode(hasText("Read")).assertExists()
    }

    @Test
    fun focusIdleShowsPlannedTimeAndStart() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable {
            PlanBGlanceTheme { FocusContent(FocusWidgetState("Focus", "Ready", "25:00", 25 * 60_000L, running = false, action = "Start"), action, action) }
        }
        onNode(hasText("25:00")).assertExists()
        onNode(hasText("Start")).assertExists()
    }

    @Test
    fun calendarMarksBusyDays() = runGlanceAppWidgetUnitTest {
        setContext(context)
        val weeks = listOf(
            (1..7).map { CalendarCell("$it", inMonth = true, today = it == 3, busy = it == 5, description = if (it == 5) "Day 5, has plans" else "Day $it") },
        )
        provideComposable { PlanBGlanceTheme { CalendarContent(CalendarWidgetState("Mehr 1405", listOf("S", "S", "M", "T", "W", "T", "F"), weeks), action) } }
        onNode(hasText("Mehr 1405")).assertExists()
        onNode(hasContentDescription("Day 5, has plans")).assertExists()
    }

    @Test
    fun quickAddOpensCapture() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable { PlanBGlanceTheme { QuickAddContent(QuickAddWidgetState("Quick add", "A task, note or event"), action) } }
        onNode(hasText("Quick add")).assertExists()
        onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun launcherIconSwitcherKeepsExactlyOneAlias() {
        var switched = 0
        val switcher = LauncherIconSwitcher(context) { switched++ }
        assertThat(switcher.current()).isEqualTo(AppIcon.CLASSIC)
        switcher.apply(AppIcon.OCEAN)
        assertThat(switcher.current()).isEqualTo(AppIcon.OCEAN)
        val pm = context.packageManager
        val enabled = AppIcon.entries.count {
            pm.getComponentEnabledSetting(android.content.ComponentName(context, LauncherIconSwitcher.aliasClass(it))) ==
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        assertThat(enabled).isEqualTo(1)
        assertThat(switched).isEqualTo(1)
        switcher.apply(AppIcon.CLASSIC)
        assertThat(switcher.current()).isEqualTo(AppIcon.CLASSIC)
    }
}
