package com.behnamjalali.planb.navigation

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.behnamjalali.planb.core.data.repository.TemplateResult
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.model.ActivityEntityType
import com.behnamjalali.planb.core.model.SearchEntityType
import com.behnamjalali.planb.core.model.SearchResult
import com.behnamjalali.planb.core.model.TemplateType
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.feature.assistant.AiSettingsDestination
import com.behnamjalali.planb.feature.assistant.AiSettingsRoute
import com.behnamjalali.planb.feature.assistant.AssistantDestination
import com.behnamjalali.planb.feature.assistant.AssistantRoute
import com.behnamjalali.planb.feature.calendar.CalendarDestination
import com.behnamjalali.planb.feature.calendar.CalendarRoute
import com.behnamjalali.planb.feature.calendar.CalendarSettingsDestination
import com.behnamjalali.planb.feature.calendar.CalendarSettingsRoute
import com.behnamjalali.planb.feature.calendar.EventEditorDestination
import com.behnamjalali.planb.feature.calendar.EventEditorRoute
import com.behnamjalali.planb.feature.focus.FocusDestination
import com.behnamjalali.planb.feature.focus.FocusRoute
import com.behnamjalali.planb.feature.goals.GoalDetailDestination
import com.behnamjalali.planb.feature.goals.GoalDetailRoute
import com.behnamjalali.planb.feature.goals.GoalEditorDestination
import com.behnamjalali.planb.feature.goals.GoalEditorRoute
import com.behnamjalali.planb.feature.goals.GoalsDestination
import com.behnamjalali.planb.feature.goals.GoalsRoute
import com.behnamjalali.planb.feature.habits.HabitDetailDestination
import com.behnamjalali.planb.feature.habits.HabitDetailRoute
import com.behnamjalali.planb.feature.habits.HabitEditorDestination
import com.behnamjalali.planb.feature.habits.HabitEditorRoute
import com.behnamjalali.planb.feature.habits.HabitsDestination
import com.behnamjalali.planb.feature.habits.HabitsRoute
import com.behnamjalali.planb.feature.habits.ChallengesDestination
import com.behnamjalali.planb.feature.habits.ChallengesRoute
import com.behnamjalali.planb.feature.habits.HabitStatsDestination
import com.behnamjalali.planb.feature.habits.HabitStatsRoute
import com.behnamjalali.planb.feature.notebooks.NoteEditorDestination
import com.behnamjalali.planb.feature.notebooks.NoteEditorRoute
import com.behnamjalali.planb.feature.notebooks.NotebookDetailDestination
import com.behnamjalali.planb.feature.notebooks.NotebookDetailRoute
import com.behnamjalali.planb.feature.notebooks.NotebooksDestination
import com.behnamjalali.planb.feature.notebooks.NotebooksRoute
import com.behnamjalali.planb.feature.notebooks.graph.NoteGraphDestination
import com.behnamjalali.planb.feature.notebooks.graph.NoteGraphRoute
import com.behnamjalali.planb.feature.notebooks.history.NoteHistoryDestination
import com.behnamjalali.planb.feature.notebooks.history.NoteHistoryRoute
import com.behnamjalali.planb.feature.journal.JournalDestination
import com.behnamjalali.planb.feature.journal.JournalRoute
import com.behnamjalali.planb.feature.journal.MoodCalendarDestination
import com.behnamjalali.planb.feature.journal.MoodCalendarRoute
import com.behnamjalali.planb.feature.journal.MoodTrackerDestination
import com.behnamjalali.planb.feature.journal.MoodTrackerRoute
import com.behnamjalali.planb.feature.pro.PaywallDestination
import com.behnamjalali.planb.feature.pro.PaywallRoute
import com.behnamjalali.planb.feature.projects.ProjectDetailDestination
import com.behnamjalali.planb.feature.projects.ProjectDetailRoute
import com.behnamjalali.planb.feature.projects.ProjectEditorDestination
import com.behnamjalali.planb.feature.projects.ProjectEditorRoute
import com.behnamjalali.planb.feature.projects.ProjectsDestination
import com.behnamjalali.planb.feature.projects.ProjectsRoute
import com.behnamjalali.planb.feature.reports.StatisticsDestination
import com.behnamjalali.planb.feature.reports.StatisticsRoute
import com.behnamjalali.planb.feature.reports.YearReportDestination
import com.behnamjalali.planb.feature.reports.YearReportRoute
import com.behnamjalali.planb.feature.review.ReviewDestination
import com.behnamjalali.planb.feature.review.ReviewRoute
import com.behnamjalali.planb.feature.search.SearchDestination
import com.behnamjalali.planb.feature.search.SearchRoute
import com.behnamjalali.planb.feature.security.ActivityDestination
import com.behnamjalali.planb.feature.security.ActivityRoute
import com.behnamjalali.planb.feature.security.SecurityDestination
import com.behnamjalali.planb.feature.security.SecurityRoute
import com.behnamjalali.planb.feature.security.TrashDestination
import com.behnamjalali.planb.feature.security.TrashRoute
import com.behnamjalali.planb.feature.settings.AboutDestination
import com.behnamjalali.planb.feature.settings.AboutRoute
import com.behnamjalali.planb.feature.settings.AppearanceDestination
import com.behnamjalali.planb.feature.settings.AppearanceRoute
import com.behnamjalali.planb.feature.settings.BackupDestination
import com.behnamjalali.planb.feature.settings.BackupRoute
import com.behnamjalali.planb.feature.settings.LicensesDestination
import com.behnamjalali.planb.feature.settings.LicensesRoute
import com.behnamjalali.planb.feature.settings.PrivacyDestination
import com.behnamjalali.planb.feature.settings.PrivacyRoute
import com.behnamjalali.planb.feature.settings.SettingsDestination
import com.behnamjalali.planb.feature.settings.SettingsRoute
import com.behnamjalali.planb.feature.tasks.EisenhowerDestination
import com.behnamjalali.planb.feature.tasks.EisenhowerRoute
import com.behnamjalali.planb.feature.tasks.SmartListEditorDestination
import com.behnamjalali.planb.feature.tasks.SmartListEditorRoute
import com.behnamjalali.planb.feature.tasks.SmartListsDestination
import com.behnamjalali.planb.feature.tasks.SmartListsRoute
import com.behnamjalali.planb.feature.tasks.TaskEditorDestination
import com.behnamjalali.planb.feature.tasks.TaskEditorRoute
import com.behnamjalali.planb.feature.tasks.TasksDestination
import com.behnamjalali.planb.feature.tasks.TasksRoute
import com.behnamjalali.planb.feature.templates.TemplatesDestination
import com.behnamjalali.planb.feature.templates.TemplatesRoute
import com.behnamjalali.planb.feature.today.TodayActions
import com.behnamjalali.planb.feature.today.TodayDestination
import com.behnamjalali.planb.feature.today.TodayRoute
import com.behnamjalali.planb.feature.today.capture.CaptureType
import com.behnamjalali.planb.feature.today.DayPlanSettingsRoute
import com.behnamjalali.planb.feature.today.RitualRoute
import com.behnamjalali.planb.feature.today.plan.DayPlanSettingsDestination
import com.behnamjalali.planb.feature.today.ritual.RitualDestination
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.behnamjalali.planb.ui.MoreScreen

