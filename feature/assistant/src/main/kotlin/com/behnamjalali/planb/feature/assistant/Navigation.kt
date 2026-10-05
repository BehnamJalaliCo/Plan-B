package com.behnamjalali.planb.feature.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.behnamjalali.planb.core.ui.AssistantHost
import kotlinx.serialization.Serializable

/** The Assistant screen: chat about the plan and plan my day/week (Plan-B Pro #39). */
@Serializable
data object AssistantRoute

/** Provider, key, model and consent of the assistant. */
@Serializable
data object AiSettingsRoute

/**
 * The contextual assistant for the editors, provided by the app through
 * [com.behnamjalali.planb.core.ui.LocalAssistant]. [onOpenSettings] opens the assistant settings
 * when the assistant is not set up yet.
 */
@Composable
fun rememberAssistantHost(onOpenSettings: () -> Unit): AssistantHost {
    val openSettings by rememberUpdatedState(onOpenSettings)
    return remember {
        AssistantHost { request, onDismiss, onApply ->
            AiActionSheet(request, onDismiss, onApply, onOpenSettings = { openSettings() })
        }
    }
}
