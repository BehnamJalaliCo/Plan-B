package com.behnamjalali.planb.navigation

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.behnamjalali.planb.core.data.repository.TemplateResult
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.model.SearchEntityType
import com.behnamjalali.planb.core.model.SearchResult
import com.behnamjalali.planb.core.model.TemplateType
import com.behnamjalali.planb.feature.calendar.CalendarDestination
import com.behnamjalali.planb.feature.calendar.CalendarRoute
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
import com.behnamjalali.planb.feature.notebooks.NoteEditorDestination
import com.behnamjalali.planb.feature.notebooks.NoteEditorRoute
import com.behnamjalali.planb.feature.notebooks.NotebookDetailDestination
import com.behnamjalali.planb.feature.notebooks.NotebookDetailRoute
import com.behnamjalali.planb.feature.notebooks.NotebooksDestination
import com.behnamjalali.planb.feature.notebooks.NotebooksRoute
import com.behnamjalali.planb.feature.projects.ProjectDetailDestination
import com.behnamjalali.planb.feature.projects.ProjectDetailRoute
import com.behnamjalali.planb.feature.projects.ProjectEditorDestination
import com.behnamjalali.planb.feature.projects.ProjectEditorRoute
import com.behnamjalali.planb.feature.projects.ProjectsDestination
import com.behnamjalali.planb.feature.projects.ProjectsRoute
import com.behnamjalali.planb.feature.review.ReviewDestination
import com.behnamjalali.planb.feature.review.ReviewRoute
import com.behnamjalali.planb.feature.search.SearchDestination
import com.behnamjalali.planb.feature.search.SearchRoute
import com.behnamjalali.planb.feature.settings.AboutDestination
import com.behnamjalali.planb.feature.settings.AboutRoute
import com.behnamjalali.planb.feature.settings.BackupDestination
import com.behnamjalali.planb.feature.settings.BackupRoute
import com.behnamjalali.planb.feature.settings.LicensesDestination
import com.behnamjalali.planb.feature.settings.LicensesRoute
import com.behnamjalali.planb.feature.settings.PrivacyDestination
import com.behnamjalali.planb.feature.settings.PrivacyRoute
import com.behnamjalali.planb.feature.settings.SettingsDestination
import com.behnamjalali.planb.feature.settings.SettingsRoute
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
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(
        navController = navController,
        startDestination = TodayRoute,
        enterTransition = { navEnter(motion.enabled) },
        exitTransition = { navExit(motion.enabled) },
        popEnterTransition = { navEnter(motion.enabled) },
        popExitTransition = { navExit(motion.enabled) },
    ) {
        composable<TodayRoute> {
            TodayDestination(
                actions = TodayActions(
                    onOpenTask = { navController.navigate(TaskEditorRoute(taskId = it)) },
                    onOpenEvent = { navController.navigate(EventEditorRoute(eventId = it)) },
                    onOpenHabit = { navController.navigate(HabitDetailRoute(it)) },
                    onOpenProject = { navController.navigate(ProjectDetailRoute(it)) },
                    onOpenNote = { navController.navigate(NoteEditorRoute(noteId = it)) },
                    onOpenFocus = { navController.navigate(FocusRoute) },
                    onOpenTasks = { navController.navigateTopLevel(TopLevelDestination.TASKS) },
                    onOpenCalendar = { navController.navigateTopLevel(TopLevelDestination.CALENDAR) },
                    onOpenHabits = { navController.navigate(HabitsRoute) },
                    onOpenProjects = { navController.navigate(ProjectsRoute) },
                    onOpenNotebooks = { navController.navigateTopLevel(TopLevelDestination.NOTEBOOKS) },
                    onOpenSearch = { navController.navigate(SearchRoute) },
                    onNewNote = { navController.navigate(NoteEditorRoute()) },
                ),
                snackbarHostState = snackbarHostState,
                contentPadding = contentPadding,
                openCustomizer = openTodayCustomizer,
                onCustomizerOpened = onTodayCustomizerOpened,
            )
        }
        composable<TasksRoute> {
            TasksDestination(
                onOpenTask = { navController.navigate(TaskEditorRoute(taskId = it)) },
                onNewTask = { navController.navigate(TaskEditorRoute()) },
                snackbarHostState = snackbarHostState,
                contentPadding = contentPadding,
            )
        }
        composable<CalendarRoute> {
            CalendarDestination(
                onOpenEvent = { navController.navigate(EventEditorRoute(eventId = it)) },
                onOpenTask = { navController.navigate(TaskEditorRoute(taskId = it)) },
                onNewEvent = { navController.navigate(EventEditorRoute(dateEpochDay = it.toEpochDay())) },
                snackbarHostState = snackbarHostState,
                contentPadding = contentPadding,
            )
        }
        composable<NotebooksRoute> {
            NotebooksDestination(
                onOpenNotebook = { navController.navigate(NotebookDetailRoute(it)) },
                onOpenNote = { navController.navigate(NoteEditorRoute(noteId = it)) },
                onNewNote = { navController.navigate(NoteEditorRoute()) },
                snackbarHostState = snackbarHostState,
                contentPadding = contentPadding,
            )
        }
        composable<MoreRoute> {
            MoreScreen(onNavigate = { navController.navigate(it) }, contentPadding = contentPadding)
        }
        composable<TaskEditorRoute> {
            TaskEditorDestination(onClose = back, onOpenSubtask = { navController.navigate(TaskEditorRoute(taskId = it)) })
        }
        composable<EventEditorRoute> { EventEditorDestination(onClose = back) }
        composable<NotebookDetailRoute> {
            NotebookDetailDestination(
                onBack = back,
                onOpenNote = { navController.navigate(NoteEditorRoute(noteId = it)) },
                onNewNote = { notebook, section -> navController.navigate(NoteEditorRoute(notebookId = notebook, sectionId = section)) },
                snackbarHostState = snackbarHostState,
            )
        }
        composable<NoteEditorRoute> {
            NoteEditorDestination(onClose = back, onOpenNote = {
                navController.navigate(NoteEditorRoute(noteId = it)) { popUpTo<NoteEditorRoute> { inclusive = true } }
            })
        }
        composable<ProjectsRoute> {
            ProjectsDestination(
                onBack = back,
                onOpenProject = { navController.navigate(ProjectDetailRoute(it)) },
                onNewProject = { navController.navigate(ProjectEditorRoute()) },
            )
        }
        composable<ProjectDetailRoute> {
            ProjectDetailDestination(
                onBack = back,
                onEdit = { navController.navigate(ProjectEditorRoute(it)) },
                onOpenTask = { navController.navigate(TaskEditorRoute(taskId = it)) },
                snackbarHostState = snackbarHostState,
            )
        }
        composable<ProjectEditorRoute> {
            ProjectEditorDestination(onClose = back, onSaved = { id, isNew ->
                navController.popBackStack()
                if (isNew) navController.navigate(ProjectDetailRoute(id))
            })
        }
        composable<HabitsRoute> {
            HabitsDestination(
                onBack = back,
                onOpenHabit = { navController.navigate(HabitDetailRoute(it)) },
                onNewHabit = { navController.navigate(HabitEditorRoute()) },
                snackbarHostState = snackbarHostState,
            )
        }
        composable<HabitDetailRoute> {
            HabitDetailDestination(onBack = back, onEdit = { navController.navigate(HabitEditorRoute(it)) }, snackbarHostState = snackbarHostState)
        }
        composable<HabitEditorRoute> { HabitEditorDestination(onClose = back) }
        composable<GoalsRoute> {
            GoalsDestination(
                onBack = back,
                onOpenGoal = { navController.navigate(GoalDetailRoute(it)) },
                onNewGoal = { navController.navigate(GoalEditorRoute()) },
            )
        }
        composable<GoalDetailRoute> {
            GoalDetailDestination(onBack = back, onEdit = { navController.navigate(GoalEditorRoute(it)) }, snackbarHostState = snackbarHostState)
        }
        composable<GoalEditorRoute> { GoalEditorDestination(onClose = back) }
        composable<FocusRoute> { FocusDestination(onBack = back, snackbarHostState = snackbarHostState) }
        composable<SearchRoute> { SearchDestination(onBack = back, onOpen = { navController.openSearchResult(it) }) }
        composable<TemplatesRoute> {
            TemplatesDestination(onBack = back, onOpenResult = { navController.openTemplateResult(it) }, snackbarHostState = snackbarHostState)
        }
        composable<ReviewRoute> { ReviewDestination(onBack = back, onOpenTask = { navController.navigate(TaskEditorRoute(taskId = it)) }) }
        composable<SettingsRoute> {
            SettingsDestination(onBack = back, onOpen = { navController.navigate(it) }, onCustomizeToday = onCustomizeToday)
        }
        composable<BackupRoute> { BackupDestination(onBack = back, snackbarHostState = snackbarHostState) }
        composable<PrivacyRoute> { PrivacyDestination(onBack = back) }
        composable<AboutRoute> { AboutDestination(onBack = back) }
        composable<LicensesRoute> { LicensesDestination(onBack = back) }
    }
}

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

/** Handles `planb://open/<type>/<id>` links from notifications. */
fun NavHostController.handleDeepLink(uri: Uri) {
    if (uri.scheme != "planb") return
    val segments = uri.pathSegments
    val id = segments.getOrNull(1)?.toLongOrNull()
    when (segments.firstOrNull()) {
        "task" -> id?.let { navigate(TaskEditorRoute(taskId = it)) }
        "event" -> id?.let { navigate(EventEditorRoute(eventId = it)) }
        "habit" -> id?.let { navigate(HabitDetailRoute(it)) }
        "focus" -> navigate(FocusRoute)
    }
}
