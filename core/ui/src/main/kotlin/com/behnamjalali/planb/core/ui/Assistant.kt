package com.behnamjalali.planb.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** Where a contextual assistant request comes from (Plan-B Pro #39). */
enum class AssistantSource { NOTE, TASK }

/** Contextual actions an editor can offer. */
enum class AssistantAction { SUMMARIZE, REWRITE, TRANSLATE, CONTINUE, EXTRACT_TASKS, SUGGEST_TITLES, BREAK_DOWN }

/**
 * What the user chose to send: [text] is a selection ([isSelection]) or the whole body. Only
 * this text (and the title) ever leaves the device, and only after the user starts an action.
 */
@Immutable
data class AssistantRequest(
    val source: AssistantSource,
    val title: String,
    val text: String,
    val isSelection: Boolean,
    val actions: List<AssistantAction>,
    /** New tasks extracted from a note go into this project (none for notes). */
    val projectId: Long? = null,
)

/** A change the user confirmed in the assistant sheet; the editor applies it (with Undo). */
sealed interface AssistantOutcome {
    /** Replace the selected text. */
    data class ReplaceSelection(val text: String) : AssistantOutcome

    /** Insert after the selection or the focused block (or at the end). */
    data class InsertText(val text: String) : AssistantOutcome

    data class SetTitle(val title: String) : AssistantOutcome

    /** Insert as checklist items. */
    data class AddChecklist(val items: List<String>) : AssistantOutcome

    data class AddSubtasks(val items: List<String>) : AssistantOutcome
}

/**
 * The assistant's sheet, provided by the app (feature modules never depend on the assistant
 * module). Null when the app has no assistant (previews, isolated tests): editors then hide
 * their entry points.
 */
@Immutable
class AssistantHost(
    val sheet: @Composable (request: AssistantRequest, onDismiss: () -> Unit, onApply: (AssistantOutcome) -> Unit) -> Unit,
)

val LocalAssistant = staticCompositionLocalOf<AssistantHost?> { null }
