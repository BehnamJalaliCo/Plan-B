package com.behnamjalali.planb.core.designsystem.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Spacing scale (4dp grid). */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 48.dp

    /** Horizontal gutter for screen content. */
    val screen = 20.dp
}

object Radius {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 22.dp
    val xl = 28.dp
    val pill = 999.dp
}

object Elevation {
    val none = 0.dp
    val card = 1.dp
    val raised = 3.dp
    val floating = 8.dp
}

object IconSize {
    val xs = 16.dp
    val sm = 18.dp
    val md = 22.dp
    val lg = 28.dp
    val xl = 40.dp
    val hero = 64.dp
}

/** Minimum accessible touch target. */
val MinTouchTarget = 48.dp

object Opacity {
    const val disabled = 0.38f
    const val muted = 0.64f
    const val subtle = 0.12f
    const val hairline = 0.08f
}

internal val PlanBShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)

/**
 * Motion tokens. When [enabled] is false (user setting or system "remove
 * animations"), decorative motion collapses to [snap] while state changes stay visible.
 */
class Motion(val enabled: Boolean) {
    object Duration {
        const val short = 140
        const val medium = 240
        const val long = 380
    }

    /** Scale applied to pressed cards/buttons. */
    val pressedScale: Float get() = if (enabled) 0.975f else 1f

    fun <T> press(): AnimationSpec<T> =
        if (enabled) spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow) else snap()

    fun <T> emphasized(): AnimationSpec<T> =
        if (enabled) spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow) else snap()

    fun <T> standard(durationMillis: Int = Duration.medium): AnimationSpec<T> =
        if (enabled) tween(durationMillis) else snap()
}
