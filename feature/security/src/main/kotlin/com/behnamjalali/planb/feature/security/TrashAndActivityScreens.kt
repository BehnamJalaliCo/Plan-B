package com.behnamjalali.planb.feature.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerEmptyState
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.ActivityAction
import com.behnamjalali.planb.core.model.ActivityEntityType
import com.behnamjalali.planb.core.model.ActivityEntry
import com.behnamjalali.planb.core.model.TrashItem
import com.behnamjalali.planb.core.model.TrashItemType
import com.behnamjalali.planb.core.ui.ConfirmDeleteDialog
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProGate
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProTeaser
import java.time.Duration
import java.time.ZoneId

// region Trash

@Composable
fun TrashDestination(onBack: () -> Unit, snackbarHostState: SnackbarHostState, viewModel: TrashViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val numbers = PlannerLocals.numbers
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val text = when (event) {
                is TrashEvent.Restored -> resources.getString(R.string.trash_restored)
                TrashEvent.Deleted -> resources.getString(R.string.trash_deleted)
                is TrashEvent.Emptied -> resources.getQuantityString(R.plurals.trash_emptied, event.count, numbers.format(event.count))
                TrashEvent.Failed -> resources.getString(com.behnamjalali.planb.core.ui.R.string.ui_error_generic)
            }
            snackbarHostState.showSnackbar(text)
        }
    }
    TrashScreen(state, onBack, viewModel::setFilter, viewModel::restore, viewModel::deleteForever, viewModel::empty)
}

@Composable
fun TrashScreen(
    state: TrashUiState,
    onBack: () -> Unit,
    onFilter: (TrashFilter) -> Unit,
    onRestore: (TrashItem) -> Unit,
    onDeleteForever: (TrashItem) -> Unit,
    onEmpty: () -> Unit,
) {
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    // Items deleted while Pro stay restorable if Pro ends; only an empty trash shows the teaser.
    val showTrash = LocalProAccess.current.isPro || state.items.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(
            stringResource(R.string.trash_title),
            onBack = onBack,
            actions = {
                if (state.items.isNotEmpty()) {
                    PlannerIconButton(Icons.Rounded.DeleteSweep, stringResource(R.string.trash_empty), { confirmEmpty = true })
                }
            },
        )
        when {
            state.loading -> PlannerLoadingState()
            !showTrash -> Box(Modifier.padding(Spacing.screen)) { ProTeaser(ProFeature.TRASH_HISTORY) }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item(key = "hint") {
                    Text(
                        stringResource(R.string.trash_hint, PlannerLocals.numbers.format(TrashItem.RETENTION_DAYS.toInt())),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item(key = "filters") {
                    FilterRow(
                        TrashFilter.entries.map { it to stringResource(trashFilterLabel(it)) },
                        state.filter,
                        onFilter,
                    )
                }
                if (state.visible.isEmpty()) {
                    item(key = "empty") {
                        PlannerEmptyState(
                            icon = Icons.Rounded.RestoreFromTrash,
                            title = stringResource(R.string.trash_empty_title),
                            message = stringResource(R.string.trash_empty_message),
                        )
                    }
                }
                items(state.visible, key = { "${it.type}_${it.id}" }) { item ->
                    TrashRow(item, onRestore = { onRestore(item) }, onDelete = { confirmDelete = "${item.type}_${item.id}" })
                }
            }
        }
    }
    if (confirmEmpty) {
        ConfirmDeleteDialog(
            stringResource(R.string.trash_empty),
            stringResource(R.string.trash_empty_confirm),
            { confirmEmpty = false },
            {
                confirmEmpty = false
                onEmpty()
            },
        )
    }
    confirmDelete?.let { key ->
        val item = state.items.firstOrNull { "${it.type}_${it.id}" == key }
        if (item == null) {
            confirmDelete = null
        } else {
            ConfirmDeleteDialog(
                stringResource(R.string.trash_delete_forever),
                stringResource(R.string.trash_delete_confirm),
                { confirmDelete = null },
                {
                    confirmDelete = null
                    onDeleteForever(item)
                },
            )
        }
    }
}

private fun trashFilterLabel(filter: TrashFilter) = when (filter) {
    TrashFilter.ALL -> R.string.filter_all
    TrashFilter.TASKS -> R.string.filter_tasks
    TrashFilter.NOTES -> R.string.filter_notes
}

@Composable
private fun TrashRow(item: TrashItem, onRestore: () -> Unit, onDelete: () -> Unit) {
    val formatter = PlannerLocals.formatter
    val numbers = PlannerLocals.numbers
    val today = PlannerLocals.today
    val resources = LocalResources.current
    val deleted = item.deletedAt.atZone(ZoneId.systemDefault()).toLocalDate()
    val daysGone = Duration.between(item.deletedAt.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(), today.atStartOfDay()).toDays()
    val left = (TrashItem.RETENTION_DAYS - daysGone).coerceAtLeast(0).toInt()
    val details = buildList {
        add(stringResource(R.string.trash_deleted_on, formatter.relativeDate(deleted, today)))
        if (item.childCount > 0) add(resources.getQuantityString(R.plurals.trash_subtasks, item.childCount, numbers.format(item.childCount)))
        add(resources.getQuantityString(R.plurals.trash_days_left, left, numbers.format(left)))
    }.joinToString(" · ")
    PlannerCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeIcon(if (item.type == TrashItemType.TASK) Icons.Rounded.TaskAlt else if (item.locked) Icons.Rounded.Lock else Icons.AutoMirrored.Rounded.StickyNote2)
            Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                Text(
                    item.title.ifBlank { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PlannerIconButton(Icons.Rounded.Restore, stringResource(R.string.trash_restore), onRestore)
            PlannerIconButton(Icons.Rounded.DeleteForever, stringResource(R.string.trash_delete_forever), onDelete)
        }
    }
}

