package com.behnamjalali.planb.screenshots

import android.app.LocaleManager
import android.content.Context
import android.os.Looper
import android.os.LocaleList
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.MainActivity
import com.behnamjalali.planb.core.data.repository.FocusRepository
import com.behnamjalali.planb.core.data.repository.GoalRepository
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.NoteRepository
import com.behnamjalali.planb.core.data.repository.ProjectRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.ProjectMilestone
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskStatus
import com.behnamjalali.planb.core.model.ThemeMode
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.PlannerIcon
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.File
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.behnamjalali.planb.R as AppR
import com.behnamjalali.planb.feature.calendar.R as CalendarR
import com.behnamjalali.planb.feature.notebooks.R as NotesR
import com.behnamjalali.planb.feature.focus.R as FocusR
import com.behnamjalali.planb.feature.habits.R as HabitsR
import com.behnamjalali.planb.feature.projects.R as ProjectsR
import com.behnamjalali.planb.feature.review.R as ReviewR
import com.behnamjalali.planb.feature.settings.R as SettingsR
import com.behnamjalali.planb.feature.templates.R as TemplatesR
import com.behnamjalali.planb.feature.today.R as TodayR

/**
 * Full-app screenshots: the real Hilt graph, Room and navigation with seeded data and a
 * frozen clock, captured for every major screen in Persian/English × light/dark, plus
 * large-font variants. Output: artifacts/screenshots/<feature>/<screen>_<variant>.png.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class AppScreenshotTest(private val variant: Variant) {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var projects: ProjectRepository
    @Inject lateinit var notes: NoteRepository
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var goals: GoalRepository
    @Inject lateinit var events: EventRepository
    @Inject lateinit var focus: FocusRepository
    @Inject lateinit var clock: FakeTimeProvider

    private var scenario: ActivityScenario<MainActivity>? = null
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val fixtures by lazy { Fixtures(variant.language) }
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    @Before
    fun setUp() {
        applyQualifiers(variant)
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(if (variant.language == AppLanguage.PERSIAN) "fa" else "en")
        hilt.inject()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(seed: Boolean = true, onboarded: Boolean = true) {
        runBlocking {
            settings.update {
                it.copy(
                    language = variant.language,
                    themeMode = if (variant.dark) ThemeMode.DARK else ThemeMode.LIGHT,
                    onboardingCompleted = onboarded,
                    animationsEnabled = false,
                )
            }
            if (seed) seed()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    private suspend fun seed() {
        val f = fixtures
        val projectIds = f.projects.associate { it.id to projects.save(it.copy(id = 0)) }
        val tagIds = f.tasks.flatMap { it.tags }.distinct().associate { it.id to tasks.upsertTag(it.copy(id = 0)) }
        val taskIds = f.tasks.associate { task ->
            task.id to tasks.save(
                task.copy(
                    id = 0,
                    projectId = task.projectId?.let(projectIds::get),
                    tags = task.tags.map { it.copy(id = tagIds.getValue(it.id)) },
                    subtaskCount = 0,
                    completedSubtaskCount = 0,
                ),
            )
        }
        val parent = taskIds.getValue(1)
        listOf("Outline" to true, "Slides" to true, "Rehearse" to false).forEach { (title, done) ->
            tasks.save(Task(title = title, parentTaskId = parent, status = if (done) TaskStatus.DONE else TaskStatus.TODO))
        }
        val launch = projectIds.getValue(1)
        tasks.save(Task(title = if (f.language == AppLanguage.PERSIAN) "طراحی سیستم رنگ" else "Design the color system", projectId = launch, status = TaskStatus.DONE))
        tasks.setStatus(taskIds.getValue(2), TaskStatus.IN_PROGRESS)
        projects.saveMilestone(ProjectMilestone(projectId = launch, title = if (f.language == AppLanguage.PERSIAN) "نسخهٔ بتا" else "Beta release", date = f.today.plusDays(7)))
        projects.saveMilestone(ProjectMilestone(projectId = launch, title = if (f.language == AppLanguage.PERSIAN) "انتشار در کافه‌بازار" else "Cafe Bazaar release", date = f.today.plusDays(20)))
        f.events.forEach { events.save(it.copy(id = 0)) }
        f.habits.forEach { h ->
            val id = habits.save(h.habit.copy(id = 0))
            h.amounts.forEach { (date, amount) -> habits.checkIn(id, date, amount) }
        }
        f.goals.forEach { goals.save(it.copy(id = 0)) }
        val personal = notes.saveNotebook(Notebook(title = if (f.language == AppLanguage.PERSIAN) "شخصی" else "Personal", icon = PlannerIcon.HOME, color = AccentColor.PEACH))
        val work = notes.saveNotebook(Notebook(title = if (f.language == AppLanguage.PERSIAN) "کار" else "Work", icon = PlannerIcon.BRIEFCASE, color = AccentColor.POWDER_BLUE))
        f.notes.forEachIndexed { i, note -> notes.saveNote(note.copy(id = 0, notebookId = if (i == 0) work else personal)) }
        // Two finished focus sessions for history and summaries.
        repeat(2) {
            focus.start(Duration.ofMinutes(25).toMillis(), null)
            clock.advance(Duration.ofMinutes(25))
            focus.finish()
        }
        clock.instant = com.behnamjalali.planb.e2e.TestClockModule.START
    }

    // region helpers
    private fun waitFor(matcher: SemanticsMatcher, timeout: Long = 10_000): SemanticsNodeInteraction {
        try {
            compose.waitUntil(timeout) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            val tree = runCatching { compose.onAllNodes(isRoot()).onFirst().printToString(maxDepth = 30) }.getOrDefault("")
            throw AssertionError("Not found: ${matcher.description}\n$tree", e)
        }
        return compose.onAllNodes(matcher, useUnmergedTree = true).onFirst()
    }

    private fun click(text: String) {
        val clickable = hasText(text, substring = true) and hasClickAction()
        compose.waitForIdle()
        // Lazy lists only compose rows near the viewport, and the screen may still be loading:
        // keep scrolling any list towards the target until it exists and is on screen.
        compose.waitUntil(15_000) {
            val matches = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
            val onScreen = matches.fetchSemanticsNodes().indices.any { runCatching { matches[it].assertIsDisplayed() }.isSuccess }
            if (!onScreen) {
                val lists = compose.onAllNodes(hasScrollToNodeAction())
                for (i in lists.fetchSemanticsNodes().indices) {
                    if (runCatching { lists[i].performScrollToNode(hasText(text, substring = true)) }.isSuccess) break
                }
            }
            compose.onAllNodes(clickable).fetchSemanticsNodes().isNotEmpty() ||
                matches.fetchSemanticsNodes().isNotEmpty()
        }
        val node = if (compose.onAllNodes(clickable).fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodes(clickable).onFirst()
        } else {
            waitFor(hasText(text, substring = true))
        }
        runCatching { node.performScrollTo() }
        if (SemanticsActions.OnClick in node.fetchSemanticsNode().config) {
            // Invoke the click action directly: no touch means no platform ripple, whose
            // real-time animation would make screenshots nondeterministic.
            node.performSemanticsAction(SemanticsActions.OnClick)
        } else {
            node.performTouchInput { click(Offset(centerX, top + minOf(height * 0.25f, 40f))) }
        }
        compose.waitForIdle()
    }

    private fun clickDescription(description: String) {
        waitFor(hasContentDescription(description))
        // The merged (clickable) node carries the icon's description.
        compose.onAllNodes(hasContentDescription(description) and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun openMore(entry: Int) {
        click(s(AppR.string.nav_more))
        // A subtitle only the More screen shows (Today also has a "Projects" header).
        waitFor(hasText(s(AppR.string.more_projects_sub)))
        click(s(entry))
    }

    /** Selects a tab/segment and waits until the (asynchronously updated) state shows it selected. */
    private fun selectTab(text: String) {
        click(text)
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(text) and isSelected()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(folder: String, name: String) {
        // Let database flows deliver and ripples/animations settle before capturing.
        repeat(6) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
        }
        compose.onAllNodes(isRoot()).onFirst()
            .captureRoboImage(File(screenshotRoot, "$folder/${name}_${variant.suffix}.png").path)
    }
    // endregion

    @Test
    fun today() {
        launch()
        waitFor(hasText(fixtures.tasks.first().title, substring = true))
        capture("today", "today")
    }

    @Test
    fun todayEmpty() {
        launch(seed = false)
        waitFor(hasText(s(TodayR.string.today_empty_tasks_title)))
        capture("states", "today_empty")
    }

    @Test
    fun quickCapture() {
        launch()
        clickDescription(s(AppR.string.quick_capture))
        waitFor(hasSetTextAction())
        capture("capture", "quick_capture")
    }

    @Test
    fun tasks() {
        launch()
        click(s(AppR.string.nav_tasks))
        waitFor(hasText(fixtures.tasks.first().title, substring = true))
        capture("tasks", "tasks_today")
    }

    @Test
    fun taskEditor() {
        launch()
        click(s(AppR.string.nav_tasks))
        click(fixtures.tasks.first().title)
        waitFor(hasText("Rehearse"))
        capture("tasks", "task_editor")
    }

    @Test
    fun calendarViews() {
        launch()
        click(s(AppR.string.nav_calendar))
        waitFor(hasText(fixtures.events.first().title, substring = true))
        capture("calendar", "calendar_month")
        selectTab(s(CalendarR.string.calendar_view_week))
        capture("calendar", "calendar_week")
        selectTab(s(CalendarR.string.calendar_view_day))
        capture("calendar", "calendar_day")
        selectTab(s(CalendarR.string.calendar_view_agenda))
        capture("calendar", "calendar_agenda")
    }

    @Test
    fun eventEditor() {
        launch()
        click(s(AppR.string.nav_calendar))
        selectTab(s(CalendarR.string.calendar_view_day))
        click(fixtures.events.first().title)
        waitFor(hasText(s(CalendarR.string.event_editor_edit)))
        waitFor(hasSetTextAction() and hasText(fixtures.events.first().title))
        capture("calendar", "event_editor")
    }

    @Test
    fun notebooks() {
        launch()
        click(s(AppR.string.nav_notebooks))
        waitFor(hasText(fixtures.notes.first().title, substring = true))
        capture("notebooks", "notebooks")
        click(fixtures.notes.first().title)
        waitFor(hasContentDescription(s(NotesR.string.note_title_hint)))
        waitFor(hasText(fixtures.notes.first().title))
        capture("notebooks", "note_editor")
    }

    @Test
    fun more() {
        launch()
        click(s(AppR.string.nav_more))
        waitFor(hasText(s(AppR.string.more_projects_sub)))
        capture("more", "more")
    }

    @Test
    fun projects() {
        launch()
        openMore(AppR.string.more_projects)
        waitFor(hasText(fixtures.projects.first().title, substring = true))
        capture("projects", "projects")
        click(fixtures.projects.first().title)
        waitFor(hasText(s(ProjectsR.string.project_tab_overview)))
        waitFor(hasText(if (variant.language == AppLanguage.PERSIAN) "نسخهٔ بتا" else "Beta release"))
        capture("projects", "project_overview")
        selectTab(s(ProjectsR.string.project_tab_board))
        waitFor(hasText(s(ProjectsR.string.project_board_in_progress)))
        capture("projects", "project_board")
    }

    @Test
    fun habits() {
        launch()
        openMore(AppR.string.more_habits)
        waitFor(hasText(fixtures.habits.first().habit.title, substring = true))
        capture("habits", "habits")
        click(fixtures.habits.first().habit.title)
        waitFor(hasText(s(HabitsR.string.habit_best_streak)))
        capture("habits", "habit_detail")
    }

    @Test
    fun goals() {
        launch()
        openMore(AppR.string.more_goals)
        waitFor(hasText(fixtures.goals.first().title, substring = true))
        capture("goals", "goals")
    }

    @Test
    fun focus() {
        launch()
        openMore(AppR.string.more_focus)
        waitFor(hasText(s(FocusR.string.focus_history)))
        capture("focus", "focus")
    }

    @Test
    fun search() {
        launch()
        clickDescription(s(TodayR.string.today_search))
        waitFor(hasSetTextAction()).performTextInput(if (variant.language == AppLanguage.PERSIAN) "گزارش" else "report")
        waitFor(hasText(fixtures.tasks[3].title, substring = true))
        capture("search", "search_results")
    }

    @Test
    fun templates() {
        launch()
        openMore(AppR.string.more_templates)
        waitFor(hasText(s(TemplatesR.string.templates_builtin)))
        capture("templates", "templates")
    }

    @Test
    fun weeklyReview() {
        launch()
        openMore(AppR.string.more_review)
        waitFor(hasText(s(ReviewR.string.review_completed)))
        capture("review", "weekly_review")
    }

    @Test
    fun settingsAndBackup() {
        launch()
        openMore(AppR.string.more_settings)
        waitFor(hasText(s(SettingsR.string.settings_language)))
        capture("settings", "settings")
        click(s(SettingsR.string.settings_backup_restore))
        waitFor(hasText(s(SettingsR.string.backup_create)))
        capture("settings", "backup")
    }

    @Test
    fun about() {
        launch()
        openMore(AppR.string.more_settings)
        click(s(SettingsR.string.settings_about))
        waitFor(hasText(s(SettingsR.string.about_developer)))
        capture("settings", "about")
    }

    @Test
    fun onboarding() {
        launch(seed = false, onboarded = false)
        compose.waitForIdle()
        capture("onboarding", "onboarding")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = (Variant.STANDARD + Variant.LARGE_FONT).map { arrayOf(it) }
    }
}