@Composable
fun PlanBNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    openTodayCustomizer: Boolean,
    onTodayCustomizerOpened: () -> Unit,
    onCustomizeToday: () -> Unit,
) {
    val motion = PlanBTheme.motion
    NavHost(
        navController = navController,
        startDestination = TodayRoute,
        enterTransition = { navEnter(motion.enabled) },
        exitTransition = { navExit(motion.enabled) },
        popEnterTransition = { navEnter(motion.enabled) },
        popExitTransition = { navExit(motion.enabled) },
    ) {
        composable<TodayRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            TodayDestination(
                actions = TodayActions(
                    onOpenTask = { nav.navigate(TaskEditorRoute(taskId = it)) },
                    onOpenEvent = { nav.navigate(EventEditorRoute(eventId = it)) },
                    onOpenHabit = { nav.navigate(HabitDetailRoute(it)) },
                    onOpenProject = { nav.navigate(ProjectDetailRoute(it)) },
                    onOpenNote = { nav.navigate(NoteEditorRoute(noteId = it)) },
                    onOpenFocus = { nav.navigate(FocusRoute) },
                    onOpenTasks = { nav.go { navigateTopLevel(TopLevelDestination.TASKS) } },
                    onOpenCalendar = { nav.go { navigateTopLevel(TopLevelDestination.CALENDAR) } },
                    onOpenHabits = { nav.navigate(HabitsRoute) },
                    onOpenProjects = { nav.navigate(ProjectsRoute) },
                    onOpenNotebooks = { nav.go { navigateTopLevel(TopLevelDestination.NOTEBOOKS) } },
                    onOpenSearch = { nav.navigate(SearchRoute) },
                    onNewNote = { nav.navigate(NoteEditorRoute()) },
                    onOpenRitual = { nav.navigate(RitualRoute(it.key)) },
                    onOpenDayPlanSettings = { nav.navigate(DayPlanSettingsRoute) },
                    onMoodCheckIn = { nav.navigate(MoodTrackerRoute(checkIn = true, mood = it)) },
                ),
                snackbarHostState = snackbarHostState,
                contentPadding = contentPadding,
                openCustomizer = openTodayCustomizer,
                onCustomizerOpened = onTodayCustomizerOpened,
            )
        }
        composable<TasksRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            TasksDestination(
                onOpenTask = { nav.navigate(TaskEditorRoute(taskId = it)) },
                onNewTask = { nav.navigate(TaskEditorRoute()) },
                snackbarHostState = snackbarHostState,
                contentPadding = contentPadding,
                onEditSmartList = { nav.navigate(SmartListEditorRoute(it ?: 0)) },
                onManageSmartLists = { nav.navigate(SmartListsRoute) },
                onOpenEisenhower = { nav.navigate(EisenhowerRoute) },
            )
        }
        composable<CalendarRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            CalendarDestination(
                onOpenEvent = { nav.navigate(EventEditorRoute(eventId = it)) },
                onOpenTask = { nav.navigate(TaskEditorRoute(taskId = it)) },
                onNewEvent = { nav.navigate(EventEditorRoute(dateEpochDay = it.toEpochDay())) },
                snackbarHostState = snackbarHostState,
                contentPadding = contentPadding,
            )
        }
        composable<NotebooksRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            NotebooksDestination(
                onOpenNotebook = { nav.navigate(NotebookDetailRoute(it)) },
                onOpenNote = { nav.navigate(NoteEditorRoute(noteId = it)) },
                onNewNote = { nav.navigate(NoteEditorRoute()) },
                snackbarHostState = snackbarHostState,
                contentPadding = contentPadding,
                onOpenJournal = { nav.navigate(JournalRoute) },
                onOpenGraph = { nav.navigate(NoteGraphRoute) },
            )
        }
        composable<MoreRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            MoreScreen(onNavigate = { nav.navigate(it) }, contentPadding = contentPadding)
        }
        composable<TaskEditorRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            TaskEditorDestination(
                onClose = nav.back,
                onOpenSubtask = { nav.navigate(TaskEditorRoute(taskId = it)) },
                onOpenActivity = { nav.navigate(ActivityRoute(ActivityEntityType.TASK.name, it)) },
            )
        }
        composable<EventEditorRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            EventEditorDestination(onClose = nav.back)
        }
        composable<NotebookDetailRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            NotebookDetailDestination(
                onBack = nav.back,
                onOpenNote = { nav.navigate(NoteEditorRoute(noteId = it)) },
                onNewNote = { notebook, section -> nav.navigate(NoteEditorRoute(notebookId = notebook, sectionId = section)) },
                snackbarHostState = snackbarHostState,
            )
        }
        composable<NoteEditorRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            NoteEditorDestination(
                onClose = nav.back,
                onOpenNote = { nav.go { navigate(NoteEditorRoute(noteId = it)) { popUpTo<NoteEditorRoute> { inclusive = true } } } },
                onOpenActivity = { nav.navigate(ActivityRoute(ActivityEntityType.NOTE.name, it)) },
                onOpenLinkedNote = { nav.navigate(NoteEditorRoute(noteId = it)) },
                onOpenHistory = { nav.navigate(NoteHistoryRoute(it)) },
            )
        }
        composable<ProjectsRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            ProjectsDestination(
                onBack = nav.back,
                onOpenProject = { nav.navigate(ProjectDetailRoute(it)) },
                onNewProject = { nav.navigate(ProjectEditorRoute()) },
            )
        }
        composable<ProjectDetailRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            ProjectDetailDestination(
                onBack = nav.back,
                onEdit = { nav.navigate(ProjectEditorRoute(it)) },
                onOpenTask = { nav.navigate(TaskEditorRoute(taskId = it)) },
                snackbarHostState = snackbarHostState,
            )
        }
        composable<ProjectEditorRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            ProjectEditorDestination(onClose = nav.back, onSaved = { id, isNew ->
                nav.go {
                    popBackStack()
                    if (isNew) navigate(ProjectDetailRoute(id))
                }
            })
        }
        composable<HabitsRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            HabitsDestination(
                onBack = nav.back,
                onOpenHabit = { nav.navigate(HabitDetailRoute(it)) },
                onNewHabit = { nav.navigate(HabitEditorRoute()) },
                snackbarHostState = snackbarHostState,
                onOpenChallenges = { nav.navigate(ChallengesRoute()) },
                onOpenMood = { nav.navigate(MoodTrackerRoute()) },
            )
        }
        composable<HabitDetailRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            HabitDetailDestination(
                onBack = nav.back,
                onEdit = { nav.navigate(HabitEditorRoute(it)) },
                snackbarHostState = snackbarHostState,
                onOpenStats = { nav.navigate(HabitStatsRoute(it)) },
                onOpenChallenges = { nav.navigate(ChallengesRoute()) },
            )
        }
        // Plan-B Pro habits and focus (#28–#30).
        composable<HabitStatsRoute> { entry -> HabitStatsDestination(onBack = rememberScreenNavigator(navController, entry).back) }
        composable<ChallengesRoute> { entry ->
            ChallengesDestination(onBack = rememberScreenNavigator(navController, entry).back, snackbarHostState = snackbarHostState)
        }
        composable<MoodTrackerRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            MoodTrackerDestination(onBack = nav.back, onOpenCalendar = { nav.navigate(MoodCalendarRoute) }, snackbarHostState = snackbarHostState)
        }
        composable<HabitEditorRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            HabitEditorDestination(onClose = nav.back)
        }
        composable<GoalsRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            GoalsDestination(
                onBack = nav.back,
                onOpenGoal = { nav.navigate(GoalDetailRoute(it)) },
                onNewGoal = { nav.navigate(GoalEditorRoute()) },
            )
        }
        composable<GoalDetailRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            GoalDetailDestination(onBack = nav.back, onEdit = { nav.navigate(GoalEditorRoute(it)) }, snackbarHostState = snackbarHostState)
        }
        composable<GoalEditorRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            GoalEditorDestination(onClose = nav.back)
        }
        composable<FocusRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            FocusDestination(onBack = nav.back, snackbarHostState = snackbarHostState)
        }
        composable<SearchRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            SearchDestination(onBack = nav.back, onOpen = { result -> nav.go { openSearchResult(result) } })
        }
        composable<TemplatesRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            TemplatesDestination(onBack = nav.back, onOpenResult = { result -> nav.go { openTemplateResult(result) } }, snackbarHostState = snackbarHostState)
        }
        composable<ReviewRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            ReviewDestination(onBack = nav.back, onOpenTask = { nav.navigate(TaskEditorRoute(taskId = it)) })
        }
        composable<SettingsRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            SettingsDestination(
                onBack = nav.back,
                onOpen = { nav.navigate(it) },
                onOpenPro = { nav.navigate(PaywallRoute()) },
                onCustomizeToday = onCustomizeToday,
                onOpenSecurity = { nav.navigate(SecurityRoute) },
                onOpenCalendarSettings = { nav.navigate(CalendarSettingsRoute) },
                onOpenDayPlanning = { nav.navigate(DayPlanSettingsRoute) },
                onOpenAssistant = { nav.navigate(AiSettingsRoute) },
            )
        }
        composable<PaywallRoute> { entry -> PaywallDestination(onBack = rememberScreenNavigator(navController, entry).back) }
        composable<BackupRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            BackupDestination(onBack = nav.back, snackbarHostState = snackbarHostState)
        }
        composable<PrivacyRoute> { entry -> PrivacyDestination(onBack = rememberScreenNavigator(navController, entry).back) }
        composable<AboutRoute> { entry -> AboutDestination(onBack = rememberScreenNavigator(navController, entry).back) }
        composable<LicensesRoute> { entry -> LicensesDestination(onBack = rememberScreenNavigator(navController, entry).back) }
        // Plan-B Pro security and data (#36–#38).
        composable<SecurityRoute> { entry -> SecurityDestination(onBack = rememberScreenNavigator(navController, entry).back, snackbarHostState = snackbarHostState) }
        composable<TrashRoute> { entry -> TrashDestination(onBack = rememberScreenNavigator(navController, entry).back, snackbarHostState = snackbarHostState) }
        composable<ActivityRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            ActivityDestination(onBack = nav.back, onOpen = { type, id -> nav.go { openActivityItem(type, id) } })
        }
        // Plan-B Pro: reports and personalization (WP5).
        composable<StatisticsRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            StatisticsDestination(onBack = nav.back, onOpenYear = { nav.navigate(YearReportRoute()) }, snackbarHostState = snackbarHostState)
        }
        composable<YearReportRoute> { entry ->
            YearReportDestination(onBack = rememberScreenNavigator(navController, entry).back, snackbarHostState = snackbarHostState)
        }
        composable<AppearanceRoute> { entry -> AppearanceDestination(onBack = rememberScreenNavigator(navController, entry).back) }
        // Plan-B Pro planning (WP1): smart lists (#10) and the Eisenhower matrix (#13).
        composable<SmartListEditorRoute> { entry -> SmartListEditorDestination(onClose = rememberScreenNavigator(navController, entry).back) }
        composable<SmartListsRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            SmartListsDestination(onBack = nav.back, onEdit = { nav.navigate(SmartListEditorRoute(it ?: 0)) })
        }
        // Plan-B Pro calendar (WP2a): holidays and device calendar sync settings (#2, #3).
        composable<CalendarSettingsRoute> { entry -> CalendarSettingsDestination(onBack = rememberScreenNavigator(navController, entry).back) }
        // Plan-B Pro smart day (WP2b): rituals (#8) and working hours for day planning (#5).
        composable<RitualRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            val scope = rememberCoroutineScope()
            RitualDestination(
                onClose = nav.back,
                onOpenWorkingHours = { nav.navigate(DayPlanSettingsRoute) },
                onMessage = { scope.launch { snackbarHostState.showSnackbar(it) } },
            )
        }
        composable<DayPlanSettingsRoute> { entry -> DayPlanSettingsDestination(onBack = rememberScreenNavigator(navController, entry).back) }
        // Plan-B Pro notes knowledge (WP3b): history (#16), graph (#21), journal and mood calendar (#25).
        composable<NoteHistoryRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            NoteHistoryDestination(
                onBack = nav.back,
                // The editor below reloads the restored text: replace it with a fresh one.
                onRestored = { id -> nav.go { navigate(NoteEditorRoute(noteId = id)) { popUpTo<NoteEditorRoute> { inclusive = true } } } },
                snackbarHostState = snackbarHostState,
            )
        }
        composable<NoteGraphRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            NoteGraphDestination(onBack = nav.back, onOpenNote = { nav.navigate(NoteEditorRoute(noteId = it)) })
        }
        composable<JournalRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            JournalDestination(
                onBack = nav.back,
                onOpenNote = { nav.navigate(NoteEditorRoute(noteId = it)) },
                onOpenCalendar = { nav.navigate(MoodCalendarRoute) },
                snackbarHostState = snackbarHostState,
            )
        }
        composable<MoodCalendarRoute> { entry -> MoodCalendarDestination(onBack = rememberScreenNavigator(navController, entry).back) }
        // Plan-B Pro AI (#39): the assistant and its settings.
        composable<AssistantRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            AssistantDestination(onBack = nav.back, onOpenSettings = { nav.navigate(AiSettingsRoute) }, snackbarHostState = snackbarHostState)
        }
        composable<AiSettingsRoute> { entry -> AiSettingsDestination(onBack = rememberScreenNavigator(navController, entry).back) }
        composable<EisenhowerRoute> { entry ->
            val nav = rememberScreenNavigator(navController, entry)
            EisenhowerDestination(onBack = nav.back, onOpenTask = { nav.navigate(TaskEditorRoute(taskId = it)) }, snackbarHostState = snackbarHostState)
        }
    }
}

