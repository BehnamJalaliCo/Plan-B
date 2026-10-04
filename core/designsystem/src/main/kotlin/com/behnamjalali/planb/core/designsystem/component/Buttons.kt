package com.behnamjalali.planb.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.behnamjalali.planb.core.designsystem.theme.Elevation
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.MinTouchTarget
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing

enum class PlannerButtonStyle { Primary, Tonal, Outlined, Text, Destructive }

@Composable
fun PlannerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PlannerButtonStyle = PlannerButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(Radius.pill)
    val padding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md)
    val mod = modifier
        .defaultMinSize(minHeight = MinTouchTarget)
        .pressScale(interaction)
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.sm))
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    when (style) {
        PlannerButtonStyle.Primary -> Button(
            onClick, mod, enabled, shape = shape, contentPadding = padding, interactionSource = interaction,
        ) { content() }
        PlannerButtonStyle.Destructive -> Button(
            onClick, mod, enabled, shape = shape, contentPadding = padding, interactionSource = interaction,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) { content() }
        PlannerButtonStyle.Tonal -> FilledTonalButton(
            onClick, mod, enabled, shape = shape, contentPadding = padding, interactionSource = interaction,
        ) { content() }
        PlannerButtonStyle.Outlined -> OutlinedButton(
            onClick, mod, enabled, shape = shape, contentPadding = padding, interactionSource = interaction,
            border = BorderStroke(androidx.compose.ui.unit.Dp.Hairline, MaterialTheme.colorScheme.outline),
        ) { content() }
        PlannerButtonStyle.Text -> TextButton(
            onClick, mod, enabled, shape = shape, interactionSource = interaction,
        ) { content() }
    }
}

/** Icon button with a guaranteed 48dp target and mandatory content description. */
@Composable
fun PlannerIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = Color.Transparent,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(MinTouchTarget),
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(containerColor = containerColor, contentColor = tint),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(IconSize.md))
    }
}

@Composable
fun PlannerFAB(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.pressScale(interaction),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = Elevation.floating),
        interactionSource = interaction,
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(IconSize.lg))
    }
}
