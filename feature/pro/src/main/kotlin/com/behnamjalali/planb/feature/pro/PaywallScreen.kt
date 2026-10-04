package com.behnamjalali.planb.feature.pro

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.billing.BillingAvailability
import com.behnamjalali.planb.core.billing.ProProduct
import com.behnamjalali.planb.core.common.Digits
import com.behnamjalali.planb.core.common.NumberFormatter
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerHeroSurface
import com.behnamjalali.planb.core.designsystem.component.PlannerPill
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.MinTouchTarget
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProFeatureGroup

/** Prices shown when Cafe Bazaar gives none (the panel is the source of truth), in toman. */
internal object FallbackPrices {
    const val MONTHLY_TOMAN = 399_000L
    const val LIFETIME_TOMAN = 1_999_000L

    fun of(product: ProProduct) = when (product) {
        ProProduct.MONTHLY -> MONTHLY_TOMAN
        ProProduct.LIFETIME -> LIFETIME_TOMAN
    }
}

/** Groups thousands with the Persian (٬) or Latin (,) separator and the user's digits. */
internal fun groupedAmount(value: Long, numbers: NumberFormatter): String {
    val grouped = value.toString().reversed().chunked(3).joinToString(if (numbers.persianDigits) "٬" else ",").reversed()
    return numbers.localize(grouped)
}

/** The store's price text with the user's digits, or the fallback amount. */
@Composable
internal fun priceText(product: ProProduct, storePrices: Map<ProProduct, String>): String {
    val numbers = PlannerLocals.numbers
    val store = storePrices[product]
    return if (store != null) {
        if (numbers.persianDigits) Digits.toPersian(store) else Digits.toLatin(store)
    } else {
        stringResource(R.string.pro_price_toman, groupedAmount(FallbackPrices.of(product), numbers))
    }
}

@Composable
fun PaywallDestination(onBack: () -> Unit, viewModel: PaywallViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? ComponentActivity
    PaywallScreen(
        state = state,
        onBack = onBack,
        onSelect = viewModel::select,
        onBuy = { activity?.let(viewModel::purchase) },
        onRestore = viewModel::restore,
        onRetry = viewModel::connect,
        onMessageShown = viewModel::messageShown,
    )
}

