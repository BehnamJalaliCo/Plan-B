package com.behnamjalali.planb.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.behnamjalali.planb.R
import com.behnamjalali.planb.core.datetime.MonthGrid
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.ui.PlannerLocals

/*
 * The intro scenes. Each one is a few layers driven by one 0 → 1 [Scene] value: elements
 * enter in a staggered order with a spring-like overshoot, and layers sit at different depths
 * for parallax. All animated reads happen in graphicsLayer/draw lambdas; paths and brushes are
 * built once per size in drawWithCache, so frames allocate nothing.
 */

private typealias Scene = Animatable<Float, AnimationVector1D>

/** Overshoots a little before settling, like a soft spring. */
private val Overshoot = CubicBezierEasing(0.34f, 1.45f, 0.64f, 1f)

private fun Float.pop(start: Float, end: Float): Float = Overshoot.transform(((this - start) / (end - start)).coerceIn(0f, 1f))

/**
 * One scene layer: enters between [start] and [end] of the scene (fading, moving from
 * [fromX]/[fromY] and scaling from [fromScale]), rests tilted by [tilt] degrees and moves at
 * [depth] while paging. Horizontal motion and tilt are mirrored for right-to-left.
 */
private fun Modifier.sceneLayer(
    scene: Scene,
    pager: PagerState,
    page: Int,
    direction: LayoutDirection,
    start: Float,
    end: Float,
    depth: Dp = 0.dp,
    fromX: Dp = 0.dp,
    fromY: Dp = 24.dp,
    fromScale: Float = 1f,
    tilt: Float = 0f,
): Modifier = graphicsLayer {
    val sign = if (direction == LayoutDirection.Rtl) -1f else 1f
    val t = scene.value
    val p = t.pop(start, end)
    alpha = t.window(start, start + (end - start) * 0.6f)
    translationX = (1f - p) * fromX.toPx() * sign + pager.offsetOf(page) * depth.toPx() * sign
    translationY = (1f - p) * fromY.toPx()
    if (fromScale != 1f) {
        val s = fromScale + (1f - fromScale) * p
        scaleX = s
        scaleY = s
    }
    rotationZ = tilt * sign
}

/** Soft color fields behind a scene. */
@Composable
private fun Backdrop(first: Color, second: Color, direction: LayoutDirection, modifier: Modifier = Modifier) {
    val dim = if (PlanBTheme.colors.isDark) 0.55f else 1f
    Canvas(modifier.graphicsLayer { alpha = dim }) {
        fun x(fraction: Float) = if (direction == LayoutDirection.Rtl) size.width * (1f - fraction) else size.width * fraction
        drawCircle(first, radius = size.width * 0.4f, center = Offset(x(0.56f), size.height * 0.44f))
        drawCircle(second, radius = size.width * 0.13f, center = Offset(x(0.13f), size.height * 0.82f))
        drawCircle(second, radius = size.width * 0.06f, center = Offset(x(0.9f), size.height * 0.12f))
    }
}

@Composable
private fun SceneCard(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(18.dp), content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, PlanBTheme.colors.cardBorder),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(12.dp), content = content)
    }
}

// region 1 · Plan your days

@Composable
internal fun PlanScene(scene: Scene, pager: PagerState, page: Int, direction: LayoutDirection) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth
        Backdrop(
            colors.primaryContainer.copy(alpha = 0.7f),
            colors.secondaryContainer.copy(alpha = 0.8f),
            direction,
            Modifier.matchParentSize().sceneLayer(scene, pager, page, direction, 0f, 0.35f, depth = 72.dp, fromY = 0.dp, fromScale = 0.82f),
        )
        TodayCard(
            scene,
            Modifier
                .align(Alignment.TopStart)
                .padding(start = side * 0.03f, top = side * 0.07f)
                .width(side * 0.7f)
                .sceneLayer(scene, pager, page, direction, 0.04f, 0.42f, fromY = 36.dp),
        )
        MiniCalendar(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = side * 0.01f, bottom = side * 0.02f)
                .width(side * 0.52f)
                .sceneLayer(scene, pager, page, direction, 0.24f, 0.62f, depth = (-56).dp, fromX = 44.dp, fromY = 28.dp, tilt = -3f),
            scene,
        )
    }
}

