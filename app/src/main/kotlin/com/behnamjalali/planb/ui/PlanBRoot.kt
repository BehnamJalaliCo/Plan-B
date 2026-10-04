package com.behnamjalali.planb.ui

import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.MainUiState
import com.behnamjalali.planb.MainViewModel
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.model.ThemeMode
import com.behnamjalali.planb.core.model.UserSettings
import com.behnamjalali.planb.core.ui.LocalDateFormatter
import com.behnamjalali.planb.core.ui.LocalToday
import com.behnamjalali.planb.core.ui.rememberDateFormatter
import com.behnamjalali.planb.feature.security.AppLockGate
import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PlanBRoot(
    viewModel: MainViewModel,
    pendingLink: StateFlow<Uri?>,
    onLinkHandled: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val link by pendingLink.collectAsStateWithLifecycle()
    val settings = (state as? MainUiState.Ready)?.settings ?: return
    PlanBProviders(settings, today) {
        // App lock (Plan-B Pro #36) covers the whole app while it is locked.
        AppLockGate {
            PlanBApp(
                settings = settings,
                pendingLink = link,
                onLinkHandled = onLinkHandled,
                onOnboardingDone = viewModel::completeOnboarding,
            )
        }
    }
}

/** Theme + formatting locals derived from the user's settings. */
@Composable
fun PlanBProviders(settings: UserSettings, today: LocalDate, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    PlanBTheme(darkTheme = dark, animationsEnabled = settings.animationsEnabled, hapticsEnabled = settings.hapticsEnabled) {
        val formatter = rememberDateFormatter(settings.calendarSystem, settings.firstDayOfWeek, settings.usePersianDigits)
        CompositionLocalProvider(LocalDateFormatter provides formatter, LocalToday provides today, content = content)
    }
}