/** Stateless Pro screen (paywall for free users, "Your Pro" for Pro users). */
@Composable
fun PaywallScreen(
    state: PaywallUiState,
    onBack: () -> Unit,
    onSelect: (ProProduct) -> Unit,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val numbers = PlannerLocals.numbers
    val pro = state.entitlement.isPro
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(stringResource(R.string.pro_title), onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "hero") { Hero(pro, numbers.format(ProFeature.entries.size)) }
            state.justPurchased?.let { item(key = "success") { SuccessCard() } }
            if (pro) {
                item(key = "active") { ActiveCard(state.entitlement.product) }
            } else {
                state.focus?.let { feature -> item(key = "focus") { FocusCard(feature) } }
                item(key = "status") { StoreStatus(state.availability, onRetry) }
                items(listOf(ProProduct.LIFETIME, ProProduct.MONTHLY), key = { it.name }) { product ->
                    PlanCard(product, priceText(product, state.storePrices), state.selected == product) { onSelect(product) }
                }
                item(key = "buy") {
                    PlannerButton(
                        text = stringResource(
                            when {
                                state.busy -> R.string.pro_purchasing
                                state.selected == ProProduct.LIFETIME -> R.string.pro_buy_lifetime
                                else -> R.string.pro_buy_monthly
                            },
                        ),
                        onClick = onBuy,
                        enabled = state.canBuy,
                        icon = Icons.Rounded.WorkspacePremium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            state.message?.let { message -> item(key = "message") { MessageCard(message, onMessageShown) } }
            item(key = "restore") {
                PlannerButton(
                    text = stringResource(R.string.pro_restore),
                    onClick = onRestore,
                    style = PlannerButtonStyle.Text,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!pro) {
                item(key = "legal") {
                    Text(
                        stringResource(R.string.pro_legal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "features") { PlannerSectionHeader(stringResource(R.string.pro_features_title)) }
            ProFeatureGroup.entries.forEach { group ->
                item(key = "g_${group.name}") {
                    Text(
                        stringResource(group.title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
                    )
                }
                items(ProFeature.entries.filter { it.group == group }, key = { it.id }) { feature -> FeatureRow(feature, pro) }
            }
        }
    }
}

@Composable
private fun Hero(pro: Boolean, featureCount: String) {
    PlannerHeroSurface(Modifier.fillMaxWidth()) {
        IconBadge(Icons.Rounded.WorkspacePremium, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
        Spacer(Modifier.size(Spacing.md))
        Text(
            stringResource(if (pro) R.string.pro_active_title else R.string.pro_hero_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.size(Spacing.xs))
        Text(stringResource(R.string.pro_hero_body, featureCount), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun IconBadge(icon: ImageVector, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Box(Modifier.size(IconSize.xl + Spacing.sm), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.md))
        }
    }
}

@Composable
private fun FocusCard(feature: ProFeature) {
    PlannerCard(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Text(stringResource(R.string.pro_focus_label), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.size(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(feature.icon, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(feature.title), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.size(Spacing.xs))
        Text(stringResource(feature.description), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StoreStatus(availability: BillingAvailability?, onRetry: () -> Unit) {
    when (availability) {
        null -> Row(
            Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(Modifier.size(IconSize.md), strokeWidth = 2.dp)
            Spacer(Modifier.width(Spacing.md))
            Text(stringResource(R.string.pro_state_loading), style = MaterialTheme.typography.bodyMedium)
        }
        BillingAvailability.READY -> Unit
        BillingAvailability.STORE_NOT_INSTALLED -> NoticeCard(Icons.Rounded.Storefront, R.string.pro_state_no_bazaar_title, R.string.pro_state_no_bazaar_body)
        BillingAvailability.NOT_CONFIGURED -> NoticeCard(Icons.Rounded.Info, R.string.pro_state_not_configured_title, R.string.pro_state_not_configured_body)
        BillingAvailability.UNAVAILABLE -> NoticeCard(Icons.Rounded.CloudOff, R.string.pro_state_unavailable_title, R.string.pro_state_unavailable_body) {
            PlannerButton(stringResource(R.string.pro_retry), onRetry, style = PlannerButtonStyle.Tonal)
        }
    }
}

@Composable
private fun NoticeCard(icon: ImageVector, title: Int, body: Int, action: (@Composable () -> Unit)? = null) {
    PlannerCard(containerColor = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 0.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                action?.invoke()
            }
        }
    }
}

@Composable
private fun PlanCard(product: ProProduct, price: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val recommended = product == ProProduct.LIFETIME
    PlannerCard(
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
        onClick = onClick,
        // Opaque, so the card's shadow never shows through the tint.
        containerColor = if (selected) scheme.primaryContainer.copy(alpha = 0.55f).compositeOver(scheme.surfaceContainerLowest) else scheme.surfaceContainerLowest,
        border = if (selected) BorderStroke(2.dp, scheme.primary) else BorderStroke(1.dp, PlanBTheme.colors.cardBorder),
        shape = RoundedCornerShape(Radius.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                if (recommended) {
                    PlannerPill(
                        stringResource(R.string.pro_recommended),
                        container = scheme.tertiaryContainer,
                        content = scheme.onTertiaryContainer,
                    )
                    Spacer(Modifier.size(Spacing.xs))
                }
                Text(
                    stringResource(if (recommended) R.string.pro_plan_lifetime else R.string.pro_plan_monthly),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(if (recommended) R.string.pro_plan_lifetime_sub else R.string.pro_plan_monthly_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Text(price, style = MaterialTheme.typography.titleMedium, color = scheme.primary)
        }
    }
}

@Composable
private fun SuccessCard() {
    // A short, gentle entrance (none when motion is reduced).
    val motion = PlanBTheme.motion
    val visible = remember { MutableTransitionState(!motion.enabled) }.apply { targetState = true }
    AnimatedVisibility(
        visibleState = visible,
        enter = scaleIn(spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow), initialScale = 0.92f) + fadeIn(),
    ) {
        PlannerCard(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Celebration, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pro_success_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.pro_success_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ActiveCard(product: ProProduct?) {
    PlannerCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = PlanBTheme.colors.success)
            Spacer(Modifier.width(Spacing.md))
            Text(
                stringResource(if (product == ProProduct.LIFETIME) R.string.pro_active_lifetime else R.string.pro_active_monthly),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (product == ProProduct.MONTHLY) {
            Spacer(Modifier.size(Spacing.sm))
            Text(stringResource(R.string.pro_manage_monthly), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MessageCard(message: PaywallMessage, onDismiss: () -> Unit) {
    val text = when (message) {
        PaywallMessage.RESTORED -> R.string.pro_msg_restored
        PaywallMessage.NOTHING_TO_RESTORE -> R.string.pro_msg_nothing
        PaywallMessage.RESTORE_FAILED -> R.string.pro_msg_restore_failed
        PaywallMessage.CANCELLED -> R.string.pro_msg_cancelled
        PaywallMessage.FAILED -> R.string.pro_msg_error
        PaywallMessage.VERIFICATION_FAILED -> R.string.pro_msg_verification
    }
    PlannerCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 0.dp) {
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
        PlannerButton(stringResource(R.string.pro_dismiss), onDismiss, style = PlannerButtonStyle.Text)
    }
}

@Composable
private fun FeatureRow(feature: ProFeature, unlocked: Boolean) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(feature.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.md))
        Spacer(Modifier.width(Spacing.md))
        Text(stringResource(feature.title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (unlocked) {
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.pro_feature_unlocked), style = MaterialTheme.typography.labelMedium, color = PlanBTheme.colors.success)
        }
    }
}
