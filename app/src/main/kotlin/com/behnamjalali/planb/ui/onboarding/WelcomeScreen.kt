package com.behnamjalali.planb.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.R
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The welcome: the "B" assembles from its layers (stem rising, lobes springing in, a light
 * sweep, a breathing glow), then the wordmark, the tagline and the Start button. About two
 * seconds; a tap anywhere shows the end state at once. With reduced motion everything simply
 * fades in. Shown once per visit: coming back (e.g. after a rotation) shows the end state.
 */
@Composable
internal fun WelcomeScreen(onStart: () -> Unit, modifier: Modifier = Modifier) {
    val motion = PlanBTheme.motion
    var played by rememberSaveable { mutableStateOf(false) }
    val timeline = remember { WelcomeTimeline(if (played) 1f else 0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(timeline) {
        if (!played) {
            if (motion.enabled) timeline.play() else timeline.fadeIn()
            played = true
        }
    }
    val skipLabel = stringResource(R.string.onboarding_welcome_skip_animation)
    Box(
        modifier
            .fillMaxSize()
            .testTag("onboarding_welcome")
            .clickable(enabled = !timeline.actionReady, interactionSource = null, indication = null, onClickLabel = skipLabel) {
                scope.launch {
                    timeline.finish()
                    played = true
                }
            }
            .systemBarsPadding(),
    ) {
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = Spacing.screen).padding(bottom = Spacing.huge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AssemblingMark(timeline, Modifier.size(LogoSize))
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.entrance(timeline.wordmark, 14.dp, scaleFrom = 0.92f),
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(R.string.onboarding_welcome_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.entrance(timeline.tagline, 18.dp),
            )
        }
        PlannerButton(
            text = stringResource(R.string.onboarding_welcome_start),
            onClick = onStart,
            enabled = timeline.actionReady,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(Spacing.screen)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .entrance(timeline.action, 24.dp)
                .testTag("onboarding_welcome_start"),
        )
    }
}

/**
 * The welcome's animated values (0 → 1 each; springs overshoot a little). [actionReady]
 * turns on when the Start button appears; until then a tap skips to the end.
 */
@Stable
internal class WelcomeTimeline(initial: Float) {
    val glow = Animatable(initial)
    val stem = Animatable(initial)
    val upperLobe = Animatable(initial)
    val lowerLobe = Animatable(initial)
    val shadow = Animatable(initial)

    /** Position of the light sweep across the mark; at rest (0 or 1) it is off the mark. */
    val sweep = Animatable(initial)
    val wordmark = Animatable(initial)
    val tagline = Animatable(initial)
    val action = Animatable(initial)
    var actionReady by mutableStateOf(initial >= 1f)
        private set

    private val all = listOf(glow, stem, upperLobe, lowerLobe, shadow, sweep, wordmark, tagline, action)

    /** The full sequence, about two seconds. */
    suspend fun play() = coroutineScope {
        launch { glow.animateTo(1f, tween(900, easing = LinearOutSlowInEasing)) }
        launch { delay(120); stem.animateTo(1f, EntranceSpring) }
        launch { delay(320); upperLobe.animateTo(1f, LobeSpring) }
        launch { delay(470); lowerLobe.animateTo(1f, LobeSpring) }
        launch { delay(720); shadow.animateTo(1f, tween(500)) }
        launch { delay(860); sweep.animateTo(1f, tween(760, easing = FastOutSlowInEasing)) }
        launch { delay(960); wordmark.animateTo(1f, EntranceSpring) }
        launch { delay(1180); tagline.animateTo(1f, EntranceSpring) }
        launch {
            delay(1520)
            actionReady = true
            action.animateTo(1f, EntranceSpring)
        }
    }

    /** Reduced motion: one plain fade, no movement and no sweep. */
    suspend fun fadeIn() = coroutineScope {
        actionReady = true
        sweep.snapTo(1f)
        all.filter { it !== sweep }.forEach { launch { it.animateTo(1f, tween(REDUCED_FADE_MS)) } }
    }

    /** Jumps to the end state (a tap while it plays). */
    suspend fun finish() = coroutineScope {
        actionReady = true
        all.forEach { launch { it.snapTo(1f) } }
    }
}

/** The "B" built from its layers over a breathing glow, with a light sweep across it. */
@Composable
private fun AssemblingMark(timeline: WelcomeTimeline, modifier: Modifier = Modifier) {
    val layers = rememberBrandLayers()
    val breathing = rememberBreathing(PlanBTheme.motion.enabled)
    val colors = MaterialTheme.colorScheme
    val warm = colors.tertiary
    val cool = colors.primary
    Box(modifier) {
        // Glow: two soft color fields drifting slightly out of phase.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val b = breathing.value
                    val s = 1.95f + 0.12f * b
                    scaleX = s
                    scaleY = s
                    alpha = timeline.glow.value * (0.85f + 0.15f * b)
                }
                .drawWithCache {
                    // Both fields stay inside the box: a layer does not draw outside its bounds.
                    val r = size.minDimension * 0.44f
                    val coolCenter = Offset(size.width * 0.46f, size.height * 0.47f)
                    val warmCenter = Offset(size.width * 0.55f, size.height * 0.55f)
                    val coolBrush = Brush.radialGradient(listOf(cool.copy(alpha = 0.30f), cool.copy(alpha = 0f)), coolCenter, r)
                    val warmBrush = Brush.radialGradient(listOf(warm.copy(alpha = 0.24f), warm.copy(alpha = 0f)), warmCenter, r * 0.9f)
                    onDrawBehind {
                        drawCircle(coolBrush, r, coolCenter)
                        drawCircle(warmBrush, r * 0.9f, warmCenter)
                    }
                },
        )
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithCache {
                    val band = size.width * 0.42f
                    val brush = Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.6f),
                        1f to Color.Transparent,
                        start = Offset.Zero,
                        end = Offset(band, band * 0.45f),
                    )
                    onDrawWithContent {
                        drawContent()
                        val p = timeline.sweep.value
                        if (p > 0f && p < 1f) {
                            // Only where the mark is drawn (SrcAtop), moving across it.
                            val x = -band * 1.5f + (size.width + band * 2f) * p
                            translate(left = x) { drawRect(brush, topLeft = Offset(-x, 0f), size = size, blendMode = BlendMode.SrcAtop) }
                        }
                    }
                },
        ) {
            val fill = Modifier.matchParentSize()
            BrandLayer(layers.shadow, fill.graphicsLayer { alpha = timeline.shadow.value.coerceIn(0f, 1f) })
            BrandLayer(
                layers.stem,
                fill.graphicsLayer {
                    val p = timeline.stem.value
                    alpha = (p * 1.5f).coerceIn(0f, 1f)
                    translationY = (1f - p) * size.height * 0.22f
                },
            )
            BrandLayer(
                layers.upperLobe,
                fill.graphicsLayer {
                    val p = timeline.upperLobe.value
                    alpha = (p * 1.6f).coerceIn(0f, 1f)
                    translationX = (1f - p) * size.width * 0.3f
                    translationY = -(1f - p) * size.height * 0.08f
                    rotationZ = (1f - p) * -16f
                },
            )
            BrandLayer(
                layers.lowerLobe,
                fill.graphicsLayer {
                    val p = timeline.lowerLobe.value
                    alpha = (p * 1.6f).coerceIn(0f, 1f)
                    translationX = (1f - p) * size.width * 0.34f
                    translationY = (1f - p) * size.height * 0.1f
                    rotationZ = (1f - p) * 12f
                },
            )
        }
    }
}

/** 0 ↔ 1, slowly and forever while motion is on (paused in tests by the infinite-animation policy). */
@Composable
private fun rememberBreathing(enabled: Boolean): State<Float> {
    if (!enabled) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "breathing").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
}

private val LogoSize = 208.dp
private const val REDUCED_FADE_MS = 280
