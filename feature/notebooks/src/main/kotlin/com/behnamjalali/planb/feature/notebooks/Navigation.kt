package com.behnamjalali.planb.feature.notebooks

import kotlinx.serialization.Serializable

@Serializable
data object NotebooksRoute

@Serializable
data class NotebookDetailRoute(val notebookId: Long)

/** [noteId] 0 creates a new note in [notebookId] (or the default notebook). */
@Serializable
data class NoteEditorRoute(val noteId: Long = 0, val notebookId: Long? = null, val sectionId: Long? = null)
