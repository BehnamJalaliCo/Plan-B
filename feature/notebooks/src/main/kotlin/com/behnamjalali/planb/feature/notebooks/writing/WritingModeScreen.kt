package com.behnamjalali.planb.feature.notebooks.writing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerProgressRing
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.WordCount
import com.behnamjalali.planb.core.model.WritingSettings
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.metaSeparator
import com.behnamjalali.planb.feature.notebooks.EditorBlock
import com.behnamjalali.planb.feature.notebooks.NoteEditorActions
import com.behnamjalali.planb.feature.notebooks.NoteEditorState
import com.behnamjalali.planb.feature.notebooks.R
import com.behnamjalali.planb.feature.notebooks.blockTypeLabel
import com.behnamjalali.planb.feature.notebooks.knowledge.NoteKnowledgeUi
import com.behnamjalali.planb.feature.notebooks.knowledge.rememberNoteLinkTransformation
import com.behnamjalali.planb.feature.notebooks.knowledge.WritingViewModel

/** Block types the writing mode edits as text; anything else is shown as a quiet placeholder. */
private val textTypes = setOf(BlockType.TEXT, BlockType.HEADING, BlockType.CHECKLIST, BlockType.BULLET, BlockType.NUMBERED, BlockType.QUOTE, BlockType.CODE)

/**
 * Distraction-free writing (Plan-B Pro #24): no toolbars, a centred column in larger type, a
 * Persian-aware word and character count, today's word goal as a ring, the streak and a quiet
 * session timer. Optional typewriter scrolling keeps the line being written mid-screen (without
 * animation when motion is reduced). It edits the same note through the editor's actions, so
 * autosave, drafts and history work as always.
 */
@Composable
fun WritingModeScreen(
    state: NoteEditorState,
    actions: NoteEditorActions,
    knowledge: NoteKnowledgeUi,
    viewModel: WritingViewModel = hiltViewModel(),
) {
    val writing by viewModel.writing.collectAsStateWithLifecycle()
    val minutes by viewModel.sessionMinutes.collectAsStateWithLifecycle()
    val counts = remember(state.blocks) {
        WordCount.of(NoteDocument(blocks = state.blocks.map { NoteBlock(it.id, it.type, it.value.text, it.checked) }))
    }
    LaunchedEffect(Unit) { viewModel.startSession(counts.words) }
    LaunchedEffect(counts.words) { viewModel.onWords(counts.words) }
    var settings by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val motion = PlanBTheme.motion
    val requesters = remember { mutableStateMapOf<String, FocusRequester>() }
    fun requester(id: String) = requesters.getOrPut(id) { FocusRequester() }

    // Typewriter scrolling: the focused block's line stays in the middle of the screen.
    val focusedIndex = state.blocks.indexOfFirst { it.id == state.focusId }
    val focusedLength = state.blocks.getOrNull(focusedIndex)?.value?.text?.length ?: 0
    LaunchedEffect(writing.typewriter, focusedIndex, focusedLength) {
        if (!writing.typewriter || focusedIndex < 0) return@LaunchedEffect
        val index = focusedIndex + 1 // after the title
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
        val viewport = layout.viewportEndOffset - layout.viewportStartOffset
        val offset = -((viewport - (item?.size ?: 0)) / 2).coerceAtLeast(0)
        if (motion.enabled) listState.animateScrollToItem(index, offset) else listState.scrollToItem(index, offset)
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            WritingStatusBar(
                words = counts.words,
                characters = counts.characters,
                writing = writing,
                today = PlannerLocals.today,
                minutes = minutes,
                onExit = { knowledge.onWritingMode(false) },
                onSettings = { settings = true },
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = Spacing.xxxl, vertical = Spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    item(key = "title") { WritingTitle(state, actions) }
                    itemsIndexed(state.blocks, key = { _, b -> b.id }) { _, block ->
                        WritingBlock(block, requester(block.id), actions)
                    }
                    // Room to scroll the last line up to the middle.
                    item(key = "end") { Spacer(Modifier.heightIn(min = 320.dp)) }
                }
            }
        }
    }
    if (settings) {
        WritingSettingsDialog(
            writing = writing,
            onGoal = viewModel::setGoal,
            onTypewriter = viewModel::setTypewriter,
            onDismiss = { settings = false },
        )
    }
}