/**
 * Navigation started from one screen ([entry]). Every action runs only while that screen is
 * the current destination: a fast double tap cannot push the same screen twice, and a double
 * back cannot pop the screen below it. Back never pops the start destination, which would
 * leave an empty window. Feature screens only get these callbacks, so the guard lives here.
 */
@Stable
class ScreenNavigator(private val navController: NavHostController, private val entry: NavBackStackEntry) {
    private val isCurrent: Boolean get() = navController.currentBackStackEntry?.id == entry.id

    fun go(action: NavHostController.() -> Unit) {
        if (isCurrent) navController.action()
    }

    fun navigate(route: Any) = go { navigate(route) }

    val back: () -> Unit = {
        if (isCurrent && navController.previousBackStackEntry != null) navController.popBackStack()
    }
}

@Composable
private fun rememberScreenNavigator(navController: NavHostController, entry: NavBackStackEntry): ScreenNavigator =
    remember(navController, entry) { ScreenNavigator(navController, entry) }

fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.startId()) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavGraph.startId(): Int = findStartDestination().id

/** Opens the item that was just created from Quick Capture. */
fun NavHostController.openCaptured(type: CaptureType, id: Long) {
    when (type) {
        CaptureType.TASK -> navigate(TaskEditorRoute(taskId = id))
        CaptureType.EVENT -> navigate(EventEditorRoute(eventId = id))
        CaptureType.NOTE -> navigate(NoteEditorRoute(noteId = id))
        CaptureType.HABIT -> navigate(HabitDetailRoute(id))
        CaptureType.PROJECT -> navigate(ProjectDetailRoute(id))
    }
}

