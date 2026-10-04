package com.behnamjalali.planb.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerPill
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.Spacing

/**
 * Whether the user has Plan-B Pro, and how to show the Pro screen. The app provides it from
 * the billing entitlement; feature modules only read it, so they never depend on billing.
 * [openPaywall] opens the Pro screen focused on a feature (or the general one for null).
 */
@Immutable
data class ProAccess(
    val isPro: Boolean,
    val openPaywall: (ProFeature?) -> Unit,
)

/** Defaults to "not Pro" with a no-op paywall (previews and isolated screen tests). */
val LocalProAccess = staticCompositionLocalOf { ProAccess(isPro = false, openPaywall = {}) }

/**
 * Shows [content] to Pro users and [teaser] to everyone else. Free features never go through
 * a gate. The teaser explains the feature and only opens the Pro screen when tapped.
 */
@Composable
fun ProGate(
    feature: ProFeature,
    modifier: Modifier = Modifier,
    teaser: @Composable () -> Unit = { ProTeaser(feature, modifier) },
    content: @Composable () -> Unit,
) {
    if (LocalProAccess.current.isPro) content() else teaser()
}

/** Runs Pro actions, or opens the Pro screen for that feature when the user is not Pro. */
@Stable
class ProGuard internal constructor(private val access: () -> ProAccess) {
    val isPro: Boolean get() = access().isPro

    /**
     * Runs [action] for Pro users. Otherwise opens the Pro screen focused on [feature]. This
     * only ever happens in reply to the user's own tap, never on its own.
     */
    fun run(feature: ProFeature, action: () -> Unit) {
        val current = access()
        if (current.isPro) action() else current.openPaywall(feature)
    }
}

@Composable
fun rememberProGuard(): ProGuard {
    val access = rememberUpdatedState(LocalProAccess.current)
    return remember { ProGuard { access.value } }
}

/** A small "Pro" label next to Pro actions. */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    PlannerPill(
        text = stringResource(R.string.ui_pro_badge),
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier,
    )
}

/** Explains a Pro feature in place of its content; tapping the button opens the Pro screen. */
@Composable
fun ProTeaser(feature: ProFeature, modifier: Modifier = Modifier) {
    val access = LocalProAccess.current
    val title = stringResource(feature.title)
    val label = stringResource(R.string.ui_pro_feature_cd, title)
    PlannerCard(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.semantics(mergeDescendants = true) { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(IconSize.xl), contentAlignment = Alignment.Center) {
                    Icon(feature.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            ProBadge()
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(stringResource(feature.description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.ui_pro_teaser), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.md))
        PlannerButton(
            text = stringResource(R.string.ui_pro_unlock),
            onClick = { access.openPaywall(feature) },
            style = PlannerButtonStyle.Tonal,
            icon = Icons.Rounded.WorkspacePremium,
        )
    }
}
