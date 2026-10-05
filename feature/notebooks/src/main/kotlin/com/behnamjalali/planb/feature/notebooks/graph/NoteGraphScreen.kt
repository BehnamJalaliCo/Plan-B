package com.behnamjalali.planb.feature.notebooks.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.Note
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerEmptyState
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.GraphLayout
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProGate
import com.behnamjalali.planb.core.ui.ProTeaser
import com.behnamjalali.planb.feature.notebooks.R
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun NoteGraphDestination(onBack: () -> Unit, onOpenNote: (EntityId) -> Unit, viewModel: NoteGraphViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NoteGraphScreen(
        state = state,
        onBack = onBack,
        onOpenNote = onOpenNote,
        onNotebook = viewModel::setNotebook,
        onTag = viewModel::setTag,
        onOrphans = viewModel::setOrphans,
    )
}

/**
 * The note graph (Plan-B Pro #21): notes as dots sized by their links, links as lines. Drag to
 * move, pinch to zoom; tapping a note highlights it with its neighbours, tapping it again (or
 * "Open") opens it. Filters by notebook and tag, and unlinked notes can be hidden. The list view
 * shows the same notes as plain rows for screen readers and anyone who prefers it.
 */
@Composable
fun NoteGraphScreen(
    state: GraphUi,
    onBack: () -> Unit,
    onOpenNote: (EntityId) -> Unit,
    onNotebook: (EntityId?) -> Unit,
    onTag: (EntityId?) -> Unit,
    onOrphans: (Boolean) -> Unit,
) {
    var listView by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(
            title = stringResource(R.string.graph_title),
            onBack = onBack,
            actions = {
                if (!listView) {
                    PlannerIconButton(Icons.Rounded.CenterFocusStrong, stringResource(R.string.graph_reset), {
                        zoom = 1f
                        pan = Offset.Zero
                        selected = null
                    })
                }
                PlannerIconButton(
                    if (listView) Icons.Rounded.Hub else Icons.AutoMirrored.Rounded.List,
                    stringResource(if (listView) R.string.graph_show_graph else R.string.graph_show_list),
                    { listView = !listView },
                )
            },
        )
        ProGate(ProFeature.NOTE_GRAPH, teaser = { ProTeaser(ProFeature.NOTE_GRAPH, Modifier.padding(horizontal = Spacing.screen)) }) {
            GraphFilters(state, onNotebook, onTag, onOrphans)
            val numbers = PlannerLocals.numbers
            if (state.capped) {
                Text(
                    stringResource(R.string.graph_capped, numbers.format(GraphLayout.MAX_NODES)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screen),
                )
            }
            when {
                state.loading -> PlannerLoadingState()
                state.nodes.isEmpty() -> PlannerEmptyState(Icons.Rounded.Hub, stringResource(R.string.graph_empty_title), stringResource(R.string.graph_empty))
                listView -> GraphList(state, onOpenNote)
                else -> Box(Modifier.fillMaxSize()) {
                    val selectedIndex = state.nodes.indexOfFirst { it.id == selected }
                    GraphCanvas(
                        state = state,
                        selectedIndex = selectedIndex,
                        zoom = zoom,
                        pan = pan,
                        onTransform = { z, p ->
                            zoom = z
                            pan = p
                        },
                        onTapNode = { index ->
                            val id = index?.let { state.nodes[it].id }
                            if (id != null && id == selected) onOpenNote(id) else selected = id
                        },
                    )
                    if (selectedIndex >= 0) {
                        val node = state.nodes[selectedIndex]
                        PlannerCard(modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.screen).navigationBarsPadding().fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        node.title.ifBlank { stringResource(R.string.link_untitled) },
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        stringResource(R.string.graph_links, numbers.format(node.degree)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                PlannerButton(stringResource(R.string.graph_open), { onOpenNote(node.id) }, style = PlannerButtonStyle.Tonal)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphFilters(state: GraphUi, onNotebook: (EntityId?) -> Unit, onTag: (EntityId?) -> Unit, onOrphans: (Boolean) -> Unit) {
    var notebookMenu by remember { mutableStateOf(false) }
    var tagMenu by remember { mutableStateOf(false) }
    val notebook = state.notebooks.firstOrNull { it.id == state.filter.notebookId }
    val tag = state.tags.firstOrNull { it.id == state.filter.tagId }
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Box {
                PlannerChip(
                    label = notebook?.title ?: stringResource(R.string.graph_all_notebooks),
                    selected = notebook != null,
                    onClick = { notebookMenu = true },
                    icon = Icons.Rounded.Book,
                )
                DropdownMenu(expanded = notebookMenu, onDismissRequest = { notebookMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.graph_all_notebooks)) }, onClick = {
                        notebookMenu = false
                        onNotebook(null)
                    })
                    state.notebooks.forEach { nb ->
                        DropdownMenuItem(text = { Text(nb.title) }, onClick = {
                            notebookMenu = false
                            onNotebook(nb.id)
                        })
                    }
                }
            }
        }
        if (state.tags.isNotEmpty()) {
            item {
                Box {
                    PlannerChip(
                        label = tag?.let { "#${it.name}" } ?: stringResource(R.string.graph_all_tags),
                        selected = tag != null,
                        onClick = { tagMenu = true },
                        icon = Icons.Rounded.Label,
                    )
                    DropdownMenu(expanded = tagMenu, onDismissRequest = { tagMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.graph_all_tags)) }, onClick = {
                            tagMenu = false
                            onTag(null)
                        })
                        state.tags.forEach { t ->
                            DropdownMenuItem(text = { Text("#${t.name}") }, onClick = {
                                tagMenu = false
                                onTag(t.id)
                            })
                        }
                    }
                }
            }
        }
        item {
            PlannerChip(
                label = stringResource(R.string.graph_orphans),
                selected = state.filter.orphans,
                onClick = { onOrphans(!state.filter.orphans) },
                icon = Icons.Rounded.LinkOff,
            )
        }
    }
}