@Composable
private fun TodayCard(scene: Scene, modifier: Modifier = Modifier) {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    val colors = MaterialTheme.colorScheme
    SceneCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_today), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
            Surface(shape = RoundedCornerShape(50), color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                Text(formatter.dayMonth(today), style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        val accents = PlanBTheme.colors
        TaskLine(scene, 0.50f, 0.82f, accents.accent(AccentColor.LAVENDER).strong)
        Spacer(Modifier.height(8.dp))
        TaskLine(scene, 0.64f, 0.62f, accents.accent(AccentColor.MINT).strong)
        Spacer(Modifier.height(8.dp))
        TaskLine(scene, null, 0.74f, accents.accent(AccentColor.PEACH).strong)
    }
}

/** A task row: a check circle that fills and ticks at [checkAt] (never when null), and a text bar. */
@Composable
private fun TaskLine(scene: Scene, checkAt: Float?, length: Float, accent: Color) {
    val colors = MaterialTheme.colorScheme
    val outline = colors.outline
    val bar = colors.onSurface.copy(alpha = 0.16f)
    val onAccent = colors.surfaceContainerLowest
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(18.dp).drawWithCache {
                val tick = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.67f)
                    lineTo(size.width * 0.73f, size.height * 0.36f)
                }
                val measure = PathMeasure().apply { setPath(tick, false) }
                val partial = Path()
                val stroke = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val ring = Stroke(1.4.dp.toPx())
                onDrawBehind {
                    val at = checkAt ?: 2f
                    val c = scene.value.window(at, at + 0.14f)
                    val r = size.minDimension / 2f
                    drawCircle(outline, r - ring.width / 2f, style = ring, alpha = 1f - c)
                    if (c > 0f) {
                        drawCircle(accent, r * scene.value.pop(at, at + 0.12f).coerceAtLeast(0f))
                        partial.rewind()
                        measure.getSegment(0f, measure.length * c, partial, true)
                        drawPath(partial, onAccent, style = stroke)
                    }
                }
            },
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.weight(1f).height(14.dp).drawWithCache {
                val stroke = 1.4.dp.toPx()
                val h = 7.dp.toPx()
                onDrawBehind {
                    val c = checkAt?.let { scene.value.window(it + 0.04f, it + 0.18f) } ?: 0f
                    val w = size.width * length
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    val left = if (rtl) size.width - w else 0f
                    val top = (size.height - h) / 2f
                    drawRoundRect(bar, Offset(left, top), Size(w, h), CornerRadius(h / 2f), alpha = 1f - 0.5f * c)
                    if (c > 0f) {
                        // Strike-through, written from the start edge.
                        val from = if (rtl) size.width else 0f
                        val to = if (rtl) size.width - w * c else w * c
                        drawLine(outline, Offset(from, size.height / 2f), Offset(to, size.height / 2f), stroke, StrokeCap.Round)
                    }
                }
            },
        )
    }
}

