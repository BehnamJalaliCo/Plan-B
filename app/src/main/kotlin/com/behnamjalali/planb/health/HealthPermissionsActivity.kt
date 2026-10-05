package com.behnamjalali.planb.health

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.MainUiState
import com.behnamjalali.planb.MainViewModel
import com.behnamjalali.planb.R
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.ui.PlanBProviders
import dagger.hilt.android.AndroidEntryPoint

/**
 * Health Connect's "why does this app ask" screen (Plan-B Pro #27), required for apps that read
 * Health Connect: opened by Health Connect's permission screen (Android 13 and older,
 * `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`) and from the system's app permissions
 * (Android 14+, `VIEW_PERMISSION_USAGE` with the `HEALTH_PERMISSIONS` category, through an alias
 * that only the system may start). It only explains what is read and why; it changes nothing.
 */
@AndroidEntryPoint
class HealthPermissionsActivity : AppCompatActivity() {
    private val main: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val state by main.uiState.collectAsStateWithLifecycle()
            val today by main.today.collectAsStateWithLifecycle()
            val settings = (state as? MainUiState.Ready)?.settings ?: return@setContent
            PlanBProviders(settings, today) { HealthRationale(onClose = ::finish) }
        }
    }
}

@Composable
internal fun HealthRationale(onClose: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Spacing.xl))
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(stringResource(R.string.health_rationale_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                listOf(R.string.health_rationale_intro, R.string.health_rationale_what, R.string.health_rationale_how, R.string.health_rationale_control).forEach {
                    Text(stringResource(it), style = MaterialTheme.typography.bodyLarge)
                }
                PlannerButton(stringResource(R.string.health_rationale_close), onClose, Modifier.fillMaxWidth())
            }
        }
    }
}
