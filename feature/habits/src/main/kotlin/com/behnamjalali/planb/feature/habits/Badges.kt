package com.behnamjalali.planb.feature.habits

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.theme.AccentTones
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.BadgeCategory
import com.behnamjalali.planb.core.model.BadgeDefinition
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.core.ui.PlannerLocals
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The icon of a badge's category. */
internal val BadgeCategory.icon: ImageVector
    get() = when (this) {
        BadgeCategory.STREAK -> Icons.Rounded.LocalFireDepartment
        BadgeCategory.FOCUS -> Icons.Rounded.Timer
        BadgeCategory.TASKS -> Icons.Rounded.TaskAlt
        BadgeCategory.JOURNAL -> Icons.Rounded.AutoStories
        BadgeCategory.MOOD -> Icons.Rounded.Mood
        BadgeCategory.CHALLENGE -> Icons.Rounded.EmojiEvents
    }

internal val BadgeCategory.accent: AccentColor
    get() = when (this) {
        BadgeCategory.STREAK -> AccentColor.PEACH
        BadgeCategory.FOCUS -> AccentColor.MINT
        BadgeCategory.TASKS -> AccentColor.POWDER_BLUE
        BadgeCategory.JOURNAL -> AccentColor.SAND
        BadgeCategory.MOOD -> AccentColor.ROSE
        BadgeCategory.CHALLENGE -> AccentColor.LAVENDER
    }

@Composable
internal fun badgeName(badge: BadgeDefinition): String {
    val n = badge.threshold
    val text = PlannerLocals.numbers.format(n)
    return pluralStringResource(
        when (badge.category) {
            BadgeCategory.STREAK -> R.plurals.badge_name_streak
            BadgeCategory.FOCUS -> R.plurals.badge_name_focus
            BadgeCategory.TASKS -> R.plurals.badge_name_tasks
            BadgeCategory.JOURNAL -> R.plurals.badge_name_journal
            BadgeCategory.MOOD -> R.plurals.badge_name_mood
            BadgeCategory.CHALLENGE -> R.plurals.badge_name_challenge
        },
        n,
        text,
    )
}

@Composable
internal fun badgeDescription(badge: BadgeDefinition): String {
    val n = badge.threshold
    val text = PlannerLocals.numbers.format(n)
    return pluralStringResource(
        when (badge.category) {
            BadgeCategory.STREAK -> R.plurals.badge_desc_streak
            BadgeCategory.FOCUS -> R.plurals.badge_desc_focus
            BadgeCategory.TASKS -> R.plurals.badge_desc_tasks
            BadgeCategory.JOURNAL -> R.plurals.badge_desc_journal
            BadgeCategory.MOOD -> R.plurals.badge_desc_mood
            BadgeCategory.CHALLENGE -> R.plurals.badge_desc_challenge
        },
        n,
        text,
    )
}

/** A badge's medal: colored when earned, a quiet outline with a lock otherwise. */
@Composable
internal fun BadgeMedal(badge: BadgeDefinition, earned: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val tones: AccentTones = PlanBTheme.colors.accent(badge.category.accent)
    val scheme = MaterialTheme.colorScheme
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = if (earned) tones.container else scheme.surfaceContainerHigh,
            contentColor = if (earned) tones.strong else scheme.outline,
            modifier = Modifier.size(size),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(badge.category.icon, contentDescription = null, modifier = Modifier.size(size * 0.5f))
            }
        }
        if (!earned) {
            Surface(shape = CircleShape, color = scheme.surface, modifier = Modifier.align(Alignment.BottomEnd).size(size * 0.36f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = scheme.outline, modifier = Modifier.size(size * 0.22f))
                }
            }
        }
    }
}

/**
 * The badge unlock moment (Plan-B Pro #29): a dialog over any screen for each badge earned in
 * the last days, once. The medal pops in with a burst of confetti; with reduced motion (the app
 * setting or the system's "remove animations") it simply appears. Only for Pro users.
 */
@Composable
fun BadgeCelebrationHost(onOpenBadges: () -> Unit, viewModel: BadgeCelebrationViewModel = hiltViewModel()) {
    if (!LocalProAccess.current.isPro) return
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val award = pending.firstOrNull() ?: return
    PlannerDialog(
        title = stringResource(R.string.badge_unlocked),
        onDismiss = { viewModel.dismiss(award) },
        confirmLabel = stringResource(R.string.badge_unlocked_ok),
        onConfirm = { viewModel.dismiss(award) },
        dismissLabel = "",
        content = {
            Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.CenterHorizontally) {
                CelebratingMedal(award.badge, key = award.id)
                Text(badgeName(award.badge), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(
                    badgeDescription(award.badge),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.badge_earned_on, PlannerLocals.formatter.mediumDate(award.earnedAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate())),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    viewModel.dismiss(award)
                    onOpenBadges()
                }) { Text(stringResource(R.string.badge_unlocked_all)) }
            }
        },
    )
}

@Composable
private fun CelebratingMedal(badge: BadgeDefinition, key: Long) {
    val motion = PlanBTheme.motion.enabled
    val scale = remember(key) { Animatable(if (motion) 0.3f else 1f) }
    val burst = remember(key) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(key, motion) {
        if (!motion) return@LaunchedEffect
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(key, motion) {
        if (!motion) return@LaunchedEffect
        burst.animateTo(1f, tween(durationMillis = 1_400, easing = LinearEasing))
    }
    val tones = PlanBTheme.colors.accent(badge.category.accent)
    val colors = listOf(tones.strong, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, PlanBTheme.colors.warning, PlanBTheme.colors.success)
    val pieces = remember(key) { confetti(key) }
    Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
        if (motion && burst.value < 1f) {
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val t = burst.value
                val center = Offset(size.width / 2, size.height / 2)
                pieces.forEachIndexed { i, p ->
                    val distance = p.speed * t * size.minDimension * 0.55f
                    val fall = t * t * size.height * 0.25f
                    val position = center + Offset(cos(p.angle) * distance, sin(p.angle) * distance + fall)
                    drawCircle(colors[i % colors.size].copy(alpha = (1f - t).coerceIn(0f, 1f)), radius = p.radius * density, center = position)
                }
            }
        }
        BadgeMedal(badge, earned = true, size = 88.dp, modifier = Modifier.scale(scale.value))
    }
}

private class Piece(val angle: Float, val speed: Float, val radius: Float)

private fun confetti(seed: Long): List<Piece> {
    val random = Random(seed)
    return List(28) { Piece(angle = random.nextFloat() * 2 * Math.PI.toFloat(), speed = 0.5f + random.nextFloat() * 0.5f, radius = 2f + random.nextFloat() * 3f) }
}
