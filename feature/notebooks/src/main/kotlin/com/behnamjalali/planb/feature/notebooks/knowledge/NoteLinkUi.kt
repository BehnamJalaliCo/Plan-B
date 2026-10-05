package com.behnamjalali.planb.feature.notebooks.knowledge

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Note
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.NoteLinks
import com.behnamjalali.planb.core.model.NoteRef
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.core.ui.ProBadge
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.feature.notebooks.R

/**
 * What the note editor needs for Plan-B Pro notes knowledge (#16 links and history, #24
 * writing mode). The defaults switch it all off, so the editor works without it.
 */
@Immutable
data class NoteKnowledgeUi(
    /** Current titles and state of the linked notes. */
    val refs: Map<EntityId, NoteRef> = emptyMap(),
    val backlinks: List<NoteRef> = emptyList(),
    /** The "[[" search the suggestions are for, and the notes found. */
    val suggestionsFor: String? = null,
    val suggestions: List<NoteRef> = emptyList(),
    val onSearch: (String?) -> Unit = {},
    val onInsertLink: (blockId: String, query: NoteLinks.Query, cursor: Int, note: NoteRef) -> Unit = { _, _, _, _ -> },
    val onOpenNote: (EntityId) -> Unit = {},
    val writingMode: Boolean = false,
    val onWritingMode: (Boolean) -> Unit = {},
    /** Opens the version history; null hides the menu item. */
    val onOpenHistory: (() -> Unit)? = null,
)

/** Linked notes' current state, for drawing links inside block text. */
val LocalNoteLinkRefs = compositionLocalOf<Map<EntityId, NoteRef>> { emptyMap() }

/** Ids of every note linked from the given block texts. */
fun linkedIds(texts: List<String>): Set<EntityId> = buildSet { texts.forEach { t -> NoteLinks.spans(t).forEach { add(it.noteId) } } }

/**
 * Shows link tokens as their note's current title, underlined in the link color (a deleted or
 * trashed note's in a muted, struck-through style). The caret never stops inside a link: it
 * snaps to the link's start or end.
 */
@Composable
fun rememberNoteLinkTransformation(text: String): VisualTransformation {
    if (!text.contains("[[note:")) return VisualTransformation.None
    val refs = LocalNoteLinkRefs.current
    val link = MaterialTheme.colorScheme.primary
    val gone = MaterialTheme.colorScheme.outline
    val untitled = stringResource(R.string.link_untitled)
    return remember(refs, link, gone, untitled) { NoteLinkTransformation(refs, link, gone, untitled) }
}

class NoteLinkTransformation(
    private val refs: Map<EntityId, NoteRef>,
    private val linkColor: Color,
    private val goneColor: Color,
    private val untitled: String,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val spans = NoteLinks.spans(raw)
        if (spans.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        // Shown text: start offsets of each link's label in it, and the label lengths.
        val labelStarts = IntArray(spans.size)
        val labelLengths = IntArray(spans.size)
        val shown = buildAnnotatedString {
            var at = 0
            spans.forEachIndexed { i, span ->
                append(text.subSequence(at, span.start))
                val ref = refs[span.noteId]
                val available = ref != null && !ref.trashed
                val label = (ref?.title ?: span.title).ifBlank { untitled }
                labelStarts[i] = length
                labelLengths[i] = label.length
                pushStyle(
                    SpanStyle(
                        color = if (available) linkColor else goneColor,
                        fontWeight = if (available) FontWeight.Medium else null,
                        textDecoration = if (available) TextDecoration.Underline else TextDecoration.LineThrough,
                    ),
                )
                append(label)
                pop()
                at = span.end
            }
            append(text.subSequence(at, raw.length))
        }
        return TransformedText(shown, LinkOffsets(spans, labelStarts, labelLengths, raw.length, shown.length))
    }

    override fun equals(other: Any?): Boolean =
        other is NoteLinkTransformation && other.refs == refs && other.linkColor == linkColor && other.goneColor == goneColor && other.untitled == untitled

    override fun hashCode(): Int = refs.hashCode() * 31 + linkColor.hashCode()
}

/** Maps between the raw text (with link tokens) and the shown text (with titles). */
internal class LinkOffsets(
    private val spans: List<NoteLinks.Span>,
    private val labelStarts: IntArray,
    private val labelLengths: IntArray,
    private val rawLength: Int,
    private val shownLength: Int,
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        var delta = 0
        spans.forEachIndexed { i, span ->
            when {
                offset <= span.start -> return (offset + delta).coerceIn(0, shownLength)
                offset < span.end -> return labelStarts[i] + labelLengths[i]
                else -> delta += labelLengths[i] - (span.end - span.start)
            }
        }
        return (offset + delta).coerceIn(0, shownLength)
    }

    override fun transformedToOriginal(offset: Int): Int {
        var delta = 0
        spans.forEachIndexed { i, span ->
            val start = labelStarts[i]
            val end = start + labelLengths[i]
            when {
                offset <= start -> return (offset - delta).coerceIn(0, rawLength)
                offset < end -> return span.end
                else -> delta = end - span.end
            }
        }
        return (offset - delta).coerceIn(0, rawLength)
    }
}

