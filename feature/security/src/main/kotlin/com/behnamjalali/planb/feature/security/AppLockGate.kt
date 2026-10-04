package com.behnamjalali.planb.feature.security

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.data.security.LockState
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.ui.DeviceAuth
import com.behnamjalali.planb.core.ui.findFragmentActivity

/**
 * App lock (Plan-B Pro #36): shows [content], covered by the lock screen while the app is
 * locked. It also reports when the app leaves and returns (not for rotation), and hides the app
 * in the recent-apps overview when the user chose so.
 */
@Composable
fun AppLockGate(viewModel: AppLockViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hideInRecents by viewModel.hideInRecents.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (activity?.isChangingConfigurations != true) viewModel.onBackground()
                Lifecycle.Event.ON_START -> viewModel.onForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(activity, hideInRecents) {
        activity?.let { setHiddenInRecents(it, hideInRecents) }
        onDispose {}
    }

    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)
    val unlock: () -> Unit = {
        if (activity == null || !DeviceAuth.canAuthenticate(context)) {
            // Without any screen lock on the device there is nothing to check against.
            viewModel.unlock()
        } else {
            viewModel.authenticationStarted()
            DeviceAuth.authenticate(activity, title, subtitle) { ok ->
                viewModel.authenticationFinished()
                if (ok) viewModel.unlock()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (state == LockState.UNLOCKED) Modifier else Modifier.clearAndSetSemantics {})) { content() }
        if (state != LockState.UNLOCKED) {
            // The opaque surface also keeps touches from reaching the content below.
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (state == LockState.LOCKED) {
                    BackHandler { activity?.moveTaskToBack(true) }
                    LockScreen(onUnlock = unlock)
                    LaunchedEffect(Unit) {
                        // Ask once when the lock screen appears; the button asks again.
                        if (activity != null && DeviceAuth.canAuthenticate(context)) unlock()
                    }
                }
            }
        }
    }
}

private fun setHiddenInRecents(activity: Activity, hidden: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Hides only the recents preview; screenshots of the app keep working.
        activity.setRecentsScreenshotEnabled(!hidden)
    } else if (hidden) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/** The lock screen: the app is locked until the device lock confirms the user. */
@Composable
fun LockScreen(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(Spacing.xl))
        Text(
            stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            stringResource(R.string.lock_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        PlannerButton(text = stringResource(R.string.lock_unlock), onClick = onUnlock, icon = Icons.Rounded.Lock)
    }
}