@Composable
private fun WritingStatusBar(
    words: Int,
    characters: Int,
    writing: WritingSettings,
    today: java.time.LocalDate,
    minutes: Int,
    onExit: () -> Unit,
    onSettings: () -> Unit,
) {
    val numbers = PlannerLocals.numbers
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PlannerIconButton(Icons.Rounded.CloseFullscreen, stringResource(R.string.writing_exit), onExit)
        val goal = writing.dailyGoal
        val done = writing.wordsOn(today)
        if (goal > 0) {
            val progressText = if (done >= goal) {
                stringResource(R.string.writing_goal_done)
            } else {
                stringResource(R.string.writing_goal_progress, numbers.format(done), numbers.format(goal))
            }
            PlannerProgressRing(
                progress = (done.toFloat() / goal).coerceIn(0f, 1f),
                size = 32.dp,
                strokeWidth = 4.dp,
                contentDescription = progressText,
            )
        }
        Column(Modifier.weight(1f)) {
            val separator = metaSeparator()
            Text(
                stringResource(R.string.writing_words, numbers.format(words)) + separator + stringResource(R.string.writing_characters, numbers.format(characters)),
                style = MaterialTheme.typography.labelLarge,
                color = muted,
                maxLines = 1,
            )
            val streak = writing.streakOn(today)
            val sub = buildList {
                if (goal > 0) {
                    add(
                        if (done >= goal) {
                            stringResource(R.string.writing_goal_done)
                        } else {
                            stringResource(R.string.writing_goal_progress, numbers.format(done), numbers.format(goal))
                        },
                    )
                }
                if (streak > 0) add(stringResource(R.string.writing_streak, numbers.format(streak)))
            }.joinToString(separator)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 2)
        }
        if (writing.streakOn(today) > 0) {
            Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = PlanBTheme.colors.warning)
        }
        val sessionDescription = stringResource(R.string.writing_session_cd, numbers.format(minutes))
        Text(
            stringResource(R.string.writing_session, numbers.format(minutes)),
            style = MaterialTheme.typography.labelMedium,
            color = muted,
            modifier = Modifier.clearAndSetSemantics { contentDescription = sessionDescription },
        )
        PlannerIconButton(Icons.Rounded.Tune, stringResource(R.string.writing_settings), onSettings)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun WritingTitle(state: NoteEditorState, actions: NoteEditorActions) {
    val hint = stringResource(R.string.note_title_hint)
    var title by remember(state.titleRevision) { mutableStateOf(state.title) }
    val style = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        value = title,
        onValueChange = {
            title = it.copy(text = it.text.replace('\n', ' '))
            actions.onTitle(it)
        },
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md).semantics {
            contentDescription = hint
            heading()
        },
        decorationBox = { inner ->
            if (title.text.isEmpty()) Text(hint, style = style.copy(color = MaterialTheme.colorScheme.outline))
            inner()
        },
    )
}

@Composable
private fun WritingBlock(block: EditorBlock, focusRequester: FocusRequester, actions: NoteEditorActions) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    if (block.type == BlockType.DIVIDER) {
        HorizontalDivider(Modifier.padding(vertical = Spacing.md), color = scheme.outlineVariant)
        return
    }
    if (block.type !in textTypes) {
        // Rich blocks (images, tables, …) stay as they are; they are edited in the normal editor.
        Text(
            stringResource(R.string.writing_other_block, blockTypeLabel(block.type)),
            style = typography.bodyMedium,
            color = scheme.outline,
            modifier = Modifier.padding(vertical = Spacing.xs),
        )
        return
    }
    val body = typography.bodyLarge.copy(fontSize = typography.bodyLarge.fontSize * 1.25f, lineHeight = 1.7.em)
    val style: TextStyle = when (block.type) {
        BlockType.HEADING -> typography.headlineSmall
        BlockType.CODE -> body.copy(fontFamily = FontFamily.Monospace, fontSize = typography.bodyLarge.fontSize)
        BlockType.QUOTE -> body.copy(color = scheme.onSurfaceVariant)
        else -> body
    }.let {
        it.copy(
            color = if (block.type == BlockType.QUOTE) scheme.onSurfaceVariant else if (block.checked) scheme.outline else scheme.onSurface,
            textDecoration = if (block.type == BlockType.CHECKLIST && block.checked) TextDecoration.LineThrough else null,
        )
    }
    val prefix = when (block.type) {
        BlockType.BULLET -> "•"
        BlockType.CHECKLIST -> if (block.checked) "☑" else "☐"
        BlockType.NUMBERED -> "–"
        else -> null
    }
    var value by remember(block.id, block.revision) { mutableStateOf(block.value) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (prefix != null) {
            Text(prefix, style = style, modifier = Modifier.padding(end = Spacing.sm))
        }
        if (block.type == BlockType.QUOTE) {
            Spacer(Modifier.width(Spacing.md))
        }
        BasicTextField(
            value = value,
            onValueChange = {
                value = it
                actions.onBlock(block.id, it)
            },
            textStyle = style,
            cursorBrush = SolidColor(scheme.primary),
            visualTransformation = rememberNoteLinkTransformation(value.text),
            keyboardOptions = KeyboardOptions(
                capitalization = if (block.type == BlockType.CODE) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
                autoCorrectEnabled = block.type != BlockType.CODE,
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) actions.onFocus(block.id) }
                .onPreviewKeyEvent { event ->
                    val atStart = value.selection.start == 0 && value.selection.end == 0
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && atStart) {
                        actions.onBackspaceAtStart(block.id)
                        true
                    } else {
                        false
                    }
                },
            decorationBox = { inner ->
                Box {
                    if (value.text.isEmpty()) Text(stringResource(R.string.note_block_hint), style = style.copy(color = scheme.outline, textDecoration = null))
                    inner()
                }
            },
        )
    }
}

@Composable
private fun WritingSettingsDialog(
    writing: WritingSettings,
    onGoal: (Int) -> Unit,
    onTypewriter: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val numbers = PlannerLocals.numbers
    PlannerDialog(
        title = stringResource(R.string.writing_settings),
        onDismiss = onDismiss,
        confirmLabel = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_done),
        onConfirm = onDismiss,
        dismissLabel = "",
    ) {
        Text(stringResource(R.string.writing_goal_title), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            WritingSettings.GOAL_CHOICES.forEach { goal ->
                PlannerChip(
                    label = if (goal == 0) stringResource(R.string.writing_goal_off) else numbers.format(goal),
                    selected = writing.dailyGoal == goal,
                    onClick = { onGoal(goal) },
                )
            }
        }
        Spacer(Modifier.heightIn(min = Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.writing_typewriter), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.writing_typewriter_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = writing.typewriter, onCheckedChange = onTypewriter)
        }
    }
}
