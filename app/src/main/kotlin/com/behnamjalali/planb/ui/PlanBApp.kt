package com.behnamjalali.planb.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.CompositionLocalProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.ProStatusViewModel
import com.behnamjalali.planb.R
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.core.ui.ProAccess
import com.behnamjalali.planb.feature.pro.PaywallRoute
import com.behnamjalali.planb.core.designsystem.component.PlannerFAB
import com.behnamjalali.planb.core.designsystem.component.PlannerNavItem
import com.behnamjalali.planb.core.designsystem.component.PlannerNavigationBar
import com.behnamjalali.planb.core.model.UserSettings
import com.behnamjalali.planb.feature.today.capture.CaptureType
import com.behnamjalali.planb.feature.today.capture.QuickCaptureSheet
import com.behnamjalali.planb.feature.today.capture.captureSavedMessage
import com.behnamjalali.planb.navigation.PlanBNavHost
import com.behnamjalali.planb.ui.onboarding.OnboardingHost
import com.behnamjalali.planb.ui.onboarding.rememberFadeIn
import com.behnamjalali.planb.navigation.TopLevelDestination
import com.behnamjalali.planb.navigation.handleDeepLink
import com.behnamjalali.planb.navigation.navigateTopLevel
import com.behnamjalali.planb.navigation.openCaptured
import kotlinx.coroutines.launch

@Composable
fun PlanBApp(
    settings: UserSettings,
    pendingLink: Uri?,
    onLinkHandled: () -> Unit,
    onOnboardingDone: () -> Unit,
    navController: NavHostController = rememberNavController(),
    proStatus: ProStatusViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var capturing by rememberSaveable { mutableStateOf(false) }
    var customizeToday by rememberSaveable { mutableStateOf(false) }
    // After the first-run flow, the app fades in instead of cutting in.
    val afterOnboarding = remember { !settings.onboardingCompleted }
    if (!settings.onboardingCompleted) {
        OnboardingHost(settings, onFinished = onOnboardingDone)
        return
    }
    val appAlpha = rememberFadeIn(active = afterOnboarding)
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val current = TopLevelDestination.entries.firstOrNull { top -> destination?.hasRoute(top.route::class) == true }

    LaunchedEffect(pendingLink) {
        if (pendingLink != null) {
            // Quick add from a widget, tile or shortcut opens Quick Capture over the current screen.
            if (pendingLink.scheme == "planb" && pendingLink.host == "open" && pendingLink.pathSegments.firstOrNull() == "capture") {
                capturing = true
            } else {
                navController.handleDeepLink(pendingLink)
            }
            onLinkHandled()
        }
    }

    val isPro by proStatus.isPro.collectAsStateWithLifecycle()
    // Feature screens gate Pro actions through this; the Pro screen opens only on the user's tap.
    val proAccess = remember(isPro, navController) {
        ProAccess(isPro = isPro, openPaywall = { feature -> navController.navigate(PaywallRoute(feature?.id)) })
    }
    CompositionLocalProvider(LocalProAccess provides proAccess) {
        Scaffold(
            // Test tags double as resource ids so UiAutomator (benchmarks, baseline profiles) can find them.
            modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }
                .graphicsLayer { alpha = appAlpha.value },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                AnimatedVisibility(current != null && current.showsCapture, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                    PlannerFAB(Icons.Rounded.Add, stringResource(R.string.quick_capture), { capturing = true }, Modifier.testTag("quick_capture"))
                }
            },
            bottomBar = {
                if (current != null) {
                    PlannerNavigationBar(
                        items = TopLevelDestination.entries.map {
                            PlannerNavItem(stringResource(it.label), it.icon, it.selectedIcon, testTag = "nav_${it.name.lowercase()}")
                        },
                        selectedIndex = current.ordinal,
                        onSelect = { index ->
                            val target = TopLevelDestination.entries[index]
                            navController.navigate(target.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { padding ->
            PlanBNavHost(
                navController = navController,
                snackbarHostState = snackbar,
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                openTodayCustomizer = customizeToday,
                onTodayCustomizerOpened = { customizeToday = false },
                onCustomizeToday = {
                    customizeToday = true
                    navController.navigateTopLevel(TopLevelDestination.TODAY)
                },
            )
        }

        if (capturing) {
            QuickCaptureSheet(
                onDismiss = { capturing = false },
                onSaved = { type: CaptureType, id ->
                    capturing = false
                    scope.launch {
                        val result = snackbar.showSnackbar(
                            message = resources.getString(captureSavedMessage(type)),
                            actionLabel = resources.getString(com.behnamjalali.planb.feature.today.R.string.capture_open),
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) navController.openCaptured(type, id)
                    }
                },
                onFailed = {
                    scope.launch { snackbar.showSnackbar(resources.getString(com.behnamjalali.planb.feature.today.R.string.capture_failed)) }
                },
            )
        }

        // Plan-B Pro #29: a badge earned just now is celebrated once, over whatever is open.
        com.behnamjalali.planb.feature.habits.BadgeCelebrationHost(
            onOpenBadges = { navController.navigate(com.behnamjalali.planb.feature.habits.ChallengesRoute(badges = true)) },
        )
    }
}
