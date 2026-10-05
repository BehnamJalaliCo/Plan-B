package com.behnamjalali.planb.core.speech

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.rememberProGuard

private enum class MicPrompt { RATIONALE, SETTINGS, UNAVAILABLE }

/**
 * A microphone button for Plan-B Pro voice input (#40). For free users it opens the Pro screen
 * (only on tap). Otherwise: a speech service must exist (else a calm explanation), the
 * microphone permission is asked in context after an explanation (with a way to Settings when
 * it was denied for good), then a dialog listens with live partial text and hands the final
 * text to [onResult]. [key] separates several voice fields on one screen.
 */
@Composable
fun VoiceInputButton(
    onResult: (String) -> Unit,
    key: String,
    modifier: Modifier = Modifier,
    viewModel: VoiceInputViewModel = hiltViewModel(key = "voice_input_$key"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val guard = rememberProGuard()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val languageTag = speechLanguageTag(LocalConfiguration.current.locales[0])
    var prompt by rememberSaveable { mutableStateOf<MicPrompt?>(null) }
    val currentOnResult by rememberUpdatedState(onResult)

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            granted -> viewModel.start(languageTag)
            activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> prompt = MicPrompt.SETTINGS
        }
    }
    LaunchedEffect(viewModel) { viewModel.results.collect { currentOnResult(it) } }

    val description = stringResource(if (guard.isPro) R.string.speech_voice_input else R.string.speech_voice_input_pro)
    PlannerIconButton(
        icon = Icons.Rounded.Mic,
        contentDescription = description,
        onClick = {
            guard.run(ProFeature.VOICE_INPUT) {
                prompt = when {
                    !viewModel.isAvailable() -> MicPrompt.UNAVAILABLE
                    hasMicrophone(context) -> {
                        viewModel.start(languageTag)
                        null
                    }
                    else -> MicPrompt.RATIONALE
                }
            }
        },
        modifier = modifier,
        tint = if (state.phase == VoicePhase.LISTENING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )

    when (prompt) {
        MicPrompt.RATIONALE -> PlannerDialog(
            title = stringResource(R.string.speech_permission_title),
            message = stringResource(R.string.speech_permission_message),
            onDismiss = { prompt = null },
            confirmLabel = stringResource(R.string.speech_continue),
            onConfirm = {
                prompt = null
                permission.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
        MicPrompt.SETTINGS -> PlannerDialog(
            title = stringResource(R.string.speech_permission_title),
            message = stringResource(R.string.speech_permission_settings),
            onDismiss = { prompt = null },
            confirmLabel = stringResource(R.string.speech_open_settings),
            onConfirm = {
                prompt = null
                openAppSettings(context)
            },
        )
        MicPrompt.UNAVAILABLE -> PlannerDialog(
            title = stringResource(R.string.speech_unavailable_title),
            message = stringResource(R.string.speech_unavailable_message),
            onDismiss = { prompt = null },
            confirmLabel = stringResource(R.string.speech_ok),
            onConfirm = { prompt = null },
            dismissLabel = "",
        )
        null -> Unit
    }

    if (state.phase != VoicePhase.IDLE) {
        ListeningDialog(
            state = state,
            onDone = viewModel::done,
            onCancel = viewModel::cancel,
            onRetry = {
                viewModel.dismissError()
                viewModel.start(languageTag)
            },
            onOpenSettings = {
                viewModel.dismissError()
                openAppSettings(context)
            },
        )
    }
}

/** The listening dialog: a mic that grows with the voice, the words heard so far, Done and Cancel. */
@Composable
fun ListeningDialog(
    state: VoiceInputState,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val error = state.error
    if (state.phase == VoicePhase.ERROR && error != null) {
        val permission = error == DictationError.PERMISSION
        PlannerDialog(
            title = stringResource(R.string.speech_voice_input),
            message = stringResource(errorMessage(error)),
            onDismiss = onCancel,
            confirmLabel = stringResource(if (permission) R.string.speech_open_settings else R.string.speech_try_again),
            onConfirm = if (permission) onOpenSettings else onRetry,
            dismissLabel = stringResource(R.string.speech_close),
        )
        return
    }
    val motion = PlanBTheme.motion
    val scale by animateFloatAsState(1f + state.level * 0.35f, motion.standard(120), label = "voiceLevel")
    PlannerDialog(
        title = stringResource(if (state.ready) R.string.speech_listening else R.string.speech_starting),
        onDismiss = onCancel,
        confirmLabel = stringResource(R.string.speech_done),
        onConfirm = onDone,
        confirmEnabled = state.partial.isNotBlank(),
        content = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(72.dp)
                        .scale(scale)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(IconSize.lg))
                }
            }
            Text(
                state.partial.ifBlank { stringResource(R.string.speech_speak_now) },
                style = if (state.partial.isBlank()) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                color = if (state.partial.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        },
    )
}

private fun errorMessage(error: DictationError): Int = when (error) {
    DictationError.NO_SPEECH -> R.string.speech_error_no_speech
    DictationError.UNAVAILABLE -> R.string.speech_unavailable_message
    DictationError.PERMISSION -> R.string.speech_permission_settings
    DictationError.NETWORK -> R.string.speech_error_network
    DictationError.BUSY -> R.string.speech_error_busy
    DictationError.LANGUAGE -> R.string.speech_error_language
    DictationError.OTHER -> R.string.speech_error_other
}

private fun hasMicrophone(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    runCatching {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
