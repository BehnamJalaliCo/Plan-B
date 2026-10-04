package com.behnamjalali.planb

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.ui.PlanBRoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    /** Deep links (e.g. from reminder notifications) waiting to be handled by navigation. */
    private val pendingLink = MutableStateFlow<android.net.Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // Splash stays only until stored settings are read (no artificial delay).
        splash.setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingLink.value = intent?.data
        val appLanguage = AppLanguage.fromTag(AppCompatDelegate.getApplicationLocales()[0]?.language)
        viewModel.syncLanguage(appLanguage)
        setContent {
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
