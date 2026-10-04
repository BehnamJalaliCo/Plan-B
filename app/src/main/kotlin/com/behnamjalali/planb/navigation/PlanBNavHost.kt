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
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.feature.calendar.CalendarRoute
import com.behnamjalali.planb.feature.calendar.EventEditorRoute
import com.behnamjalali.planb.feature.tasks.TaskEditorRoute
import com.behnamjalali.planb.feature.tasks.TasksRoute
import com.behnamjalali.planb.feature.today.TodayActions
import com.behnamjalali.planb.feature.today.TodayRoute
import com.behnamjalali.planb.feature.today.capture.CaptureType
import com.behnamjalali.planb.ui.MoreScreen
import com.behnamjalali.planb.feature.calendar.CalendarDestination
import com.behnamjalali.planb.feature.calendar.EventEditorDestination
import com.behnamjalali.planb.feature.tasks.TaskEditorDestination
import com.behnamjalali.planb.feature.tasks.TasksDestination
import com.behnamjalali.planb.feature.today.TodayDestination

@Composable
fun PlanBNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    showOnboarding: Boolean,
    onOnboardingDone: () -> Unit,
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
        composable<TodayRoute> {
            TodayDestination(
                actions = TodayActions(
                    onOpenTask = { navController.navigate(TaskEditorRoute(taskId = it)) },
                    onOpenEvent = { navController.navigate(EventEditorRoute(eventId = it)) },
                    onOpenTasks = { navController.navigateTopLevel(TopLevelDestination.TASKS) },
                    onOpenCalendar = { navController.navigateTopLevel(TopLevelDestination.CALENDAR) },
                ),
                snackbarHostState = snackbarHostState,
                contentPadding = contentPadding,
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
        composable<TaskEditorRoute> {
            TaskEditorDestination(
                onClose = { navController.popBackStack() },
                onOpenSubtask = { navController.navigate(TaskEditorRoute(taskId = it)) },
            )
        }
        composable<EventEditorRoute> {
            EventEditorDestination(onClose = { navController.popBackStack() })
        }
        composable<MoreRoute> {
            MoreScreen(onNavigate = { navController.navigate(it) }, contentPadding = contentPadding)
        }
    }
}

fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestinationId()) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavGraph.findStartDestinationId(): Int = findStartDestination().id

/** Opens the item that was just created from Quick Capture. */
fun NavHostController.openCaptured(type: CaptureType, id: Long) {
    when (type) {
        CaptureType.TASK -> navigate(TaskEditorRoute(taskId = id))
        CaptureType.EVENT -> navigate(EventEditorRoute(eventId = id))
        else -> Unit
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
    }
}
