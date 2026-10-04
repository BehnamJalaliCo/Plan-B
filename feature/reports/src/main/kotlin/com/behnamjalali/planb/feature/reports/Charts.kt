package com.behnamjalali.planb.feature.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.core.designsystem.theme.Spacing

/**
 * Vertical bars drawn on one Canvas, in reading order (mirrored for right-to-left). The
 * [description] is the accessible summary, so the chart never relies on color or shape alone;
 * [labels] are printed under the bars (null leaves a slot empty).
 */
@Composable
fun BarChart(
    values: List<Int>,
    labels: List<String?>,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    highlight: Int? = null,
    height: Dp = 120.dp,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val strong = MaterialTheme.colorScheme.tertiary
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val barWidth = (slot * 0.6f).coerceAtMost(28.dp.toPx())
            val radius = CornerRadius(minOf(barWidth / 2, 6.dp.toPx()))
            val rtl = layoutDirection == LayoutDirection.Rtl
            values.forEachIndexed { i, v ->
                val index = if (rtl) values.lastIndex - i else i
                val left = index * slot + (slot - barWidth) / 2
                drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), radius)
                val h = size.height * v / max
                if (h > 0) drawRoundRect(if (i == highlight) strong else color, Offset(left, size.height - h), Size(barWidth, h), radius)
            }
        }
        if (labels.any { it != null }) {
            Spacer(Modifier.height(Spacing.xs))
            Row(Modifier.fillMaxWidth()) {
                labels.forEach { label ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (label != null) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Two-part bar (for example on time vs late) that starts at the reading edge, with a legend. */
@Composable
fun SplitBar(
    first: Int,
    second: Int,
    firstLabel: String,
    secondLabel: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val firstColor = MaterialTheme.colorScheme.primary
    val secondColor = MaterialTheme.colorScheme.tertiary
    val total = (first + second).coerceAtLeast(1)
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val rtl = layoutDirection == LayoutDirection.Rtl
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(secondColor, Offset.Zero, size, radius)
            val w = size.width * first / total
            if (first > 0) drawRoundRect(firstColor, Offset(if (rtl) size.width - w else 0f, 0f), Size(w, size.height), radius)
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Legend(firstColor, firstLabel)
            Legend(secondColor, secondLabel)
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