@Composable
private fun GraphList(state: GraphUi, onOpenNote: (EntityId) -> Unit) {
    val numbers = PlannerLocals.numbers
    val rows = remember(state.nodes) { state.nodes.sortedWith(compareByDescending<GraphNodeUi> { it.degree }.thenBy { it.title }) }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(rows, key = { it.id }) { node ->
            PlannerCard(onClick = { onOpenNote(node.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    androidx.compose.material3.Icon(Icons.AutoMirrored.Rounded.Note, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        node.title.ifBlank { stringResource(R.string.link_untitled) },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.graph_links, numbers.format(node.degree)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Screen position of a node: world coordinates in [-1, 1] around the centre, then zoom and pan. */
private fun position(node: GraphNodeUi, center: Offset, base: Float, zoom: Float, pan: Offset) =
    Offset(center.x + node.x * base * zoom + pan.x, center.y + node.y * base * zoom + pan.y)

@Composable
private fun GraphCanvas(
    state: GraphUi,
    selectedIndex: Int,
    zoom: Float,
    pan: Offset,
    onTransform: (Float, Offset) -> Unit,
    onTapNode: (Int?) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 256)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurface)
    // A plain cache (not state): measuring while drawing must not trigger another draw.
    val labels = remember(state.nodes, labelStyle, measurer) { HashMap<Long, TextLayoutResult>() }
    val neighbors = remember(state.edges, selectedIndex) { if (selectedIndex >= 0) state.neighbors(selectedIndex) else emptySet() }
    val numbers = PlannerLocals.numbers
    val summary = stringResource(R.string.graph_summary, numbers.format(state.nodes.size), numbers.format(state.edges.size))
    val minRadius = with(density) { 4.dp.toPx() }
    val tapSlop = with(density) { 24.dp.toPx() }
    val currentZoom by androidx.compose.runtime.rememberUpdatedState(zoom)
    val currentPan by androidx.compose.runtime.rememberUpdatedState(pan)
    Canvas(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = summary }
            .pointerInput(state.nodes) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val newZoom = (currentZoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    // Zoom around the fingers: keep the point under the centroid in place.
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val factor = newZoom / currentZoom
                    val newPan = (currentPan + center - centroid) * factor - (center - centroid) + panChange
                    onTransform(newZoom, newPan)
                }
            }
            .pointerInput(state.nodes) {
                detectTapGestures { tap ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val base = min(size.width, size.height) / 2f * FILL
                    var best = -1
                    var bestDistance = Float.MAX_VALUE
                    state.nodes.forEachIndexed { i, node ->
                        val d = (position(node, center, base, currentZoom, currentPan) - tap).getDistance()
                        if (d < bestDistance) {
                            bestDistance = d
                            best = i
                        }
                    }
                    onTapNode(if (best >= 0 && bestDistance <= tapSlop) best else null)
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val base = min(size.width, size.height) / 2f * FILL
        val positions = state.nodes.map { position(it, center, base, zoom, pan) }
        val dim = selectedIndex >= 0
        // Links first, so dots sit on top.
        val lineColor = scheme.outlineVariant
        state.edges.forEach { (a, b) ->
            val highlighted = a == selectedIndex || b == selectedIndex
            drawLine(
                color = if (highlighted) scheme.primary else if (dim) lineColor.copy(alpha = 0.35f) else lineColor,
                start = positions[a],
                end = positions[b],
                strokeWidth = if (highlighted) 2.5f * density.density else 1f * density.density,
            )
        }
        val showAllLabels = state.nodes.size <= LABEL_LIMIT || zoom >= LABEL_ZOOM
        state.nodes.forEachIndexed { i, node ->
            val p = positions[i]
            if (p.x < -50f || p.y < -50f || p.x > size.width + 50f || p.y > size.height + 50f) return@forEachIndexed
            val radius = (minRadius + sqrt(node.degree.toFloat()) * 2f * density.density) * zoom.coerceIn(0.6f, 1.6f)
            val isSelected = i == selectedIndex
            val isNeighbor = i in neighbors
            val color: Color = when {
                isSelected -> scheme.primary
                isNeighbor -> scheme.tertiary
                node.degree == 0 -> scheme.outline
                else -> scheme.secondary
            }
            drawCircle(color = if (dim && !isSelected && !isNeighbor) color.copy(alpha = 0.3f) else color, radius = radius, center = p)
            if (isSelected) drawCircle(color = scheme.primary.copy(alpha = 0.25f), radius = radius * 2f, center = p)
            if (isSelected || isNeighbor || (showAllLabels && !dim)) {
                val label = labels.getOrPut(node.id) {
                    measurer.measure(
                        node.title.ifBlank { "…" },
                        style = labelStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        constraints = Constraints(maxWidth = (120 * density.density).toInt()),
                    )
                }
                drawLabel(label, p, radius)
            }
        }
    }
}

private fun DrawScope.drawLabel(label: TextLayoutResult, at: Offset, radius: Float) {
    drawText(label, topLeft = Offset(at.x - label.size.width / 2f, at.y + radius + 2f))
}

private const val FILL = 0.88f
private const val MIN_ZOOM = 0.4f
private const val MAX_ZOOM = 6f
private const val LABEL_LIMIT = 60
private const val LABEL_ZOOM = 2.2f
