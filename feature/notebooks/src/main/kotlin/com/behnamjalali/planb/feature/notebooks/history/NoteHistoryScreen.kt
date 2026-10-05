package com.behnamjalali.planb.feature.notebooks.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.NoteHistoryRepository
import com.behnamjalali.planb.core.data.repository.NoteRepository
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerEmptyState
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerSegmentedControl
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteDiff
import com.behnamjalali.planb.core.model.NoteVersion
import com.behnamjalali.planb.core.model.WordCount
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProGate
import com.behnamjalali.planb.core.ui.ProTeaser
import com.behnamjalali.planb.core.ui.metaSeparator
import com.behnamjalali.planb.feature.notebooks.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Version history of one note (Plan-B Pro #16). */
@Serializable
data class NoteHistoryRoute(val noteId: Long)

/** A version with what it differs from the note now. */
data class VersionRow(val version: NoteVersion, val words: Int, val added: Int, val removed: Int)

data class NoteHistoryState(
    val loading: Boolean = true,
    val note: Note? = null,
    val versions: List<VersionRow> = emptyList(),
    val now: Instant = Instant.EPOCH,
    val zone: ZoneId = ZoneId.systemDefault(),
)

sealed interface NoteHistoryEvent {
    data class Restored(val noteId: EntityId) : NoteHistoryEvent
    data object Failed : NoteHistoryEvent
}

@HiltViewModel
class NoteHistoryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    notes: NoteRepository,
    private val history: NoteHistoryRepository,
    private val time: TimeProvider,
) : ViewModel() {
    val noteId: EntityId = runCatching { savedState.toRoute<NoteHistoryRoute>().noteId }.getOrDefault(savedState.get<Long>("noteId") ?: 0L)

    val state: StateFlow<NoteHistoryState> = combine(notes.observeNote(noteId), history.observeVersions(noteId)) { note, versions ->
        val current = note?.let { NoteDiff.lines(it.title, it.document) }.orEmpty()
        NoteHistoryState(
            loading = false,
            note = note,
            versions = versions.map { v ->
                val diff = NoteDiff.diff(NoteDiff.lines(v.title, v.document), current)
                VersionRow(
                    version = v,
                    words = WordCount.of(v.document).words,
                    // Seen from the version: what the note now adds and what it no longer has.
                    added = diff.count { it.kind == NoteDiff.Kind.ADDED },
                    removed = diff.count { it.kind == NoteDiff.Kind.REMOVED },
                )
            },
            now = time.now(),
            zone = time.zone(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteHistoryState())

    private val _events = MutableSharedFlow<NoteHistoryEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<NoteHistoryEvent> = _events

    /** Line differences from [version] to the note as it is now. */
    fun diff(version: NoteVersion): List<NoteDiff.Line> {
        val note = state.value.note ?: return emptyList()
        return NoteDiff.diff(version.title, version.document, note.title, note.document)
    }

    fun restore(versionId: EntityId) = viewModelScope.launch {
        runCatchingSafely { history.restore(versionId) }
            .onSuccess { _events.tryEmit(NoteHistoryEvent.Restored(it)) }
            .onFailure { _events.tryEmit(NoteHistoryEvent.Failed) }
    }
}

@Composable
fun NoteHistoryDestination(
    onBack: () -> Unit,
    /** After a restore: reopen the note's editor (it must reload the restored text). */
    onRestored: (EntityId) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: NoteHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is NoteHistoryEvent.Restored -> {
                    onRestored(event.noteId)
                    snackbarHostState.showSnackbar(resources.getString(R.string.history_restored))
                }
                NoteHistoryEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(com.behnamjalali.planb.core.ui.R.string.ui_error_generic))
            }
        }
    }
    NoteHistoryScreen(state, onBack = onBack, diff = viewModel::diff, onRestore = { viewModel.restore(it) }, snackbarHostState = snackbarHostState)
}

private enum class VersionView { PREVIEW, COMPARE }

@Composable
fun NoteHistoryScreen(
    state: NoteHistoryState,
    onBack: () -> Unit,
    diff: (NoteVersion) -> List<NoteDiff.Line>,
    onRestore: (EntityId) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    val row = state.versions.firstOrNull { it.version.id == selected }
    val back = if (row != null) ({ selected = null }) else onBack
    androidx.activity.compose.BackHandler(enabled = row != null) { selected = null }
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(
            title = stringResource(R.string.history_title),
            subtitle = state.note?.title?.ifBlank { null },
            onBack = back,
        )
        ProGate(ProFeature.NOTE_LINKS, teaser = { ProTeaser(ProFeature.NOTE_LINKS, Modifier.padding(horizontal = Spacing.screen)) }) {
            when {
                state.loading -> PlannerLoadingState()
                state.note?.locked == true -> PlannerEmptyState(Icons.Rounded.Lock, stringResource(R.string.history_title), stringResource(R.string.history_locked))
                state.versions.isEmpty() -> {
                    val numbers = PlannerLocals.numbers
                    PlannerEmptyState(
                        icon = Icons.Rounded.History,
                        title = stringResource(R.string.history_empty_title),
                        message = stringResource(
                            R.string.history_empty,
                            numbers.format(NoteHistoryRepository.INTERVAL.toMinutes()),
                            numbers.format(NoteHistoryRepository.MAX_VERSIONS),
                            numbers.format(NoteHistoryRepository.MAX_AGE.toDays()),
                        ),
                    )
                }
                row != null -> VersionDetail(row, state, diff, onRestore)
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(state.versions, key = { it.version.id }) { r -> VersionCard(r, state) { selected = r.version.id } }
                }
            }
        }
        SnackbarHost(snackbarHostState)
    }
}