fun NavHostController.openSearchResult(result: SearchResult) {
    when (result.type) {
        SearchEntityType.TASK -> navigate(TaskEditorRoute(taskId = result.id))
        SearchEntityType.PROJECT -> navigate(ProjectDetailRoute(result.id))
        SearchEntityType.NOTE -> navigate(NoteEditorRoute(noteId = result.id))
        SearchEntityType.NOTEBOOK -> navigate(NotebookDetailRoute(result.id))
        SearchEntityType.HABIT -> navigate(HabitDetailRoute(result.id))
        SearchEntityType.GOAL -> navigate(GoalDetailRoute(result.id))
        SearchEntityType.EVENT -> navigate(EventEditorRoute(eventId = result.id))
    }
}

fun NavHostController.openTemplateResult(result: TemplateResult) {
    when (result.type) {
        TemplateType.NOTE -> navigate(NoteEditorRoute(noteId = result.id))
        TemplateType.PROJECT -> navigate(ProjectDetailRoute(result.id))
        TemplateType.TASKS -> navigateTopLevel(TopLevelDestination.TASKS)
        TemplateType.HABITS -> navigate(HabitsRoute)
    }
}

/** Opens the item of an activity entry (a deleted item shows its editor's "not found"). */
fun NavHostController.openActivityItem(type: ActivityEntityType, id: Long) {
    when (type) {
        ActivityEntityType.TASK -> navigate(TaskEditorRoute(taskId = id))
        ActivityEntityType.PROJECT -> navigate(ProjectDetailRoute(id))
        ActivityEntityType.NOTE -> navigate(NoteEditorRoute(noteId = id))
        ActivityEntityType.NOTEBOOK -> navigate(NotebookDetailRoute(id))
        ActivityEntityType.HABIT -> navigate(HabitDetailRoute(id))
        ActivityEntityType.GOAL -> navigate(GoalDetailRoute(id))
        ActivityEntityType.EVENT -> navigate(EventEditorRoute(eventId = id))
    }
}