/** This month in the user's calendar (Jalali or Gregorian) with today marked. */
@Composable
private fun MiniCalendar(modifier: Modifier, scene: Scene) {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    val colors = MaterialTheme.colorScheme
    val month = remember(formatter, today) { formatter.monthOf(today) }
    val weeks = remember(formatter, month) { MonthGrid.build(formatter.engine, month, formatter.firstDayOfWeek) }
    val cell = TextStyleSmall
    SceneCard(modifier, RoundedCornerShape(16.dp)) {
        Text(formatter.monthYear(month), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Row {
            for (day in formatter.weekdays()) {
                Text(formatter.weekdayNarrow(day), fontSize = cell, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1)
            }
        }
        for (week in weeks) {
            Row {
                for (day in week) {
                    val isToday = day.date == today
                    Box(Modifier.weight(1f).aspectRatio(1.15f), contentAlignment = Alignment.Center) {
                        if (isToday) {
                            Box(
                                Modifier.size(15.dp).graphicsLayer {
                                    val p = scene.value.pop(0.56f, 0.8f)
                                    scaleX = p
                                    scaleY = p
                                }.background(colors.primary, CircleShape),
                            )
                        }
                        Text(
                            formatter.dayNumber(day.date),
                            fontSize = cell,
                            lineHeight = 10.sp,
                            color = when {
                                isToday -> colors.onPrimary
                                day.inMonth -> colors.onSurface
                                else -> colors.onSurface.copy(alpha = 0.3f)
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private val TextStyleSmall = 8.sp

// endregion

// region 2 · Notes, habits & focus

@Composable
internal fun GrowScene(scene: Scene, pager: PagerState, page: Int, direction: LayoutDirection) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth
        Backdrop(
            colors.tertiaryContainer.copy(alpha = 0.7f),
            colors.primaryContainer.copy(alpha = 0.8f),
            direction,
            Modifier.matchParentSize().sceneLayer(scene, pager, page, direction, 0f, 0.35f, depth = 72.dp, fromY = 0.dp, fromScale = 0.82f),
        )
        NotebookPage(
            scene,
            Modifier
                .align(Alignment.TopStart)
                .padding(start = side * 0.04f, top = side * 0.06f)
                .width(side * 0.58f)
                .height(side * 0.7f)
                .sceneLayer(scene, pager, page, direction, 0.02f, 0.38f, fromY = 36.dp, tilt = -2f),
        )
        FocusDial(
            scene,
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = side * 0.02f, top = side * 0.02f)
                .size(side * 0.4f)
                .sceneLayer(scene, pager, page, direction, 0.16f, 0.5f, depth = (-40).dp, fromX = 36.dp, fromY = 0.dp, fromScale = 0.7f),
        )
        HabitRings(
            scene,
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = side * 0.02f, bottom = side * 0.06f)
                .sceneLayer(scene, pager, page, direction, 0.3f, 0.62f, depth = (-72).dp, fromY = 32.dp),
            side * 0.12f,
        )
    }
}

/** A notebook page whose lines write themselves from the start edge. */
@Composable
private fun NotebookPage(scene: Scene, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val ink = colors.onSurface.copy(alpha = 0.22f)
    val heading = colors.primary
    val rule = colors.outlineVariant.copy(alpha = 0.5f)
    val margin = colors.tertiary.copy(alpha = 0.35f)
    SceneCard(modifier) {
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val lineHeight = 7.dp.toPx()
                val gap = 15.dp.toPx()
                val top = 26.dp.toPx()
                val inset = 12.dp.toPx()
                val pen = 3.5.dp.toPx()
                onDrawBehind {
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    fun x(fromStart: Float) = if (rtl) size.width - fromStart else fromStart
                    // Margin rule and ruled lines.
                    drawLine(margin, Offset(x(inset - 5.dp.toPx()), 0f), Offset(x(inset - 5.dp.toPx()), size.height), 1.dp.toPx())
                    var y = top + lineHeight / 2f
                    while (y < size.height) {
                        drawLine(rule, Offset(0f, y + lineHeight), Offset(size.width, y + lineHeight), 0.8.dp.toPx())
                        y += gap
                    }
                    val t = scene.value
                    // Title.
                    val titleWidth = (size.width - inset) * 0.55f * t.window(0.12f, 0.24f)
                    drawPill(heading, x(inset), titleWidth, 0f, 10.dp.toPx(), rtl)
                    for (i in Lines.indices) {
                        val start = 0.24f + i * 0.09f
                        val w = t.window(start, start + 0.11f)
                        if (w <= 0f) break
                        val width = (size.width - inset) * Lines[i] * w
                        val lineTop = top + i * gap
                        drawPill(ink, x(inset), width, lineTop, lineHeight, rtl)
                        if (w < 1f) drawCircle(heading, pen, Offset(x(inset + width), lineTop + lineHeight / 2f))
                    }
                }
            },
        )
    }
}

private val Lines = floatArrayOf(0.92f, 0.74f, 0.86f, 0.58f, 0.8f, 0.42f)

/** A rounded bar [width] long, growing from [startX] towards the end edge. */
private fun DrawScope.drawPill(color: Color, startX: Float, width: Float, top: Float, height: Float, rtl: Boolean) {
    if (width <= 0f) return
    val left = if (rtl) startX - width else startX
    drawRoundRect(color, Offset(left, top), Size(width, height), CornerRadius(height / 2f))
}

/** A focus timer ring sweeping towards its goal. */
@Composable
private fun FocusDial(scene: Scene, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val track = colors.primary.copy(alpha = 0.14f)
    val arc = colors.primary
    val formatter = PlannerLocals.formatter
    Surface(modifier, shape = CircleShape, color = colors.surfaceContainerLowest, border = BorderStroke(1.dp, PlanBTheme.colors.cardBorder), shadowElevation = 6.dp) {
        Box(
            Modifier.fillMaxSize().padding(10.dp).drawWithCache {
                val stroke = Stroke(7.dp.toPx(), cap = StrokeCap.Round)
                val inset = stroke.width / 2f
                val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                onDrawBehind {
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
                    val sweep = 360f * 0.68f * scene.value.window(0.3f, 0.95f)
                    // Clockwise in left-to-right, counter-clockwise in right-to-left.
                    val signed = if (layoutDirection == LayoutDirection.Rtl) -sweep else sweep
                    if (sweep > 0f) drawArc(arc, -90f, signed, false, Offset(inset, inset), arcSize, style = stroke)
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                formatter.timer(FOCUS_MILLIS),
                style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

private const val FOCUS_MILLIS = 25 * 60 * 1000L

/** Three habit rings filling one after another; the full one gets a check. */
@Composable
private fun HabitRings(scene: Scene, modifier: Modifier, ring: Dp) {
    val tones = PlanBTheme.colors
    val habits = listOf(
        Triple(tones.accent(AccentColor.MINT), 1f, 0.42f),
        Triple(tones.accent(AccentColor.PEACH), 0.72f, 0.52f),
        Triple(tones.accent(AccentColor.POWDER_BLUE), 0.45f, 0.62f),
    )
    SceneCard(modifier, RoundedCornerShape(50)) {
        Row {
            habits.forEachIndexed { index, (accent, goal, start) ->
                if (index > 0) Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(ring).drawWithCache {
                        val stroke = Stroke(4.5.dp.toPx(), cap = StrokeCap.Round)
                        val inset = stroke.width / 2f
                        val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                        onDrawBehind {
                            drawArc(accent.container, 0f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
                            val sweep = 360f * goal * scene.value.window(start, start + 0.28f)
                            val signed = if (layoutDirection == LayoutDirection.Rtl) -sweep else sweep
                            if (sweep > 0f) drawArc(accent.strong, -90f, signed, false, Offset(inset, inset), arcSize, style = stroke)
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    if (goal >= 1f) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = accent.strong,
                            modifier = Modifier.size(ring * 0.5f).graphicsLayer {
                                val p = scene.value.pop(start + 0.26f, start + 0.4f)
                                scaleX = p
                                scaleY = p
                                alpha = p.coerceIn(0f, 1f)
                            },
                        )
                    }
                }
            }
        }
    }
}

// endregion

// region 3 · Private by design

@Composable
internal fun PrivateScene(scene: Scene, pager: PagerState, page: Int, direction: LayoutDirection) {
    val colors = MaterialTheme.colorScheme
    val orbit = rememberOrbit(PlanBTheme.motion.enabled)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth
        Halo(
            colors.secondary,
            Modifier.matchParentSize()
                .sceneLayer(scene, pager, page, direction, 0f, 0.4f, depth = 72.dp, fromY = 0.dp, fromScale = 0.7f)
                .graphicsLayer { rotationZ = orbit.value },
        )
        Phone(Modifier.align(Alignment.Center).offset(y = -side * 0.04f).width(side * 0.4f).height(side * 0.74f).sceneLayer(scene, pager, page, direction, 0.02f, 0.36f, fromY = 40.dp))
        DataTiles(scene, Modifier.matchParentSize().sceneLayer(scene, pager, page, direction, 0.1f, 0.2f, depth = (-24).dp, fromY = 0.dp))
        Shield(
            scene,
            Modifier.align(Alignment.Center).offset(y = side * 0.14f).size(side * 0.3f)
                .sceneLayer(scene, pager, page, direction, 0.42f, 0.7f, depth = (-60).dp, fromY = 20.dp, fromScale = 0.8f),
        )
    }
}

/** Concentric rings with a dashed orbit that turns slowly (only while motion is on). */
@Composable
private fun Halo(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.drawWithCache {
            val center = Offset(size.width / 2f, size.height * 0.46f)
            val dashed = Stroke(
                1.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 9.dp.toPx())),
            )
            val thin = Stroke(1.dp.toPx())
            onDrawBehind {
                drawCircle(color.copy(alpha = 0.08f), size.width * 0.47f, center)
                drawCircle(color.copy(alpha = 0.18f), size.width * 0.47f, center, style = thin)
                drawCircle(color.copy(alpha = 0.35f), size.width * 0.38f, center, style = dashed)
                drawCircle(color.copy(alpha = 0.1f), size.width * 0.3f, center)
            }
        },
    )
}

@Composable
private fun rememberOrbit(enabled: Boolean): State<Float> {
    if (!enabled) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "orbit").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbit",
    )
}

@Composable
private fun Phone(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier,
        shape = RoundedCornerShape(26.dp),
        color = colors.surfaceContainerLowest,
        border = BorderStroke(1.5.dp, colors.outlineVariant),
        shadowElevation = 8.dp,
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(28.dp).height(5.dp).background(colors.outlineVariant, RoundedCornerShape(50)))
        }
    }
}

