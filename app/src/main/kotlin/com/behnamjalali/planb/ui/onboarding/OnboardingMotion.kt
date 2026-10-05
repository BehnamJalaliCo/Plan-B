package com.behnamjalali.planb.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme

/*
 * Motion helpers for the first-run screens. Animated values are read only inside
 * graphicsLayer/draw lambdas, so a running animation redraws layers without recomposing.
 */

/** Entrance springs: a soft overshoot for elements sliding into place. */
internal val EntranceSpring = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow)

/** A livelier spring for the logo lobes. */
internal val LobeSpring = spring<Float>(dampingRatio = 0.58f, stiffness = 140f)

/** Linear [this] (0..1 timeline) remapped to the window [start]..[end], eased and clamped. */
internal fun Float.window(start: Float, end: Float): Float =
    FastOutSlowInEasing.transform(((this - start) / (end - start)).coerceIn(0f, 1f))

/**
 * Fades in and rises by [distance] as [progress] goes 0 → 1 (springs may overshoot 1; the
 * overshoot moves the element slightly past its place and back).
 */
internal fun Modifier.entrance(progress: Animatable<Float, AnimationVector1D>, distance: Dp, scaleFrom: Float = 1f): Modifier =
    graphicsLayer {
        val p = progress.value
        alpha = (p * 1.4f).coerceIn(0f, 1f)
        translationY = (1f - p) * distance.toPx()
        if (scaleFrom != 1f) {
            val s = scaleFrom + (1f - scaleFrom) * p
            scaleX = s
            scaleY = s
        }
    }

/**
 * Page offset of [page] relative to the pager's position: 0 when it is settled in view,
 * −1/+1 one page away. Read it only in layout/draw lambdas.
 */
internal fun PagerState.offsetOf(page: Int): Float = (currentPage - page) + currentPageOffsetFraction

/**
 * Moves a layer horizontally against its page by up to [depth] per page of scrolling, which
 * gives depth: positive values lag behind the page (background), negative values run ahead
 * (foreground), 0 moves with the page. Mirrored for right-to-left, where pages move the other way.
 */
internal fun Modifier.parallax(pager: PagerState, page: Int, depth: Dp, direction: LayoutDirection): Modifier =
    graphicsLayer {
        val sign = if (direction == LayoutDirection.Rtl) -1f else 1f
        translationX = pager.offsetOf(page) * depth.toPx() * sign
    }

/**
 * Alpha for content that appears right after the first-run flow: 0 → 1 once, or 1 straight
 * away when [active] is false or motion is reduced. Read it in a graphicsLayer.
 */
@Composable
fun rememberFadeIn(active: Boolean): State<Float> {
    val enabled = PlanBTheme.motion.enabled
    val alpha = remember { Animatable(if (active && enabled) 0f else 1f) }
    LaunchedEffect(alpha) { alpha.animateTo(1f, tween(FADE_IN_MS)) }
    return alpha.asState()
}

private const val FADE_IN_MS = 360