/**
 * Shown above the block toolbar while the caret is in a "[[" search (the note picker) or on a
 * link ("Open …"). Without Pro, a "[[" shows one line that explains links and opens the Pro
 * screen when tapped; the brackets stay plain text.
 */
@Composable
fun NoteLinkAssist(
    blockId: String?,
    value: TextFieldValue?,
    knowledge: NoteKnowledgeUi,
    modifier: Modifier = Modifier,
) {
    val access = LocalProAccess.current
    val cursor = value?.selection?.takeIf { it.collapsed }?.start
    val query = if (value != null && cursor != null) NoteLinks.pendingQuery(value.text, cursor) else null
    val onLink = if (value != null && cursor != null && query == null) NoteLinks.spanAt(value.text, cursor) else null
    val searchText = query?.text.takeIf { access.isPro }
    LaunchedEffect(searchText, query != null) { knowledge.onSearch(searchText) }
    if (blockId == null || value == null || cursor == null) return
    when {
        query != null && !access.isPro -> AssistSurface(modifier) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PlannerChip(
                    label = stringResource(R.string.link_pro_hint),
                    selected = false,
                    onClick = { access.openPaywall(ProFeature.NOTE_LINKS) },
                    icon = Icons.Rounded.Link,
                )
                ProBadge()
            }
        }
        query != null -> AssistSurface(modifier) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
                val ready = knowledge.suggestionsFor == query.text
                Text(
                    when {
                        query.text.isBlank() -> stringResource(R.string.link_picker_title)
                        ready && knowledge.suggestions.isEmpty() -> stringResource(R.string.link_picker_empty, query.text)
                        else -> stringResource(R.string.link_picker_hint)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = Spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (ready) {
                        knowledge.suggestions.forEach { note ->
                            PlannerChip(
                                label = note.title.ifBlank { stringResource(R.string.link_untitled) },
                                selected = false,
                                onClick = { knowledge.onInsertLink(blockId, query, cursor, note) },
                                icon = Icons.AutoMirrored.Rounded.Note,
                            )
                        }
                    }
                }
            }
        }
        onLink != null -> AssistSurface(modifier) {
            val ref = knowledge.refs[onLink.noteId]
            val available = ref != null && !ref.trashed
            val title = (ref?.title ?: onLink.title).ifBlank { stringResource(R.string.link_untitled) }
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
                PlannerChip(
                    label = when {
                        ref == null -> stringResource(R.string.link_missing)
                        ref.trashed -> stringResource(R.string.link_trashed)
                        else -> stringResource(R.string.link_open, title)
                    },
                    selected = false,
                    onClick = { if (available) knowledge.onOpenNote(onLink.noteId) },
                    icon = if (available) Icons.AutoMirrored.Rounded.OpenInNew else Icons.Rounded.LinkOff,
                )
            }
        }
    }
}

@Composable
private fun AssistSurface(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

/**
 * The end of a note: the notes it links to and the notes that link to it ("Linked from"),
 * as chips that open them. Only for Plan-B Pro; free users see the note as before.
 */
fun LazyListScope.noteLinkSections(knowledge: NoteKnowledgeUi, linkIds: Set<EntityId>, isPro: Boolean) {
    if (!isPro) return
    val outgoing = linkIds.mapNotNull { knowledge.refs[it] }.filter { !it.trashed }
    if (outgoing.isNotEmpty()) {
        item(key = "knowledge_links") { LinkChips(stringResource(R.string.links_title), outgoing, knowledge.onOpenNote) }
    }
    if (knowledge.backlinks.isNotEmpty()) {
        item(key = "knowledge_backlinks") { LinkChips(stringResource(R.string.backlinks_title), knowledge.backlinks, knowledge.onOpenNote) }
    }
}

@Composable
private fun LinkChips(title: String, notes: List<NoteRef>, onOpen: (EntityId) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = Spacing.md)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PlannerSectionHeader(title, trailing = null)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            notes.forEach { note ->
                val label = note.title.ifBlank { stringResource(R.string.link_untitled) }
                val description = stringResource(R.string.link_cd, label)
                PlannerChip(
                    label = label,
                    selected = false,
                    onClick = { onOpen(note.id) },
                    icon = Icons.AutoMirrored.Rounded.Note,
                    modifier = Modifier.semantics { contentDescription = description },
                )
            }
        }
    }
}

/** A short, single-line title for lists. */
@Composable
internal fun NoteTitleText(title: String, modifier: Modifier = Modifier) {
    Text(
        title.ifBlank { stringResource(R.string.link_untitled) },
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