// endregion

// region Activity

@Composable
fun ActivityDestination(
    onBack: () -> Unit,
    onOpen: (ActivityEntityType, Long) -> Unit,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ActivityScreen(state, onBack, viewModel::setFilter, onOpen)
}

@Composable
fun ActivityScreen(
    state: ActivityUiState,
    onBack: () -> Unit,
    onFilter: (ActivityEntityType?) -> Unit,
    onOpen: (ActivityEntityType, Long) -> Unit,
) {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    val zone = ZoneId.systemDefault()
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(stringResource(if (state.single) R.string.activity_item_title else R.string.activity_title), onBack = onBack)
        ProGate(ProFeature.TRASH_HISTORY, teaser = { Box(Modifier.padding(Spacing.screen)) { ProTeaser(ProFeature.TRASH_HISTORY) } }) {
            if (state.loading) {
                PlannerLoadingState()
                return@ProGate
            }
            LazyColumn(
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (!state.single) {
                    item(key = "filters") {
                        FilterRow(
                            listOf<Pair<ActivityEntityType?, String>>(null to stringResource(R.string.filter_all)) +
                                FILTER_TYPES.map { it to stringResource(typeLabel(it)) },
                            state.filter,
                            onFilter,
                        )
                    }
                }
                if (state.entries.isEmpty()) {
                    item(key = "empty") {
                        PlannerEmptyState(
                            icon = Icons.Rounded.History,
                            title = stringResource(R.string.activity_empty_title),
                            message = stringResource(R.string.activity_empty_message),
                        )
                    }
                }
                state.entries.groupBy { it.at.atZone(zone).toLocalDate() }.forEach { (date, entries) ->
                    item(key = "d_$date") {
                        Box(Modifier.semantics { heading() }) { PlannerSectionHeader(formatter.relativeDate(date, today)) }
                    }
                    items(entries, key = { it.id }) { entry ->
                        ActivityRow(entry, formatter.time(entry.at.atZone(zone).toLocalTime()), onClick = if (state.single) null else ({ onOpen(entry.entityType, entry.entityId) }))
                    }
                }
            }
        }
    }
}

private val FILTER_TYPES = listOf(
    ActivityEntityType.TASK, ActivityEntityType.NOTE, ActivityEntityType.PROJECT,
    ActivityEntityType.EVENT, ActivityEntityType.HABIT, ActivityEntityType.GOAL,
)

@Composable
private fun ActivityRow(entry: ActivityEntry, time: String, onClick: (() -> Unit)?) {
    val title = entry.summary.ifBlank { stringResource(R.string.untitled) }
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            TypeIcon(actionIcon(entry.action))
            Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.activity_line, stringResource(actionLabel(entry.action)), stringResource(typeLabel(entry.entityType))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun actionIcon(action: ActivityAction) = when (action) {
    ActivityAction.CREATED -> Icons.Rounded.AddCircleOutline
    ActivityAction.UPDATED -> Icons.Rounded.Edit
    ActivityAction.COMPLETED -> Icons.Rounded.CheckCircle
    ActivityAction.REOPENED -> Icons.Rounded.RadioButtonUnchecked
    ActivityAction.DELETED -> Icons.Rounded.DeleteOutline
    ActivityAction.RESTORED -> Icons.Rounded.Restore
    ActivityAction.ARCHIVED -> Icons.Rounded.Archive
}

private fun actionLabel(action: ActivityAction) = when (action) {
    ActivityAction.CREATED -> R.string.activity_created
    ActivityAction.UPDATED -> R.string.activity_updated
    ActivityAction.COMPLETED -> R.string.activity_completed
    ActivityAction.REOPENED -> R.string.activity_reopened
    ActivityAction.DELETED -> R.string.activity_deleted
    ActivityAction.RESTORED -> R.string.activity_restored
    ActivityAction.ARCHIVED -> R.string.activity_archived
}

private fun typeLabel(type: ActivityEntityType) = when (type) {
    ActivityEntityType.TASK -> R.string.type_task
    ActivityEntityType.PROJECT -> R.string.type_project
    ActivityEntityType.NOTE -> R.string.type_note
    ActivityEntityType.NOTEBOOK -> R.string.type_notebook
    ActivityEntityType.HABIT -> R.string.type_habit
    ActivityEntityType.GOAL -> R.string.type_goal
    ActivityEntityType.EVENT -> R.string.type_event
}

// endregion

@Composable
private fun TypeIcon(icon: ImageVector) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(Spacing.sm).size(IconSize.sm))
    }
}

@Composable
private fun <T> FilterRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(options, key = { it.second }) { (value, label) ->
            PlannerChip(label = label, selected = value == selected, onClick = { onSelect(value) })
        }
    }
}
