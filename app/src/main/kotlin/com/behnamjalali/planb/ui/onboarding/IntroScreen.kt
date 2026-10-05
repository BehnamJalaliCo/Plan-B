package com.behnamjalali.planb.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.behnamjalali.planb.R
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.rememberPlannerHaptics
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.ui.PlannerLocals
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** The three intro slides. */
internal const val INTRO_PAGES = 3

private class IntroPage(val title: Int, val body: Int)

private val introPages = listOf(
    IntroPage(R.string.onboarding_plan_title, R.string.onboarding_plan_body),
    IntroPage(R.string.onboarding_grow_title, R.string.onboarding_grow_body),
    IntroPage(R.string.onboarding_private_title, R.string.onboarding_private_body),
)

/**
 * Three slides, each with an animated scene drawn in Compose. Scene layers move at their own
 * depth while paging (parallax, mirrored for right-to-left), and a scene plays its staggered
 * entrance the first time its page settles. Skip and Get started both finish via [onDone].
 */
@Composable
internal fun IntroScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val pager = rememberPagerState { INTRO_PAGES }
    val scope = rememberCoroutineScope()
    val motion = PlanBTheme.motion
    val haptics = rememberPlannerHaptics()
    val direction = LocalLayoutDirection.current
    val scenes = remember { List(INTRO_PAGES) { Animatable(0f) } }
    val first by remember { derivedStateOf { pager.currentPage == 0 } }
    val last by remember { derivedStateOf { pager.currentPage == INTRO_PAGES - 1 } }

    LaunchedEffect(pager, motion.enabled) {
        // A scene starts as soon as its page is on its way in, and plays once.
        snapshotFlow { pager.targetPage }.collect { page ->
            val scene = scenes[page]
            if (scene.isRunning || scene.value >= 1f) return@collect
            launch { if (motion.enabled) scene.animateTo(1f, tween(SCENE_MS, easing = LinearEasing)) else scene.snapTo(1f) }
        }
    }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.drop(1).collect { haptics.tick() } }
    fun go(page: Int) {
        scope.launch { if (motion.enabled) pager.animateScrollToPage(page) else pager.scrollToPage(page) }
    }

    Column(modifier.fillMaxSize().systemBarsPadding().testTag("onboarding_intro")) {
        Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), contentAlignment = Alignment.CenterEnd) {
            val skipAlpha by animateFloatAsState(if (last) 0f else 1f, motion.standard(), label = "skip")
            TextButton(onClick = onDone, enabled = !last, modifier = Modifier.graphicsLayer { alpha = skipAlpha }.testTag("onboarding_skip")) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { page ->
            IntroPageContent(page, pager, scenes[page], direction)
        }
        val numbers = PlannerLocals.numbers
        val pageLabel = stringResource(R.string.onboarding_page, numbers.format(pager.currentPage + 1), numbers.format(INTRO_PAGES))
        PageIndicator(
            pager,
            direction,
            Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.md).semantics { contentDescription = pageLabel },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val backAlpha by animateFloatAsState(if (first) 0f else 1f, motion.standard(), label = "back")
            TextButton(
                onClick = { go(pager.currentPage - 1) },
                enabled = !first,
                modifier = Modifier.graphicsLayer { alpha = backAlpha }.testTag("onboarding_back"),
            ) { Text(stringResource(R.string.onboarding_back)) }
            PlannerButton(
                text = stringResource(if (last) R.string.onboarding_start else R.string.onboarding_next),
                onClick = { if (last) onDone() else go(pager.currentPage + 1) },
                modifier = Modifier.widthIn(min = 132.dp).testTag("onboarding_next"),
            )
        }
    }
}

@Composable
private fun IntroPageContent(page: Int, pager: PagerState, scene: Animatable<Float, AnimationVector1D>, direction: LayoutDirection) {
    val content = introPages[page]
    // Clipped, so the slower background layers of neighbouring pages never show at the edges.
    Column(Modifier.fillMaxSize().clipToBounds().padding(horizontal = Spacing.screen), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            val side = min(min(maxWidth, maxHeight), 360.dp)
            // Scenes are illustrations: drawn at a fixed scale whatever the font size.
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                Box(Modifier.size(side)) {
                    when (page) {
                        0 -> PlanScene(scene, pager, page, direction)
                        1 -> GrowScene(scene, pager, page, direction)
                        else -> PrivateScene(scene, pager, page, direction)
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
        Text(
            stringResource(content.title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.parallax(pager, page, (-36).dp, direction).semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            stringResource(content.body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp).parallax(pager, page, (-64).dp, direction),
        )
        if (page == INTRO_PAGES - 1) {
            Spacer(Modifier.height(Spacing.lg))
            ProNote(scene, Modifier.parallax(pager, page, (-90).dp, direction))
        }
        Spacer(Modifier.height(Spacing.sm))
    }
}

/** A quiet mention of Plan-B Pro: no prices, no call to action. */
@Composable
private fun ProNote(scene: Animatable<Float, AnimationVector1D>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.graphicsLayer {
            val p = scene.value.window(0.82f, 1f)
            alpha = p
            translationY = (1f - p) * 8.dp.toPx()
        }.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(50), color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer) {
            Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.onboarding_pro_badge), style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(stringResource(R.string.onboarding_pro_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/** Dots that stretch into a pill for the current page, following the finger while paging. */
@Composable
private fun PageIndicator(pager: PagerState, direction: LayoutDirection, modifier: Modifier = Modifier) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outlineVariant
    val dot = 8.dp
    val stretch = 18.dp
    val gap = 6.dp
    Canvas(modifier.size(width = dot * INTRO_PAGES + stretch + gap * (INTRO_PAGES - 1), height = dot)) {
        val position = pager.currentPage + pager.currentPageOffsetFraction
        val d = dot.toPx()
        val g = gap.toPx()
        var x = 0f
        for (i in 0 until INTRO_PAGES) {
            val a = (1f - kotlin.math.abs(position - i)).coerceIn(0f, 1f)
            val w = d + stretch.toPx() * a
            val left = if (direction == LayoutDirection.Rtl) size.width - x - w else x
            drawRoundRect(lerp(inactive, active, a), topLeft = Offset(left, 0f), size = Size(w, d), cornerRadius = CornerRadius(d / 2f))
            x += w + g
        }
    }
}

private const val SCENE_MS = 1_500
