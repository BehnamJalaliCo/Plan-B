package com.behnamjalali.planb.feature.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.common.todayFlow
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.EisenhowerMatrix
import com.behnamjalali.planb.core.model.EisenhowerQuadrant
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.PlannerTaskCard
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProGate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EisenhowerUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.MIN,
    val thresholdDays: Int = EisenhowerMatrix.DEFAULT_THRESHOLD,
    val quadrants: Map<EisenhowerQuadrant, List<Task>> = emptyMap(),
)

enum class EisenhowerMessage { KeptUrgent, Failed }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EisenhowerViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val threshold = savedState.getStateFlow(KEY_THRESHOLD, EisenhowerMatrix.DEFAULT_THRESHOLD)

    private val _messages = MutableSharedFlow<EisenhowerMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<EisenhowerMessage> = _messages

    val uiState: StateFlow<EisenhowerUiState> = time.todayFlow()
        .flatMapLatest { today -> tasks.observeTasks(TaskFilter(view = TaskView.ALL, today = today)).map { today to it } }
        .combine(threshold) { (today, list), days ->
            EisenhowerUiState(
                loading = false,
                today = today,
                thresholdDays = days,
                quadrants = EisenhowerQuadrant.entries.associateWith { emptyList<Task>() } +
                    list.groupBy { EisenhowerMatrix.quadrantOf(it, today, days) },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EisenhowerUiState())

    fun setThreshold(days: Int) {
        savedState[KEY_THRESHOLD] = days.coerceIn(EisenhowerMatrix.THRESHOLDS)
    }

    /** Changes priority and planned date so [taskId] lands in [target] (see [EisenhowerMatrix.move]). */
    fun move(taskId: EntityId, target: EisenhowerQuadrant) {
        viewModelScope.launch {
            runCatchingSafely {
                val task = tasks.getTask(taskId) ?: return@runCatchingSafely
                val today = time.today()
                val days = threshold.value
                if (EisenhowerMatrix.quadrantOf(task, today, days) == target) return@runCatchingSafely
                val move = EisenhowerMatrix.move(task, target, today, days)
                tasks.save(move.task)
                if (move.keptUrgentByDeadline) _messages.tryEmit(EisenhowerMessage.KeptUrgent)
            }.onFailure { _messages.tryEmit(EisenhowerMessage.Failed) }
        }
    }

    fun setCompleted(taskId: EntityId, completed: Boolean) {
        viewModelScope.launch {
            runCatchingSafely { tasks.setCompleted(taskId, completed) }.onFailure { _messages.tryEmit(EisenhowerMessage.Failed) }
        }
    }

    private companion object {
        const val KEY_THRESHOLD = "eisenhower_threshold"
    }
}

@Composable
fun EisenhowerDestination(
    onBack: () -> Unit,
    onOpenTask: (EntityId) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: EisenhowerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(
                resources.getString(if (message == EisenhowerMessage.KeptUrgent) R.string.eisenhower_kept_urgent else R.string.tasks_error),
            )
        }
    }
    EisenhowerScreen(state, onBack, onOpenTask, viewModel::setThreshold, viewModel::move, viewModel::setCompleted)
}

@Composable
private fun quadrantTitle(q: EisenhowerQuadrant): String = stringResource(
    when (q) {
        EisenhowerQuadrant.DO -> R.string.eisenhower_do
        EisenhowerQuadrant.SCHEDULE -> R.string.eisenhower_schedule
        EisenhowerQuadrant.DELEGATE -> R.string.eisenhower_delegate
        EisenhowerQuadrant.ELIMINATE -> R.string.eisenhower_eliminate
    },
)

@Composable
private fun quadrantSubtitle(q: EisenhowerQuadrant): String = stringResource(
    when (q) {
        EisenhowerQuadrant.DO -> R.string.eisenhower_do_sub
        EisenhowerQuadrant.SCHEDULE -> R.string.eisenhower_schedule_sub
        EisenhowerQuadrant.DELEGATE -> R.string.eisenhower_delegate_sub
        EisenhowerQuadrant.ELIMINATE -> R.string.eisenhower_eliminate_sub
    },
)

private fun quadrantAccent(q: EisenhowerQuadrant): AccentColor = when (q) {
    EisenhowerQuadrant.DO -> AccentColor.ROSE
    EisenhowerQuadrant.SCHEDULE -> AccentColor.POWDER_BLUE
    EisenhowerQuadrant.DELEGATE -> AccentColor.SAND
    EisenhowerQuadrant.ELIMINATE -> AccentColor.SLATE
}

