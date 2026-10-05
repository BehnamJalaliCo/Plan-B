package com.behnamjalali.planb.feature.notebooks.clipper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.NoteRepository
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerSurface
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.Tag
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProGate
import com.behnamjalali.planb.core.ui.ProTeaser
import com.behnamjalali.planb.feature.notebooks.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ClipperState(
    val loading: Boolean = true,
    /** Null when nothing worth saving was shared. */
    val request: ClipRequest? = null,
    val title: String = "",
    val tags: String = "",
    val notebookId: EntityId? = null,
    val saving: Boolean = false,
    val saved: Saved? = null,
    val failed: Boolean = false,
) {
    data class Saved(val noteId: EntityId, val notebook: String)
}

/**
 * The web clipper's sheet (Plan-B Pro #22). The shared content is parsed once off the main
 * thread and kept in memory only (never in saved state, which has a small size limit); after
 * process death the activity is recreated with the same intent and parses it again.
 */
@HiltViewModel
class ClipperViewModel @Inject constructor(private val notes: NoteRepository) : ViewModel() {
    private val _state = MutableStateFlow(ClipperState())
    val state: StateFlow<ClipperState> = _state.asStateFlow()

    val notebooks: StateFlow<List<Notebook>> = notes.observeNotebooks(false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var started = false

    fun start(input: ClipInput, defaultTitle: String) {
        if (started) return
        started = true
        viewModelScope.launch {
            val request = withContext(Dispatchers.Default) {
                runCatchingSafely { ClipParser.parse(input, { UUID.randomUUID().toString() }, defaultTitle) }.getOrNull()
            }
            _state.update { it.copy(loading = false, request = request, title = request?.title.orEmpty()) }
        }
    }

    fun setTitle(title: String) = _state.update { it.copy(title = title.replace('\n', ' ').take(ClipParser.MAX_TITLE)) }
    fun setTags(tags: String) = _state.update { it.copy(tags = tags.take(MAX_TAGS_TEXT)) }
    fun setNotebook(id: EntityId) = _state.update { it.copy(notebookId = id) }

    fun save(defaultNotebook: String) {
        val s = _state.value
        val request = s.request ?: return
        if (s.saving || s.saved != null) return
        _state.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            runCatchingSafely {
                val notebookId = s.notebookId ?: notes.ensureDefaultNotebook(defaultNotebook)
                val tags = s.tags.split(',', '،').map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.distinct().take(MAX_TAGS).map { Tag(name = it.take(MAX_TAG)) }
                val id = notes.saveNote(Note(notebookId = notebookId, title = s.title.trim().ifBlank { request.title }, document = NoteDocument(blocks = request.blocks), tags = tags))
                ClipperState.Saved(id, notes.getNotebook(notebookId)?.title.orEmpty())
            }.onSuccess { saved -> _state.update { it.copy(saving = false, saved = saved) } }
                .onFailure { _state.update { it.copy(saving = false, failed = true) } }
        }
    }

    private companion object {
        const val MAX_TAGS = 20
        const val MAX_TAG = 60
        const val MAX_TAGS_TEXT = 1_000
    }
}

/** The sheet's content: free users see what the clipper does and how to unlock it. */
@Composable
fun ClipperSheetContent(
    state: ClipperState,
    notebooks: List<Notebook>,
    onTitle: (String) -> Unit,
    onTags: (String) -> Unit,
    onNotebook: (EntityId) -> Unit,
    onSave: () -> Unit,
    onOpen: (EntityId) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.xl)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.clipper_title), style = MaterialTheme.typography.titleLarge)
        }
        ProGate(ProFeature.WEB_CLIPPER, teaser = { ProTeaser(ProFeature.WEB_CLIPPER) }) {
            val request = state.request
            val saved = state.saved
            when {
                state.loading -> Text(stringResource(com.behnamjalali.planb.core.designsystem.R.string.ds_loading), style = MaterialTheme.typography.bodyMedium)
                saved != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.clipper_saved, saved.notebook), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PlannerButton(stringResource(R.string.clipper_open), { onOpen(saved.noteId) }, modifier = Modifier.weight(1f))
                        PlannerButton(stringResource(com.behnamjalali.planb.core.ui.R.string.ui_done), onClose, style = PlannerButtonStyle.Tonal, modifier = Modifier.weight(1f))
                    }
                }
                request == null -> Text(stringResource(R.string.clipper_nothing), style = MaterialTheme.typography.bodyLarge)
                else -> ClipForm(state, request, notebooks, onTitle, onTags, onNotebook, onSave)
            }
        }
    }
}

@Composable
private fun ClipForm(
    state: ClipperState,
    request: ClipRequest,
    notebooks: List<Notebook>,
    onTitle: (String) -> Unit,
    onTags: (String) -> Unit,
    onNotebook: (EntityId) -> Unit,
    onSave: () -> Unit,
) {
    PlannerTextField(state.title, onTitle, stringResource(R.string.clipper_note_title))
    var menu by remember { mutableStateOf(false) }
    val chosen = notebooks.firstOrNull { it.id == state.notebookId } ?: notebooks.firstOrNull()
    Column {
        Text(stringResource(R.string.clipper_notebook), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        androidx.compose.foundation.layout.Box {
            PlannerChip(
                label = chosen?.title ?: stringResource(com.behnamjalali.planb.core.data.R.string.data_default_notebook),
                selected = true,
                onClick = { menu = true },
                icon = Icons.Rounded.Book,
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                notebooks.forEach { nb ->
                    DropdownMenuItem(text = { Text(nb.title) }, onClick = {
                        menu = false
                        onNotebook(nb.id)
                    })
                }
            }
        }
    }
    PlannerTextField(state.tags, onTags, stringResource(R.string.clipper_tags))
    request.url?.let { url ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(Icons.Rounded.Link, contentDescription = stringResource(R.string.clipper_source), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    val preview = request.blocks.filter { it.type != BlockType.DIVIDER && it.text != request.url }
    if (preview.isNotEmpty()) {
        PlannerSurface(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
            Text(stringResource(R.string.clipper_preview), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            preview.take(PREVIEW_BLOCKS).forEach { block ->
                Text(
                    (if (block.type == BlockType.BULLET || block.type == BlockType.NUMBERED) "• " else "") + block.text,
                    style = if (block.type == BlockType.HEADING) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (preview.size > PREVIEW_BLOCKS) {
                Text(
                    stringResource(R.string.clipper_more_blocks, com.behnamjalali.planb.core.ui.PlannerLocals.numbers.format(preview.size - PREVIEW_BLOCKS)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (request.truncated) Text(stringResource(R.string.clipper_truncated), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (state.failed) Text(stringResource(R.string.clipper_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    PlannerButton(
        text = stringResource(R.string.clipper_save),
        onClick = onSave,
        enabled = !state.saving,
        icon = Icons.Rounded.BookmarkAdd,
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val PREVIEW_BLOCKS = 4