/**
 * Four bits of the user's data (task, note, habit, event) arriving from around the phone and
 * settling inside it: everything stays on the device.
 */
@Composable
private fun DataTiles(scene: Scene, modifier: Modifier = Modifier) {
    val tones = PlanBTheme.colors
    val accents = listOf(AccentColor.LAVENDER, AccentColor.MINT, AccentColor.PEACH, AccentColor.ROSE).map { tones.accent(it) }
    Box(
        modifier.drawWithCache {
            val tile = size.width * 0.11f
            val gap = size.width * 0.03f
            val phoneTop = size.height * 0.5f - size.height * 0.37f - size.height * 0.04f
            val gridLeft = size.width / 2f - tile - gap / 2f
            val gridTop = phoneTop + size.height * 0.1f
            val radius = CornerRadius(tile * 0.28f)
            val bar = tile * 0.12f
            onDrawBehind {
                val rtl = layoutDirection == LayoutDirection.Rtl
                for (i in 0 until 4) {
                    val col = i % 2
                    val row = i / 2
                    val targetX = gridLeft + col * (tile + gap)
                    val targetY = gridTop + row * (tile + gap)
                    // Start outside the phone, each from its own side.
                    val fromX = if ((col == 0) != rtl) -size.width * 0.32f else size.width * 0.32f
                    val fromY = if (row == 0) -size.height * 0.12f else size.height * 0.16f
                    val start = 0.1f + i * 0.08f
                    val p = scene.value.pop(start, start + 0.34f)
                    val a = scene.value.window(start, start + 0.12f)
                    if (a <= 0f) continue
                    val x = targetX + (1f - p) * fromX
                    val y = targetY + (1f - p) * fromY
                    drawRoundRect(accents[i].container, Offset(x, y), Size(tile, tile), radius, alpha = a)
                    drawRoundRect(accents[i].strong, Offset(x + tile * 0.2f, y + tile * 0.32f), Size(tile * 0.6f, bar), CornerRadius(bar / 2f), alpha = a)
                    drawRoundRect(accents[i].strong, Offset(x + tile * 0.2f, y + tile * 0.56f), Size(tile * 0.38f, bar), CornerRadius(bar / 2f), alpha = a * 0.6f)
                }
            }
        },
    )
}

