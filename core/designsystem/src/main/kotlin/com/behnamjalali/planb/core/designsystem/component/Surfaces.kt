package com.behnamjalali.planb.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.behnamjalali.planb.core.designsystem.theme.Elevation
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing

/** Subtle scale-down while pressed. Uses graphicsLayer so it never triggers relayout. */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val motion = PlanBTheme.motion
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) motion.pressedScale else 1f,
        animationSpec = motion.press(),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Haptics gated by the user's preference. */
class PlannerHaptics internal constructor(
    private val enabled: Boolean,
    private val perform: (HapticFeedbackType) -> Unit,
) {
    fun success() { if (enabled) perform(HapticFeedbackType.Confirm) }
    fun longPress() { if (enabled) perform(HapticFeedbackType.LongPress) }
    fun tick() { if (enabled) perform(HapticFeedbackType.SegmentTick) }
}

@Composable
fun rememberPlannerHaptics(): PlannerHaptics {
    val feedback = LocalHapticFeedback.current
    val enabled = PlanBTheme.experience.hapticsEnabled
    return remember(feedback, enabled) { PlannerHaptics(enabled) { feedback.performHapticFeedback(it) } }
}

/**
 * Premium card: soft layered surface with a hairline border and press feedback.
 * Supply [onClick] for interactive cards; long-press is optional.
 */
@Composable
fun PlannerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = RoundedCornerShape(Radius.lg),
    border: BorderStroke? = BorderStroke(Dp.Hairline, PlanBTheme.colors.cardBorder),
    tonalElevation: Dp = Elevation.none,
    shadowElevation: Dp = Elevation.card,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val clickModifier = when {
        onClick != null && onLongClick != null -> Modifier.combinedClickable(
            interactionSource = interaction,
            indication = androidx.compose.material3.ripple(),
            onClickLabel = onClickLabel,
            role = Role.Button,
            onLongClick = onLongClick,
            onClick = onClick,
        )
        onClick != null -> Modifier.clickable(
            interactionSource = interaction,
            indication = androidx.compose.material3.ripple(),
            onClickLabel = onClickLabel,
            role = Role.Button,
            onClick = onClick,
        )
        else -> Modifier
    }
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.pressScale(interaction) else Modifier),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    ) {
        Column(clickModifier.padding(contentPadding), content = content)
    }
}

/** Flat tinted container for grouping content without card chrome. */
@Composable
fun PlannerSurface(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: Shape = RoundedCornerShape(Radius.md),
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, shape = shape, color = color) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Hero surface with the controlled cinematic gradient. */
@Composable
fun PlannerHeroSurface(
    modifier: Modifier = Modifier,
    brush: Brush = PlanBTheme.colors.heroGradient,
    shape: Shape = RoundedCornerShape(Radius.xl),
    contentPadding: PaddingValues = PaddingValues(Spacing.xl),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(Dp.Hairline, PlanBTheme.colors.cardBorder),
    ) {
        Column(
            Modifier
                .background(brush)
                .padding(contentPadding),
            content = content,
        )
    }
}
