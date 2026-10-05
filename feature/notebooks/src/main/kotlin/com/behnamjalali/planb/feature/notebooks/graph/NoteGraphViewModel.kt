package com.behnamjalali.planb.feature.notebooks.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.data.repository.NoteLinkRepository
import com.behnamjalali.planb.core.data.repository.NoteRepository
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.GraphLayout
import com.behnamjalali.planb.core.model.NoteGraph
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

/** The note graph (Plan-B Pro #21). */
@Serializable
data object NoteGraphRoute

data class GraphFilter(val notebookId: EntityId? = null, val tagId: EntityId? = null, val orphans: Boolean = true)

data class GraphNodeUi(val id: EntityId, val title: String, val x: Float, val y: Float, val degree: Int)

/** Nodes with their laid-out positions in [-1, 1]; [edges] are index pairs into [nodes]. */
data class GraphUi(
    val loading: Boolean = true,
    val nodes: List<GraphNodeUi> = emptyList(),
    val edges: List<Pair<Int, Int>> = emptyList(),
    /** Notes left out because the graph was larger than [GraphLayout.MAX_NODES]. */
    val capped: Boolean = false,
    val filter: GraphFilter = GraphFilter(),
    val notebooks: List<Notebook> = emptyList(),
    val tags: List<Tag> = emptyList(),
) {
    /** Indices of the nodes linked to node [index]. */
    fun neighbors(index: Int): Set<Int> = edges.mapNotNullTo(HashSet()) { (a, b) -> if (a == index) b else if (b == index) a else null }
}

/** Filtering, capping and layout of the graph: pure, so it is unit-tested and runs off the main thread. */
object GraphBuilder {
    /** The same notes always get the same picture. */
    const val SEED = 21L

    data class Built(val nodes: List<GraphNodeUi>, val edges: List<Pair<Int, Int>>, val capped: Boolean)

    fun build(graph: NoteGraph, filter: GraphFilter, maxNodes: Int = GraphLayout.MAX_NODES): Built {
        var notes = graph.notes
        filter.notebookId?.let { nb -> notes = notes.filter { it.notebookId == nb } }
        filter.tagId?.let { tag -> notes = notes.filter { graph.tags[it.id]?.contains(tag) == true } }
        val ids = notes.mapTo(HashSet()) { it.id }
        val edges = graph.edges.filter { (a, b) -> a != b && a in ids && b in ids }.distinct()
        val degree = HashMap<EntityId, Int>()
        edges.forEach { (a, b) ->
            degree[a] = (degree[a] ?: 0) + 1
            degree[b] = (degree[b] ?: 0) + 1
        }
        if (!filter.orphans) notes = notes.filter { (degree[it.id] ?: 0) > 0 }
        val capped = notes.size > maxNodes
        if (capped) {
            // The best-connected notes stay; ties by id, so the choice is stable.
            notes = notes.sortedWith(compareByDescending<com.behnamjalali.planb.core.model.NoteRef> { degree[it.id] ?: 0 }.thenBy { it.id }).take(maxNodes)
        }
        notes = notes.sortedBy { it.id }
        val index = notes.withIndex().associate { (i, n) -> n.id to i }
        val pairs = edges.mapNotNull { (a, b) -> val ia = index[a]; val ib = index[b]; if (ia != null && ib != null) ia to ib else null }
        val positions = GraphLayout.layout(notes.size, pairs, seed = SEED)
        val degrees = IntArray(notes.size)
        pairs.forEach { (a, b) ->
            degrees[a]++
            degrees[b]++
        }
        val nodes = notes.mapIndexed { i, n -> GraphNodeUi(n.id, n.title, positions.x[i], positions.y[i], degrees[i]) }
        return Built(nodes, pairs, capped)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NoteGraphViewModel @Inject constructor(
    links: NoteLinkRepository,
    notes: NoteRepository,
    tasks: TaskRepository,
) : ViewModel() {
    private val filter = MutableStateFlow(GraphFilter())

    private val built = combine(links.observeGraph(), filter) { graph, f -> graph to f }
        .distinctUntilChanged()
        .mapLatest { (graph, f) -> GraphBuilder.build(graph, f) }
        .flowOn(Dispatchers.Default)

    val state: StateFlow<GraphUi> = combine(
        built,
        filter,
        notes.observeNotebooks(false),
        tasks.observeTags(),
        links.observeGraph(),
    ) { b, f, notebooks, tags, graph ->
        val usedTags = graph.tags.values.flatten().toSet()
        GraphUi(
            loading = false,
            nodes = b.nodes,
            edges = b.edges,
            capped = b.capped,
            filter = f,
            notebooks = notebooks,
            tags = tags.filter { it.id in usedTags },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GraphUi())

    fun setNotebook(id: EntityId?) = filter.update { it.copy(notebookId = id) }
    fun setTag(id: EntityId?) = filter.update { it.copy(tagId = id) }
    fun setOrphans(show: Boolean) = filter.update { it.copy(orphans = show) }
}
