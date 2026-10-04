package com.behnamjalali.planb

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.behnamjalali.planb.core.model.ThemeMode
import com.behnamjalali.planb.core.notifications.Notifier
import com.behnamjalali.planb.ui.PlanBRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var notifier: Notifier

    /** Deep links (e.g. from reminder notifications) waiting to be handled by navigation. */
    private val pendingLink = MutableStateFlow<android.net.Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // Splash stays only until stored settings are read (no artificial delay).
        splash.setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // This context carries the app language, so channel names follow it (also before API 33).
        notifier.createChannels(this)
        if (savedInstanceState == null) pendingLink.value = intent?.data
        lifecycleScope.launch {
            // The stored language is the source of truth: a restore or a settings change is
            // applied here; a choice made in the system settings is stored first.
            viewModel.appLanguage(AppLocales.platformLanguage(this@MainActivity)).collect { language ->
                AppLocales.apply(this@MainActivity, language)
            }
        }
        lifecycleScope.launch { viewModel.themeMode.collect(AppTheme::applyChangedNightMode) }
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val themeMode = (state as? MainUiState.Ready)?.settings?.themeMode ?: ThemeMode.SYSTEM
            val dark = AppTheme.isDark(themeMode, LocalConfiguration.current)
            // System bar icons follow the app theme, which can differ from the system theme.
            DisposableEffect(dark) {
                enableEdgeToEdge(statusBarStyle = AppTheme.statusBarStyle(dark), navigationBarStyle = AppTheme.navigationBarStyle(dark))
                onDispose {}
            }
            PlanBRoot(
                viewModel = viewModel,
                pendingLink = pendingLink,
                onLinkHandled = { pendingLink.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { pendingLink.value = it }
    }
}
