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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
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
import com.behnamjalali.planb.core.billing.DeveloperBilling
import com.behnamjalali.planb.core.calendarsync.DeviceEventData
import com.behnamjalali.planb.core.calendarsync.FakeDeviceCalendarStore
import com.behnamjalali.planb.core.datastore.UserPreferencesDataSource
import com.behnamjalali.planb.core.billing.EntitlementRepository
import com.behnamjalali.planb.core.billing.ProProduct
import com.behnamjalali.planb.core.data.security.SecurityPreferences
import com.behnamjalali.planb.feature.security.R as SecurityR
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import com.behnamjalali.planb.core.data.repository.FocusRepository
import com.behnamjalali.planb.core.data.repository.GoalRepository
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.NoteRepository
import com.behnamjalali.planb.core.data.repository.ProjectRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.data.repository.SmartListRepository
import com.behnamjalali.planb.core.data.repository.TaskPlanningRepository
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.RecurrenceFrequency
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.behnamjalali.planb.core.model.SavedFilter
import com.behnamjalali.planb.core.model.SmartDateRange
import com.behnamjalali.planb.core.model.SmartFilter
import com.behnamjalali.planb.core.model.TaskReminder
import com.behnamjalali.planb.core.model.TaskReminderKind
import com.behnamjalali.planb.core.model.TaskSort
import com.behnamjalali.planb.feature.tasks.R as TasksR
import com.behnamjalali.planb.core.ui.R as UiR
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.data.repository.AttachmentRepository
import com.behnamjalali.planb.core.model.AttachmentKind
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.rich.AudioData
import com.behnamjalali.planb.core.model.rich.DrawingRef
import com.behnamjalali.planb.core.model.rich.RichBlocks
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
import com.behnamjalali.planb.feature.pro.R as ProR
import com.behnamjalali.planb.feature.projects.R as ProjectsR
import com.behnamjalali.planb.feature.reports.R as ReportsR
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
    @Inject lateinit var developerBilling: DeveloperBilling
    @Inject lateinit var entitlements: EntitlementRepository
    @Inject lateinit var security: SecurityPreferences
    @Inject lateinit var smartLists: SmartListRepository
    @Inject lateinit var planning: TaskPlanningRepository
    @Inject lateinit var deviceCalendars: FakeDeviceCalendarStore
    @Inject lateinit var preferences: UserPreferencesDataSource
    @Inject lateinit var attachments: AttachmentRepository

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

    private fun launch(
        seed: Boolean = true,
        onboarded: Boolean = true,
        languageChosen: Boolean = true,
        pro: Boolean = false,
        beforeLaunch: suspend () -> Unit = {},
    ) {
        runBlocking {
            if (pro) {
                developerBilling.setOwned(ProProduct.LIFETIME)
                // Ask the (fake) store directly so the cached entitlement is written before launch.
                entitlements.refresh()
                withTimeout(30_000) { entitlements.isPro.first { it } }
            }
            settings.update {
                it.copy(
                    language = variant.language,
                    themeMode = if (variant.dark) ThemeMode.DARK else ThemeMode.LIGHT,
                    onboardingCompleted = onboarded,
                    languageChosen = languageChosen,
                    animationsEnabled = false,
                )
            }
            if (seed) seed()
            beforeLaunch()
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
        // The form loads asynchronously (task, then its planning data).
        waitFor(hasSetTextAction() and hasText(fixtures.tasks.first().title))
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

    // region Plan-B Pro rich notes (#15, #17, #19, #20, #23)

    /** A note of its own (pinned, so it is listed first), opened in the editor. */
    private fun openRichNote(title: String, blocks: suspend (noteId: Long) -> List<NoteBlock>) {
        launch(pro = true) {
            val notebook = notes.observeNotebooks().first().first().id
            val id = notes.saveNote(Note(notebookId = notebook, title = title, pinned = true))
            notes.updateContent(id, title, NoteDocument(blocks = blocks(id)))
        }
        click(s(AppR.string.nav_notebooks))
        click(title)
        waitFor(hasContentDescription(s(NotesR.string.note_title_hint)))
        waitFor(hasText(title))
    }

    @Test
    fun noteWithImageTableAndDatabase() {
        openRichNote(t("خرید وسایل دفتر", "Office supplies")) { id ->
            val photo = attachments.addImage(id, AttachmentKind.IMAGE, "board.jpg") { RichFixtures.photo() }
            listOf(
                NoteBlock("p", BlockType.IMAGE, t("تخته‌ی جلسه", "Meeting whiteboard"), attachmentId = photo.id),
                NoteBlock("t", BlockType.TABLE, data = RichBlocks.encode(RichFixtures.table(variant.language))),
                NoteBlock("d", BlockType.DATABASE, data = RichBlocks.encode(RichFixtures.database(variant.language))),
            )
        }
        waitFor(hasTestTag(com.behnamjalali.planb.feature.notebooks.rich.LOADED_TAG))
        capture("notebooks", "note_rich")
    }

    @Test
    fun noteWithDrawing() {
        openRichNote(t("طرح اولیه", "First sketch")) { id ->
            val drawing = RichFixtures.drawing()
            val (preview, vector) = attachments.saveDrawing(id, null, null, drawing, RichFixtures.png(drawing), drawing.width, drawing.height)
            listOf(
                NoteBlock("d", BlockType.DRAWING, attachmentId = preview.id, data = RichBlocks.encode(DrawingRef(vector.id, drawing.width, drawing.height))),
                NoteBlock("x", BlockType.TEXT, t("ایده‌ی صفحه‌ی خانه با کارت‌های بزرگ.", "Home screen idea with large cards.")),
            )
        }
        waitFor(hasTestTag(com.behnamjalali.planb.feature.notebooks.rich.LOADED_TAG))
        capture("notebooks", "note_drawing")
    }

    @Test
    fun noteWithVoiceRecording() {
        openRichNote(t("جلسه‌ی صبح", "Morning stand-up")) { id ->
            val file = File.createTempFile("voice", ".m4a").apply { writeBytes(ByteArray(2048)) }
            val audio = attachments.addRecording(id, file, 83_000, t("یادداشت صوتی", "Voice note"))
            attachments.setTranscript(audio.id, t("فردا نسخه‌ی بتا را برای تیم می‌فرستیم و بازخوردها را جمع می‌کنیم.", "Tomorrow we send the beta to the team and collect feedback."))
            listOf(
                NoteBlock("a", BlockType.AUDIO, data = RichBlocks.encode(AudioData(RichFixtures.waveform())), attachmentId = audio.id),
                NoteBlock("x", BlockType.TEXT, t("کارهای بعدی را در فهرست بنویسم.", "Write the next steps into the list.")),
            )
        }
        waitFor(hasText(t("فردا نسخه‌ی بتا", "Tomorrow we send"), substring = true))
        capture("notebooks", "note_audio")
    }

    @Test
    fun noteWithMathAndChart() {
        openRichNote(t("جمع‌بندی فصل", "Quarter summary")) {
            listOf(
                NoteBlock("m", BlockType.MATH, "x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}"),
                NoteBlock("s", BlockType.MATH, "\\sum_{i=1}^{n} i^2 = \\frac{n(n+1)(2n+1)}{6}"),
                NoteBlock("c", BlockType.CHART, data = RichBlocks.encode(RichFixtures.chart(variant.language))),
            )
        }
        waitFor(hasContentDescription(t("نمودار میله‌ای", "Bar chart"), substring = true))
        capture("notebooks", "note_math_chart")
    }

    @Test
    fun noteWithScan() {
        openRichNote(t("فاکتور تعمیرات", "Repair invoice")) { id ->
            val scan = attachments.addImage(id, AttachmentKind.SCAN, "scan.jpg") { RichFixtures.receipt() }
            attachments.setOcrText(scan.id, "INVOICE No. 1042\nScreen repair      1,200,000\nBattery              850,000\nTOTAL            2,050,000")
            listOf(NoteBlock("s", BlockType.SCAN, attachmentId = scan.id))
        }
        waitFor(hasTestTag(com.behnamjalali.planb.feature.notebooks.rich.LOADED_TAG))
        waitFor(hasText("INVOICE No. 1042", substring = true))
        capture("notebooks", "note_scan")
    }

    // endregion

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
        // The automatic-backup section appears once its device-only settings are read.
        waitFor(hasText(s(SettingsR.string.auto_backup_summary)))
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
    fun paywall() {
        launch()
        click(s(AppR.string.nav_more))
        waitFor(hasText(s(AppR.string.more_projects_sub)))
        click(s(AppR.string.more_pro_sub))
        waitFor(hasText(s(ProR.string.pro_hero_title)))
        // Wait until the (fake) store has answered, so the plans are enabled.
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(s(ProR.string.pro_state_loading))).fetchSemanticsNodes().isEmpty() }
        capture("pro", "paywall")
    }

    // region Plan-B Pro security and data (#36–#38)

    @Test
    fun trash() {
        launch(pro = true) {
            tasks.delete(listOf(tasks.observeTasks(com.behnamjalali.planb.core.data.repository.TaskFilter(today = fixtures.today)).first().first { it.title == fixtures.tasks[5].title }.id))
            val notebook = notes.observeNotebooks().first().first().id
            notes.deleteNote(notes.observeNotes(notebook).first().first().id)
            clock.advance(Duration.ofDays(3))
            tasks.delete(listOf(tasks.observeTasks(com.behnamjalali.planb.core.data.repository.TaskFilter(today = fixtures.today)).first().first { it.title == fixtures.tasks[0].title }.id))
            clock.instant = com.behnamjalali.planb.e2e.TestClockModule.START.plus(Duration.ofDays(3))
        }
        openMore(AppR.string.more_trash)
        waitFor(hasText(fixtures.tasks[0].title, substring = true))
        capture("security", "trash")
    }

    @Test
    fun activity() {
        launch(pro = true) {
            val id = tasks.observeTasks(com.behnamjalali.planb.core.data.repository.TaskFilter(today = fixtures.today)).first().first { it.title == fixtures.tasks[2].title }.id
            clock.advance(Duration.ofMinutes(40))
            tasks.setCompleted(id, true)
            clock.instant = com.behnamjalali.planb.e2e.TestClockModule.START
        }
        openMore(AppR.string.more_activity)
        waitFor(hasText(s(SecurityR.string.activity_completed), substring = true))
        capture("security", "activity")
    }

    @Test
    fun securitySettings() {
        launch(pro = true)
        openMore(AppR.string.more_settings)
        click(s(SettingsR.string.settings_security_summary))
        waitFor(hasText(s(SecurityR.string.security_set_passphrase)))
        capture("security", "security_settings")
    }

    @Test
    fun lockScreen() {
        launch { security.updateAppLock { it.copy(enabled = true) } }
        waitFor(hasText(s(SecurityR.string.lock_title)))
        capture("security", "lock_screen")
    }

    // endregion

    // Plan-B Pro reports and personalization (#31–#35), captured as a Pro user.

    @Test
    fun statistics() {
        launch(pro = true)
        openMore(AppR.string.more_statistics)
        waitFor(hasText(s(ReportsR.string.reports_completed_chart)))
        capture("reports", "statistics")
    }

    @Test
    fun yearReport() {
        launch(pro = true)
        openMore(AppR.string.more_statistics)
        click(s(ReportsR.string.reports_open_year))
        waitFor(hasText(s(ReportsR.string.year_tasks)))
        capture("reports", "my_year")
    }

    @Test
    fun appearance() {
        launch(pro = true)
        openMore(AppR.string.more_settings)
        click(s(SettingsR.string.settings_personalize))
        waitFor(hasText(s(SettingsR.string.appearance_app_icon)))
        capture("settings", "appearance")
    }

    // region Plan-B Pro planning (#4, #9–#14), captured as a Pro user.

    private suspend fun seededTask(index: Int): Task =
        tasks.observeTasks(com.behnamjalali.planb.core.data.repository.TaskFilter(today = fixtures.today)).first().first { it.title == fixtures.tasks[index].title }

    @Test
    fun smartListBuilder() {
        launch(pro = true) {
            smartLists.save(
                SavedFilter(
                    name = if (variant.language == AppLanguage.PERSIAN) "مهم این هفته" else "Important this week",
                    icon = PlannerIcon.ROCKET,
                    color = AccentColor.ROSE,
                    filter = SmartFilter(
                        priorities = setOf(Priority.HIGH, Priority.MEDIUM),
                        dateRange = SmartDateRange.NEXT_7_DAYS,
                        sort = TaskSort.DEADLINE,
                    ),
                ),
            )
        }
        click(s(AppR.string.nav_tasks))
        click(if (variant.language == AppLanguage.PERSIAN) "مهم این هفته" else "Important this week")
        click(s(TasksR.string.tasks_edit_smart_list))
        waitFor(hasText(s(TasksR.string.smart_list_priority)))
        // The live match count arrives after a short debounce: high/medium priority within 7 days.
        val three = if (variant.language == AppLanguage.PERSIAN) "۳" else "3"
        waitFor(hasText(context.resources.getQuantityString(TasksR.plurals.smart_list_matches, 3, three)))
        capture("tasks", "smart_list_builder")
    }

    @Test
    fun eisenhowerMatrix() {
        launch(pro = true) {
            val report = seededTask(3)
            tasks.save(report.copy(deadline = fixtures.today.plusDays(1)))
        }
        click(s(AppR.string.nav_tasks))
        clickDescription(s(TasksR.string.tasks_eisenhower))
        waitFor(hasText(s(TasksR.string.eisenhower_schedule_sub)))
        waitFor(hasText(fixtures.tasks[6].title, substring = true))
        capture("tasks", "eisenhower")
    }

    @Test
    fun projectTimeline() {
        launch(pro = true) {
            val presentation = seededTask(0)
            val call = seededTask(1)
            tasks.save(presentation.copy(startDate = fixtures.today.minusDays(3)))
            tasks.save(call.copy(startDate = fixtures.today.plusDays(1), dueDate = fixtures.today.plusDays(4), deadline = fixtures.today.plusDays(6)))
            val projectId = presentation.projectId!!
            val beta = tasks.save(Task(title = if (variant.language == AppLanguage.PERSIAN) "آزمون نسخهٔ بتا" else "Beta testing", projectId = projectId, startDate = fixtures.today.plusDays(5), dueDate = fixtures.today.plusDays(12)))
            planning.setDependencies(call.id, listOf(presentation.id))
            planning.setDependencies(beta, listOf(call.id))
        }
        openMore(AppR.string.more_projects)
        click(fixtures.projects.first().title)
        waitFor(hasText(s(ProjectsR.string.project_tab_overview)))
        selectTab(s(ProjectsR.string.project_tab_timeline))
        waitFor(hasText(s(ProjectsR.string.project_timeline_days)))
        capture("projects", "project_timeline")
    }

    @Test
    fun taskEditorPlanning() {
        launch(pro = true) {
            val presentation = seededTask(0)
            val call = seededTask(1)
            tasks.save(presentation.copy(deadline = fixtures.today.plusDays(2), nag = true))
            planning.setReminders(
                presentation.id,
                listOf(
                    TaskReminder(kind = TaskReminderKind.OFFSET, offsetMinutes = 60),
                    TaskReminder(kind = TaskReminderKind.DEADLINE, offsetMinutes = 1440),
                ),
                nagIntervalMinutes = 15,
            )
            planning.setDependencies(presentation.id, listOf(call.id))
        }
        click(s(AppR.string.nav_tasks))
        click(fixtures.tasks.first().title)
        waitFor(hasSetTextAction() and hasText(fixtures.tasks.first().title))
        waitFor(hasText(s(TasksR.string.task_editor_more_reminders))).performScrollTo()
        waitFor(hasText(fixtures.tasks[1].title, substring = true)).performScrollTo()
        capture("tasks", "task_editor_planning")
    }

    @Test
    fun advancedRecurrence() {
        launch(pro = true) {
            val bill = seededTask(5)
            tasks.save(
                bill.copy(
                    recurrence = RecurrenceRule(
                        RecurrenceFrequency.MONTHLY,
                        weekdays = setOf(java.time.DayOfWeek.MONDAY),
                        setPosition = 2,
                        calendarSystem = if (variant.language == AppLanguage.PERSIAN) CalendarSystem.JALALI else CalendarSystem.GREGORIAN,
                    ),
                ),
            )
        }
        click(s(AppR.string.nav_tasks))
        selectTab(s(TasksR.string.tasks_view_all))
        click(fixtures.tasks[5].title)
        waitFor(hasText(s(TasksR.string.task_editor_repeat)))
        click(s(TasksR.string.task_editor_repeat))
        click(s(UiR.string.repeat_custom))
        waitFor(hasText(s(UiR.string.repeat_monthly_on_weekday)))
        capture("tasks", "advanced_recurrence")
    }

    // endregion

    // region Plan-B Pro calendar (#2, #3, #6, #7), captured as a Pro user.

    private val fa get() = variant.language == AppLanguage.PERSIAN
    private fun t(faText: String, enText: String) = if (fa) faText else enText

    /** 22 Aban 1405 (13 November 2026), 10:00 Tehran: an official holiday (martyrdom of Fatima). */
    private val holidayMorning = java.time.Instant.parse("2026-11-13T06:30:00Z")

    @Test
    fun calendarHolidays() {
        launch(pro = true) {
            clock.instant = holidayMorning
            val day = java.time.LocalDate.of(2026, 11, 13)
            events.save(com.behnamjalali.planb.core.model.CalendarEvent(title = t("دیدار با خانواده", "Family visit"), date = day, startTime = java.time.LocalTime.of(11, 0), endTime = java.time.LocalTime.of(13, 0), allDay = false, color = AccentColor.PEACH))
        }
        click(s(AppR.string.nav_calendar))
        selectTab(s(CalendarR.string.calendar_view_month))
        waitFor(hasText(t("دیدار با خانواده", "Family visit"), substring = true))
        waitFor(hasText(s(CalendarR.string.calendar_lunar_note)))
        capture("calendar", "calendar_holidays_month")
        selectTab(s(CalendarR.string.calendar_view_day))
        waitFor(hasText(s(CalendarR.string.calendar_lunar_note)))
        capture("calendar", "calendar_holiday_day")
    }

    /** Today's time blocks, an unscheduled tray and two device calendar events (sync on, show only). */
    private suspend fun seedPlannedDay() {
        val today = fixtures.today
        val zone = clock.zone()
        fun at(hour: Int, minute: Int = 0) = today.atTime(hour, minute).atZone(zone).toInstant()
        tasks.save(Task(title = t("نوشتن طرح فصل ۳", "Outline chapter 3"), dueDate = today, estimatedMinutes = 90, scheduledStart = at(11), scheduledEnd = at(12, 30), priority = Priority.HIGH))
        tasks.save(Task(title = t("پاسخ به ایمیل‌ها", "Reply to emails"), dueDate = today, estimatedMinutes = 30))
        preferences.updateCalendarSync {
            it.copy(enabled = true, visibleCalendarIds = setOf(1, 2), targetCalendarId = null, lastSyncAt = clock.now().toEpochMilli())
        }
        deviceCalendars.addForeign(1, DeviceEventData(t("جلسهٔ برنامه‌ریزی اسپرینت", "Sprint planning"), "", at(14).toEpochMilli(), at(15).toEpochMilli(), false, zone.id))
        deviceCalendars.addForeign(2, DeviceEventData(t("دندان‌پزشکی", "Dentist"), "", at(17).toEpochMilli(), at(17, 45).toEpochMilli(), false, zone.id))
    }

    @Test
    fun calendarTimeBlocking() {
        launch(pro = true) { seedPlannedDay() }
        click(s(AppR.string.nav_calendar))
        selectTab(s(CalendarR.string.calendar_view_day))
        waitFor(hasText(s(CalendarR.string.calendar_unscheduled)))
        waitFor(hasText(t("دندان‌پزشکی", "Dentist"), substring = true))
        capture("calendar", "calendar_time_blocking")
    }

    @Test
    fun calendarTimeline() {
        launch(pro = true) { seedPlannedDay() }
        click(s(AppR.string.nav_calendar))
        selectTab(s(CalendarR.string.calendar_view_timeline))
        waitFor(hasText(s(CalendarR.string.calendar_now)))
        // Device events arrive in the same state as Plan-B's items (the timed ones may be below the fold).
        waitFor(hasText(t("پاسخ به ایمیل‌ها", "Reply to emails"), substring = true))
        capture("calendar", "calendar_timeline")
    }

    @Test
    fun calendarSyncSettings() {
        launch(pro = true) {
            preferences.updateCalendarSync {
                it.copy(enabled = true, visibleCalendarIds = setOf(1, 2), targetCalendarId = 1, lastSyncAt = clock.now().toEpochMilli())
            }
        }
        openMore(AppR.string.more_settings)
        click(s(SettingsR.string.settings_calendar_extras))
        waitFor(hasText(s(CalendarR.string.calendar_settings_holidays)))
        // The calendar list is below the fold at 150% font.
        if (variant.fontScale == 1f) waitFor(hasText("Family"))
        capture("calendar", "calendar_sync_settings")
    }

    // endregion

    // region Plan-B Pro smart day (#1, #5, #8), captured as a Pro user.

    @Test
    fun quickCaptureSmart() {
        launch(pro = true)
        clickDescription(s(AppR.string.quick_capture))
        val fa = variant.language == AppLanguage.PERSIAN
        waitFor(hasSetTextAction()).performTextInput(
            if (fa) "فردا ساعت ۵ عصر جلسه با تیم #کار فوری ۴۵ دقیقه" else "Team sync tomorrow 5pm #work !! for 45 min",
        )
        // The parsed parts appear as chips under the field.
        waitFor(hasText(if (fa) "#کار" else "#work"))
        capture("capture", "quick_capture_smart")
    }

    @Test
    fun planMyDay() {
        launch(pro = true)
        click(s(TodayR.string.today_plan_day))
        waitFor(hasText(s(TodayR.string.plan_preview_hint)))
        waitFor(hasText(fixtures.tasks[2].title))
        capture("today", "plan_my_day")
    }

    @Test
    fun morningRitual() {
        launch(pro = true)
        clickDescription(s(TodayR.string.today_rituals))
        click(s(TodayR.string.ritual_morning_sub))
        waitFor(hasText(s(TodayR.string.ritual_review_title)))
        waitFor(hasText(fixtures.tasks[3].title))
        capture("today", "ritual_morning")
    }

    @Test
    fun eveningRitual() {
        launch(pro = true)
        clickDescription(s(TodayR.string.today_rituals))
        click(s(TodayR.string.ritual_evening_sub))
        waitFor(hasText(s(TodayR.string.ritual_done_title)))
        waitFor(hasText(fixtures.tasks[4].title))
        capture("today", "ritual_evening")
    }

    @Test
    fun dayPlanSettings() {
        launch(pro = true) {
            settings.update { it.copy(dayPlan = it.dayPlan.copy(lunchEnabled = true, morningReminder = true)) }
        }
        openMore(AppR.string.more_settings)
        click(s(SettingsR.string.settings_day_planning))
        waitFor(hasText(s(TodayR.string.dayplan_hours_section)))
        waitFor(hasText(s(TodayR.string.dayplan_lunch)))
        capture("settings", "day_planning")
    }

    // endregion

    // region First run: the language screen, the welcome (end of its animation), the three slides (settled)

    @Test
    fun onboardingLanguage() {
        launch(seed = false, onboarded = false, languageChosen = false)
        // Bilingual: both languages are offered whatever the app language is.
        waitFor(hasTestTag("onboarding_language_fa"))
        waitFor(hasTestTag("onboarding_language_en"))
        capture("onboarding", "onboarding_language")
    }

    @Test
    fun onboardingWelcome() {
        launch(seed = false, onboarded = false)
        waitFor(hasText(s(AppR.string.onboarding_welcome_tagline)))
        waitFor(hasTestTag("onboarding_welcome_start") and isEnabled())
        capture("onboarding", "onboarding_welcome")
    }

    @Test
    fun onboardingSlides() {
        launch(seed = false, onboarded = false)
        click(s(AppR.string.onboarding_welcome_start))
        waitFor(hasText(s(AppR.string.onboarding_plan_title)))
        capture("onboarding", "onboarding_plan")
        click(s(AppR.string.onboarding_next))
        waitFor(hasText(s(AppR.string.onboarding_grow_title)))
        capture("onboarding", "onboarding_grow")
        click(s(AppR.string.onboarding_next))
        waitFor(hasText(s(AppR.string.onboarding_private_title)))
        waitFor(hasText(s(AppR.string.onboarding_start)))
        capture("onboarding", "onboarding_private")
    }

    // endregion

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = (Variant.STANDARD + Variant.LARGE_FONT).map { arrayOf(it) }
    }
}