/** A shield traced around a lock, then filled. */
@Composable
private fun Shield(scene: Scene, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val outline = colors.primary
    val fillFrom = colors.primary
    val fillTo = colors.inversePrimary
    Box(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val shield = Path().apply {
                moveTo(w * 0.5f, h * 0.02f)
                cubicTo(w * 0.66f, h * 0.12f, w * 0.82f, h * 0.15f, w * 0.94f, h * 0.15f)
                cubicTo(w * 0.95f, h * 0.55f, w * 0.8f, h * 0.84f, w * 0.5f, h * 0.98f)
                cubicTo(w * 0.2f, h * 0.84f, w * 0.05f, h * 0.55f, w * 0.06f, h * 0.15f)
                cubicTo(w * 0.18f, h * 0.15f, w * 0.34f, h * 0.12f, w * 0.5f, h * 0.02f)
                close()
            }
            val measure = PathMeasure().apply { setPath(shield, true) }
            val traced = Path()
            val brush = Brush.linearGradient(listOf(fillFrom, fillTo), Offset(0f, 0f), Offset(w, h))
            val stroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind {
                val trace = scene.value.window(0.44f, 0.76f)
                val fill = scene.value.window(0.66f, 0.86f)
                if (fill > 0f) drawPath(shield, brush, alpha = fill)
                if (trace > 0f && fill < 1f) {
                    traced.rewind()
                    measure.getSegment(0f, measure.length * trace, traced, true)
                    drawPath(traced, outline, style = stroke, alpha = 1f - fill)
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = colors.onPrimary,
            modifier = Modifier.fillMaxSize(0.42f).graphicsLayer {
                val p = scene.value.pop(0.74f, 0.95f)
                scaleX = p
                scaleY = p
                alpha = p.coerceIn(0f, 1f)
            },
        )
    }
}

// endregion
