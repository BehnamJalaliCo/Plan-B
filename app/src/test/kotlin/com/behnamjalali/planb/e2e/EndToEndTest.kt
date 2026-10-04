package com.behnamjalali.planb.e2e

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.MainActivity
import com.behnamjalali.planb.core.backup.BackupCodec
import com.behnamjalali.planb.core.backup.BackupManager
import com.behnamjalali.planb.core.data.repository.FocusRepository
import com.behnamjalali.planb.core.data.repository.GoalRepository
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.NoteRepository
import com.behnamjalali.planb.core.data.repository.ProjectRepository
import com.behnamjalali.planb.core.data.repository.SearchRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.FocusStatus
import com.behnamjalali.planb.core.model.Goal
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.model.ThemeMode
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import com.behnamjalali.planb.R as AppR
import com.behnamjalali.planb.feature.focus.R as FocusR
import com.behnamjalali.planb.feature.notebooks.R as NotesR
import com.behnamjalali.planb.feature.projects.R as ProjectsR
import com.behnamjalali.planb.feature.settings.R as SettingsR
import com.behnamjalali.planb.feature.tasks.R as TasksR
import com.behnamjalali.planb.feature.today.R as TodayR

/**
 * End-to-end flows through the real app (Hilt graph, Room, DataStore, navigation),
 * hosted by Robolectric so they run on any machine. The same flows also run as
 * instrumentation tests on CI emulators.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "fa-rIR-ldrtl-w411dp-h891dp-xxhdpi")
class EndToEndTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var notes: NoteRepository
    @Inject lateinit var projects: ProjectRepository
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var goals: GoalRepository
    @Inject lateinit var focus: FocusRepository
    @Inject lateinit var search: SearchRepository
    @Inject lateinit var backup: BackupManager

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    @Before
    fun setUp() {
        hilt.inject()
        runBlocking { settings.update { it.copy(onboardingCompleted = true, animationsEnabled = false) } }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() = scenario.close()

    private fun waitFor(matcher: SemanticsMatcher, timeout: Long = 10_000): SemanticsNodeInteraction {
        try {
            compose.waitUntil(timeout) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            val tree = runCatching { compose.onAllNodes(androidx.compose.ui.test.isRoot()).onFirst().printToString(maxDepth = 60) }.getOrDefault("")
            throw AssertionError("Not found: ${matcher.description}\n$tree", e)
        }
        return compose.onAllNodes(matcher, useUnmergedTree = true).onFirst()
    }

    private fun waitForText(text: String) = waitFor(hasText(text, substring = true))
    private fun click(text: String) {
        // Prefer the clickable ancestor (merged tree) so dialogs and rows receive the click.
        val clickable = hasText(text, substring = true) and hasClickAction()
        val node = if (compose.onAllNodes(clickable).fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodes(clickable).onFirst()
        } else {
            waitForText(text)
        }
        // Items in scrollable rows (tabs, chips) may be off-screen; bring them into view first.
        runCatching { node.performScrollTo() }
        node.performClick()
    }
    private fun clickDescription(description: String) = waitFor(hasContentDescription(description)).performClick()

    private fun waitUntil(describe: () -> String = { "" }, condition: () -> Boolean) {
        try {
            compose.waitUntil(10_000) {
                // Robolectric's paused looper does not advance time on its own; let debounced work run.
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
                condition()
            }
        } catch (e: ComposeTimeoutException) {
            val tree = runCatching { compose.onAllNodes(androidx.compose.ui.test.isRoot()).onFirst().printToString(maxDepth = 40) }.getOrDefault("")
            throw AssertionError("Condition failed. State: ${describe()}\n$tree", e)
        }
    }

    private fun openTab(label: Int) = click(s(label))

    @Test
    fun quickCapture_createsTask_whichShowsInTasks_andCanBeCompleted() {
        clickDescription(s(AppR.string.quick_capture))
        waitFor(hasSetTextAction()).performTextInput("خرید کتاب Kotlin")
        click(s(TodayR.string.capture_save))
        waitUntil { runBlocking { tasks.observeTasks(TaskFilter(view = TaskView.ALL, today = LocalDate.now())).first() }.any { it.title == "خرید کتاب Kotlin" } }

        openTab(AppR.string.nav_tasks)
        waitForText("خرید کتاب Kotlin")
        // Complete via the checkbox (its content description is the task title).
        waitFor(hasContentDescription("خرید کتاب Kotlin")).performClick()
        waitUntil {
            runBlocking { tasks.observeTasks(TaskFilter(view = TaskView.COMPLETED, today = LocalDate.now())).first() }.any { it.title == "خرید کتاب Kotlin" }
        }
    }

    @Test
    fun taskEditor_editsAndPersistsChanges() {
        val id = runBlocking { tasks.save(com.behnamjalali.planb.core.model.Task(title = "Draft task", dueDate = LocalDate.now())) }
        openTab(AppR.string.nav_tasks)
        click("Draft task")
        waitForText(s(TasksR.string.task_editor_edit))
        waitFor(hasSetTextAction() and hasText("Draft task")).performTextReplacement("Draft task edited")
        click(s(TasksR.string.task_editor_save))
        waitUntil({ "title=" + runBlocking { tasks.getTask(id)?.title } }) { runBlocking { tasks.getTask(id)?.title } == "Draft task edited" }
    }

    @Test
    fun notes_newNote_typingAutosaves() {
        openTab(AppR.string.nav_notebooks)
        clickDescription(s(NotesR.string.notebooks_new_note))
        waitFor(hasContentDescription(s(NotesR.string.note_title_hint))).performTextInput("یادداشت آزمایشی")
        waitFor(hasSetTextAction() and !hasContentDescription(s(NotesR.string.note_title_hint))).performTextInput("Mixed متن with English")
        waitUntil({ runBlocking { notes.observeRecent(5).first() }.joinToString { it.title + "|" + it.document.plainText() } }) {
            runBlocking { notes.observeRecent(5).first() }.any { it.title == "یادداشت آزمایشی" && it.document.plainText().contains("Mixed متن") }
        }
    }

    @Test
    fun projects_createdProject_opensDetailAndBoard() {
        runBlocking { projects.save(Project(title = "Launch Plan-B")) }
        openTab(AppR.string.nav_more)
        click(s(AppR.string.more_projects))
        click("Launch Plan-B")
        click(s(ProjectsR.string.project_tab_board))
        waitForText(s(ProjectsR.string.project_board_in_progress))
    }

    @Test
    fun habits_checkInFromToday_persists() {
        val id = runBlocking { habits.save(Habit(title = "Read", startDate = LocalDate.now().minusDays(3))) }
        waitForText("Read")
        waitFor(hasContentDescription("Read")).performClick()
        waitUntil { runBlocking { habits.amountOn(id, LocalDate.now()) } == 1 }
    }

    @Test
    fun goal_andFocus_flows() {
        runBlocking { goals.save(Goal(title = "Read 12 books", target = 12.0)) }
        openTab(AppR.string.nav_more)
        click(s(AppR.string.more_focus))
        click(s(FocusR.string.focus_start))
        waitUntil { runBlocking { focus.observeActive().first() }?.status == FocusStatus.RUNNING }
        click(s(FocusR.string.focus_finish))
        waitUntil { runBlocking { focus.observeHistory().first() }.any { it.status == FocusStatus.COMPLETED } }
        assertThat(runBlocking { goals.observeGoals().first() }.single().title).isEqualTo("Read 12 books")
    }

    @Test
    fun search_findsPersianTaskWithArabicKeyboardVariant() {
        runBlocking { tasks.save(com.behnamjalali.planb.core.model.Task(title = "جلسهٔ کاری با تیم")) }
        clickDescription(s(TodayR.string.today_search))
        waitFor(hasSetTextAction()).performTextInput("كاري")
        waitForText("جلسهٔ کاری با تیم")
    }

    @Test
    fun settings_changeThemeAndLanguage_persist() {
        openTab(AppR.string.nav_more)
        click(s(AppR.string.more_settings))
        click(s(SettingsR.string.settings_theme_system))
        click(s(SettingsR.string.settings_theme_dark))
        waitUntil({ "theme=" + runBlocking { settings.current().themeMode } }) { runBlocking { settings.current().themeMode } == ThemeMode.DARK }
        click(s(SettingsR.string.settings_language_fa))
        click(s(SettingsR.string.settings_language_en))
        waitUntil({ "language=" + runBlocking { settings.current().language } }) { runBlocking { settings.current().language } == AppLanguage.ENGLISH }
    }

    @Test
    fun backup_snapshotAndRestore_roundTripsUserData() = runBlocking {
        tasks.save(com.behnamjalali.planb.core.model.Task(title = "Before backup"))
        val archive = backup.snapshot()
        val bytes = ByteArrayOutputStream().also { BackupCodec.write(archive, it) }.toByteArray()
        tasks.save(com.behnamjalali.planb.core.model.Task(title = "After backup"))
        backup.restore(BackupCodec.read(ByteArrayInputStream(bytes)))
        val titles = tasks.observeTasks(TaskFilter(view = TaskView.ALL, today = LocalDate.now())).first().map { it.title }
        assertThat(titles).containsExactly("Before backup")
        assertThat(search.search("After")).isEmpty()
    }
}