/**
 * The Eisenhower matrix (Plan-B Pro #13): urgent quadrants on the reading start side (right in
 * Persian), important ones on top. A task is moved by holding and dragging it onto another
 * quadrant, or through its accessibility actions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EisenhowerScreen(
    state: EisenhowerUiState,
    onBack: () -> Unit,
    onOpenTask: (EntityId) -> Unit,
    onThreshold: (Int) -> Unit,
    onMove: (EntityId, EisenhowerQuadrant) -> Unit,
    onToggleComplete: (EntityId, Boolean) -> Unit,
) {
    val numbers = PlannerLocals.numbers
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(title = stringResource(R.string.eisenhower_title), onBack = onBack)
        ProGate(ProFeature.EISENHOWER, modifier = Modifier.padding(Spacing.screen)) {
            if (state.loading) {
                PlannerLoadingState()
                return@ProGate
            }
            // Drag state, in root coordinates.
            val bounds = remember { mutableStateMapOf<EisenhowerQuadrant, Rect>() }
            var dragged by remember { mutableStateOf<Task?>(null) }
            var dragPosition by remember { mutableStateOf(Offset.Zero) }
            var boxOrigin by remember { mutableStateOf(Offset.Zero) }
            val target = dragged?.let { bounds.entries.firstOrNull { (_, r) -> r.contains(dragPosition) }?.key }

            Column(Modifier.fillMaxSize()) {
                Text(
                    stringResource(R.string.eisenhower_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screen),
                )
                FlowRow(
                    Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    listOf(1, 2, 3, 7).forEach { days ->
                        PlannerChip(stringResource(R.string.eisenhower_threshold, numbers.format(days)), state.thresholdDays == days, { onThreshold(days) })
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { boxOrigin = it.positionInRoot() }) {
                    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf(
                            EisenhowerQuadrant.DO to EisenhowerQuadrant.SCHEDULE,
                            EisenhowerQuadrant.DELEGATE to EisenhowerQuadrant.ELIMINATE,
                        ).forEach { (start, end) ->
                            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                listOf(start, end).forEach { q ->
                                    Quadrant(
                                        quadrant = q,
                                        tasks = state.quadrants[q].orEmpty(),
                                        highlighted = target == q && dragged?.let { EisenhowerMatrix.quadrantOf(it, state.today, state.thresholdDays) } != q,
                                        onBounds = { bounds[q] = it },
                                        onOpenTask = onOpenTask,
                                        onMove = onMove,
                                        onToggleComplete = onToggleComplete,
                                        onDragStart = { task, at ->
                                            dragged = task
                                            dragPosition = at
                                        },
                                        onDrag = { dragPosition += it },
                                        onDragEnd = {
                                            val task = dragged
                                            val drop = bounds.entries.firstOrNull { (_, r) -> r.contains(dragPosition) }?.key
                                            dragged = null
                                            if (task != null && drop != null) onMove(task.id, drop)
                                        },
                                        onDragCancel = { dragged = null },
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                    )
                                }
                            }
                        }
                    }
                    // The task follows the finger while it is dragged.
                    dragged?.let { task ->
                        Surface(
                            shape = RoundedCornerShape(Radius.md),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .graphicsLayer {
                                    translationX = dragPosition.x - boxOrigin.x - 24.dp.toPx()
                                    translationY = dragPosition.y - boxOrigin.y - 56.dp.toPx()
                                }
                                .widthIn(max = 220.dp)
                                .shadow(8.dp, RoundedCornerShape(Radius.md)),
                        ) {
                            Text(task.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(Spacing.md))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Quadrant(
    quadrant: EisenhowerQuadrant,
    tasks: List<Task>,
    highlighted: Boolean,
    onBounds: (Rect) -> Unit,
    onOpenTask: (EntityId) -> Unit,
    onMove: (EntityId, EisenhowerQuadrant) -> Unit,
    onToggleComplete: (EntityId, Boolean) -> Unit,
    onDragStart: (Task, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tones = PlanBTheme.colors.accent(quadrantAccent(quadrant))
    val numbers = PlannerLocals.numbers
    val others = EisenhowerQuadrant.entries.filter { it != quadrant }
    val otherTitles = others.map { quadrantTitle(it) }
    val moveLabels = otherTitles.map { stringResource(R.string.eisenhower_move_to, it) }
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = tones.container.copy(alpha = 0.55f),
        border = if (highlighted) BorderStroke(2.dp, tones.strong) else null,
        modifier = modifier.onGloballyPositioned { onBounds(it.boundsInRoot()) },
    ) {
        Column {
            Column(Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm).semantics(mergeDescendants = true) { heading() }) {
                Row {
                    Text(quadrantTitle(quadrant), style = MaterialTheme.typography.titleSmall, color = tones.onContainer, modifier = Modifier.weight(1f))
                    Text(numbers.format(tasks.size), style = MaterialTheme.typography.labelLarge, color = tones.onContainer)
                }
                Text(quadrantSubtitle(quadrant), style = MaterialTheme.typography.labelSmall, color = tones.onContainer.copy(alpha = 0.8f))
            }
            if (tasks.isEmpty()) {
                Text(
                    stringResource(R.string.eisenhower_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
            LazyColumn(contentPadding = PaddingValues(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(tasks, key = { it.id }) { task ->
                    var origin by remember { mutableStateOf(Offset.Zero) }
                    PlannerTaskCard(
                        task = task,
                        onToggleComplete = { onToggleComplete(task.id, it) },
                        onClick = { onOpenTask(task.id) },
                        modifier = Modifier
                            .animateItem()
                            .onGloballyPositioned { origin = it.positionInRoot() }
                            .semantics {
                                customActions = others.mapIndexed { i, q -> CustomAccessibilityAction(moveLabels[i]) { onMove(task.id, q); true } }
                            }
                            .pointerInput(task.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { onDragStart(task, origin + it) },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        onDrag(amount)
                                    },
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragCancel,
                                )
                            },
                    )
                }
            }
        }
    }
}