/** Handles `planb://open/<type>/<id>` links from notifications. */
fun NavHostController.handleDeepLink(uri: Uri) {
    // Only our own notification links (planb://open/...) are honoured.
    if (uri.scheme != "planb" || uri.host != "open") return
    val segments = uri.pathSegments
    val id = segments.getOrNull(1)?.toLongOrNull()
    when (segments.firstOrNull()) {
        "task" -> id?.let { navigate(TaskEditorRoute(taskId = it)) }
        "event" -> id?.let { navigate(EventEditorRoute(eventId = it)) }
        "habit" -> id?.let { navigate(HabitDetailRoute(it)) }
        // A note saved with the web clipper (Plan-B Pro #22) and the journal reminder (#25).
        "note" -> id?.let { navigate(NoteEditorRoute(noteId = it)) }
        "journal" -> navigate(JournalRoute)
        "focus" -> navigate(FocusRoute)
        // Ritual reminders (Plan-B Pro #8): planb://open/ritual/morning|evening.
        "ritual" -> segments.getOrNull(1)?.takeIf { it == "morning" || it == "evening" }?.let { navigate(RitualRoute(it)) }
        // Widgets, quick-settings tiles and launcher shortcuts (Plan-B Pro #32, #34).
        "new-task" -> navigate(TaskEditorRoute())
        "new-note" -> navigate(NoteEditorRoute())
        "today" -> navigateTopLevel(TopLevelDestination.TODAY)
        "calendar" -> navigateTopLevel(TopLevelDestination.CALENDAR)
        "habits" -> navigate(HabitsRoute)
        // Plan-B Pro #29 and #30.
        "badges" -> navigate(ChallengesRoute(badges = true))
        "mood" -> navigate(MoodTrackerRoute(checkIn = segments.getOrNull(1) == "check-in"))
        "pro" -> navigate(PaywallRoute(ProFeature.entries.firstOrNull { it.id == segments.getOrNull(1) }?.id))
    }
}