/** "Just now", "5 min ago", "14:30", or "Yesterday, 14:30" / a date with the time. */
@Composable
private fun versionTime(at: Instant, state: NoteHistoryState): String {
    val formatter = PlannerLocals.formatter
    val age = Duration.between(at, state.now)
    val local = at.atZone(state.zone)
    val today = state.now.atZone(state.zone).toLocalDate()
    return when {
        !age.isNegative && age.toMinutes() < 1 -> stringResource(R.string.history_just_now)
        !age.isNegative && age.toMinutes() < 60 -> stringResource(R.string.history_minutes_ago, PlannerLocals.numbers.format(age.toMinutes()))
        local.toLocalDate() == today -> formatter.time(local.toLocalTime())
        else -> formatter.relativeDate(local.toLocalDate(), today) + metaSeparator() + formatter.time(local.toLocalTime())
    }
}

@Composable
private fun changesText(row: VersionRow): String {
    val numbers = PlannerLocals.numbers
    return if (row.added == 0 && row.removed == 0) {
        stringResource(R.string.history_no_changes)
    } else {
        stringResource(R.string.history_changes, numbers.format(row.added), numbers.format(row.removed))
    }
}

@Composable
private fun VersionCard(row: VersionRow, state: NoteHistoryState, onClick: () -> Unit) {
    PlannerCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(versionTime(row.version.createdAt, state), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            row.version.title.ifBlank { stringResource(R.string.link_untitled) },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            pluralStringResource(R.plurals.history_words, row.words, PlannerLocals.numbers.format(row.words)) + metaSeparator() + changesText(row),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VersionDetail(row: VersionRow, state: NoteHistoryState, diff: (NoteVersion) -> List<NoteDiff.Line>, onRestore: (EntityId) -> Unit) {
    var view by rememberSaveable { mutableStateOf(VersionView.COMPARE) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val lines = remember(row, state.note, view) {
        if (view == VersionView.COMPARE) diff(row.version) else NoteDiff.lines(row.version.title, row.version.document).map { NoteDiff.Line(NoteDiff.Kind.SAME, it) }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Spacing.screen)) {
            Text(versionTime(row.version.createdAt, state), style = MaterialTheme.typography.titleMedium)
            Text(changesText(row), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PlannerSegmentedControl(
                options = VersionView.entries,
                selected = view,
                onSelect = { view = it },
                label = { stringResource(if (it == VersionView.PREVIEW) R.string.history_preview else R.string.history_compare) },
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            itemsIndexed(lines) { _, line -> DiffLine(line) }
        }
        PlannerButton(
            text = stringResource(R.string.history_restore),
            onClick = { confirm = true },
            icon = Icons.Rounded.SettingsBackupRestore,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.md).navigationBarsPadding(),
        )
    }
    if (confirm) {
        PlannerDialog(
            title = stringResource(R.string.history_restore),
            message = stringResource(R.string.history_restore_confirm),
            onDismiss = { confirm = false },
            confirmLabel = stringResource(R.string.history_restore),
            onConfirm = {
                confirm = false
                onRestore(row.version.id)
            },
        )
    }
}

@Composable
private fun DiffLine(line: NoteDiff.Line) {
    val colors = PlanBTheme.colors
    val scheme = MaterialTheme.colorScheme
    val (marker, background, description) = when (line.kind) {
        NoteDiff.Kind.SAME -> Triple(" ", null, line.text)
        NoteDiff.Kind.ADDED -> Triple("+", colors.success.copy(alpha = 0.16f), stringResource(R.string.history_added_cd, line.text))
        NoteDiff.Kind.REMOVED -> Triple("−", scheme.error.copy(alpha = 0.14f), stringResource(R.string.history_removed_cd, line.text))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (background != null) Modifier.background(background, RoundedCornerShape(Radius.xs)) else Modifier)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            marker,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = when (line.kind) {
                NoteDiff.Kind.ADDED -> colors.success
                NoteDiff.Kind.REMOVED -> scheme.error
                NoteDiff.Kind.SAME -> scheme.outline
            },
            modifier = Modifier.padding(end = Spacing.sm),
        )
        Text(
            line.text.ifEmpty { " " },
            style = MaterialTheme.typography.bodyMedium,
            color = if (line.kind == NoteDiff.Kind.SAME) scheme.onSurfaceVariant else scheme.onSurface,
        )
    }
}
