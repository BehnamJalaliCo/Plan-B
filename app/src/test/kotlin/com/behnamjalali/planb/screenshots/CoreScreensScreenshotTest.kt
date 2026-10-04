package com.behnamjalali.planb.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.behnamjalali.planb.core.model.CalendarView
import com.behnamjalali.planb.core.model.DashboardConfig
import com.behnamjalali.planb.core.model.Streak
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.feature.calendar.CalendarCallbacks
import com.behnamjalali.planb.feature.calendar.CalendarScreen
import com.behnamjalali.planb.feature.calendar.CalendarUiState
import com.behnamjalali.planb.feature.calendar.DayItems
import com.behnamjalali.planb.feature.tasks.TasksCallbacks
import com.behnamjalali.planb.feature.tasks.TasksFilterState
import com.behnamjalali.planb.feature.tasks.TasksScreen
import com.behnamjalali.planb.feature.tasks.TasksUiState
import com.behnamjalali.planb.feature.today.Greeting
import com.behnamjalali.planb.feature.today.HabitToday
import com.behnamjalali.planb.feature.today.TodayActions
import com.behnamjalali.planb.feature.today.TodayData
import com.behnamjalali.planb.feature.today.TodayScreen
import com.behnamjalali.planb.feature.today.TodayUiState
import com.behnamjalali.planb.core.model.UserSettings
import com.behnamjalali.planb.core.datetime.CalendarEngines
import com.behnamjalali.planb.core.datetime.MonthGrid
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CoreScreensScreenshotTest(private val variant: Variant) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun today() = compose.captureScreen("today", "today", variant) { f ->
        TodayScreen(
            state = TodayUiState.Success(
                TodayData(
                    date = f.today,
                    greeting = Greeting.MORNING,
                    dashboard = DashboardConfig(),
                    todayTasks = f.todayTasks,
                    completedToday = 2,
                    upcoming = f.upcoming,
                    events = f.occurrences(f.today, f.today),
                    habits = f.habits.map { HabitToday(it, it.amounts[f.today] ?: 0, Streak(12, Streak.Unit.DAYS)) },
                    activeFocus = null,
                    focusMinutesToday = 50,
                    projects = f.projectSummaries,
                    notes = f.notes,
                ),
            ),
            actions = TodayActions(),
            onToggleTask = { _, _ -> },
            onCheckInHabit = { _, _ -> },
        )
    }

    @Test
    fun tasks() = compose.captureScreen("tasks", "tasks", variant) { f ->
        TasksScreen(
            state = TasksUiState(
                loading = false,
                filter = TasksFilterState(view = TaskView.TODAY),
                tasks = f.tasks.take(6),
                projects = f.projects,
                tags = f.tasks.flatMap { it.tags }.distinct(),
            ),
            callbacks = TasksCallbacks(),
        )
    }

    @Test
    fun calendarMonth() = compose.captureScreen("calendar", "calendar_month", variant) { f ->
        val settings = UserSettings(language = variant.language)
        val engine = CalendarEngines.of(settings.calendarSystem)
        val grid = MonthGrid.build(engine, engine.monthOf(f.today), settings.firstDayOfWeek)
        val from = grid.first().first().date
        val to = grid.last().last().date
        val items = f.occurrences(from, to).groupBy { it.date }.mapValues { DayItems(events = it.value) }
            .toMutableMap()
        items[f.today] = DayItems(events = f.occurrences(f.today, f.today), tasks = f.todayTasks.take(2))
        CalendarScreen(
            state = CalendarUiState(
                loading = false,
                view = CalendarView.MONTH,
                selected = f.today,
                calendarSystem = settings.calendarSystem,
                firstDayOfWeek = settings.firstDayOfWeek,
                rangeStart = from,
                rangeEnd = to,
                items = items,
            ),
            callbacks = CalendarCallbacks(),
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = Variant.STANDARD.map { arrayOf(it) }
    }
}
