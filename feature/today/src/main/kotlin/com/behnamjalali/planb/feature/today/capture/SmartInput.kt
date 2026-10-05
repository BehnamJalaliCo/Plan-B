package com.behnamjalali.planb.feature.today.capture

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.MinTouchTarget
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.nlp.QuickAddKind
import com.behnamjalali.planb.core.nlp.QuickAddPart
import com.behnamjalali.planb.core.nlp.QuickAddResult
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProBadge
import com.behnamjalali.planb.core.ui.metaSeparator
import com.behnamjalali.planb.core.ui.priorityLabel
import com.behnamjalali.planb.core.ui.recurrenceSummary
import com.behnamjalali.planb.core.ui.reminderLabel
import com.behnamjalali.planb.feature.today.R

/*
 * Plan-B Pro #1: what Quick Capture understood from the text, as chips under the field and a
 * subtle highlight inside it. Tapping a chip keeps its words as plain text.
 */

/** Highlights the recognized parts of the text; the text itself is unchanged. */
class PartsHighlight(private val parts: List<QuickAddPart>, private val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val styled = AnnotatedString.Builder(text).apply {
            // Parts come from the same text; skip any that no longer line up (typing ahead).
            parts.filter { it.end <= raw.length && raw.substring(it.start, it.end) == it.text }.forEach {
                addStyle(SpanStyle(background = color, fontWeight = FontWeight.Medium), it.start, it.end)
            }
        }.toAnnotatedString()
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean = other is PartsHighlight && other.parts == parts && other.color == color

    override fun hashCode(): Int = parts.hashCode() * 31 + color.hashCode()
}

@Composable
fun rememberPartsHighlight(result: QuickAddResult?): VisualTransformation {
    val color = MaterialTheme.colorScheme.tertiaryContainer
    val parts = result?.parts.orEmpty()
    return remember(parts, color) { if (parts.isEmpty()) VisualTransformation.None else PartsHighlight(parts, color) }
}

private fun partIcon(kind: QuickAddKind): ImageVector = when (kind) {
    QuickAddKind.DATE -> Icons.Rounded.Event
    QuickAddKind.TIME -> Icons.Rounded.Schedule
    QuickAddKind.RECURRENCE -> Icons.Rounded.Repeat
    QuickAddKind.PRIORITY -> Icons.Rounded.Flag
    QuickAddKind.TAG -> Icons.AutoMirrored.Rounded.Label
    QuickAddKind.PROJECT -> Icons.Rounded.Folder
    QuickAddKind.DEADLINE -> Icons.Rounded.HourglassBottom
    QuickAddKind.DURATION -> Icons.Rounded.Timer
    QuickAddKind.REMINDER -> Icons.Rounded.NotificationsActive
}

/** The meaning of a part in the user's language, calendar and digits. */
@Composable
fun partLabel(part: QuickAddPart, result: QuickAddResult): String {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    return when (part.kind) {
        QuickAddKind.DATE -> result.date?.let { formatter.relativeDate(it, today) } ?: part.text
        QuickAddKind.TIME -> result.time?.let { time ->
            val date = result.date
            if (date != null && date != today) formatter.relativeDate(date, today) + metaSeparator() + formatter.time(time) else formatter.time(time)
        } ?: part.text
        QuickAddKind.RECURRENCE -> recurrenceSummary(result.recurrence)
        QuickAddKind.PRIORITY -> result.priority?.let { priorityLabel(it) } ?: part.text
        QuickAddKind.TAG -> part.text
        QuickAddKind.PROJECT -> result.project?.name ?: part.text
        QuickAddKind.DEADLINE -> result.deadline?.let { stringResource(R.string.capture_part_deadline, formatter.relativeDate(it, today)) } ?: part.text
        QuickAddKind.DURATION -> result.durationMinutes?.let { stringResource(R.string.capture_part_duration, formatter.duration(it)) } ?: part.text
        QuickAddKind.REMINDER -> reminderLabel(result.reminderMinutesBefore)
    }
}

/** Chips for every recognized part; each one, when tapped, keeps its words as text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParsedParts(result: QuickAddResult, onDismiss: (QuickAddPart) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        result.parts.forEach { part ->
            val label = partLabel(part, result)
            ParsedPartChip(
                icon = partIcon(part.kind),
                label = label,
                description = stringResource(R.string.capture_part_remove, part.text, label),
                onClick = { onDismiss(part) },
            )
        }
    }
}

@Composable
private fun ParsedPartChip(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .defaultMinSize(minHeight = MinTouchTarget)
            .semantics(mergeDescendants = true) { contentDescription = description },
        shape = RoundedCornerShape(Radius.pill),
        color = scheme.tertiaryContainer,
        contentColor = scheme.onTertiaryContainer,
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(start = Spacing.md, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.xs))
            Spacer(Modifier.width(Spacing.xs))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(Spacing.xs))
            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(IconSize.xs))
        }
    }
}

/** For free users: what smart input does, opening the Pro screen when tapped. */
@Composable
fun SmartInputTeaser(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(IconSize.sm))
        Spacer(Modifier.width(Spacing.sm))
        Text(
            stringResource(R.string.capture_smart_teaser),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.sm))
        ProBadge()
    }
}
