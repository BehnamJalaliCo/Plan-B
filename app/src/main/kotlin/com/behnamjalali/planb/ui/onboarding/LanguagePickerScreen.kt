package com.behnamjalali.planb.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.R
import com.behnamjalali.planb.core.designsystem.component.pressScale
import com.behnamjalali.planb.core.designsystem.component.rememberPlannerHaptics
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.AppLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The very first screen: Persian or English. It is bilingual by design (every text appears
 * in both languages, each card in its own direction), so it reads the same whatever language
 * the app starts in. Tapping a card chooses at once: the screen fades to the bare background
 * and only then reports the choice, so the language switch (which may recreate the activity)
 * happens behind an empty frame.
 */
@Composable
internal fun LanguagePickerScreen(suggested: AppLanguage, onChosen: (AppLanguage) -> Unit, modifier: Modifier = Modifier) {
    val motion = PlanBTheme.motion
    val haptics = rememberPlannerHaptics()
    val currentOnChosen by rememberUpdatedState(onChosen)
    var chosen by rememberSaveable { mutableStateOf<AppLanguage?>(null) }
    val entrances = remember { List(ENTRANCE_ITEMS) { Animatable(0f) } }
    val content = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        entrances.forEachIndexed { i, item ->
            launch {
                if (motion.enabled) {
                    delay(STAGGER_MS * i)
                    item.animateTo(1f, EntranceSpring)
                } else {
                    item.animateTo(1f, tween(FADE_MS))
                }
            }
        }
    }
    LaunchedEffect(chosen) {
        val language = chosen ?: return@LaunchedEffect
        if (motion.enabled) delay(SELECTION_FEEDBACK_MS)
        content.animateTo(0f, tween(if (motion.enabled) EXIT_MS else FADE_MS))
        currentOnChosen(language)
    }

    val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    Box(
        modifier
            .fillMaxSize()
            .testTag("onboarding_language")
            .graphicsLayer { alpha = content.value }
            .drawWithCache {
                val center = Offset(size.width / 2f, size.height * 0.18f)
                val radius = size.maxDimension * 0.55f
                val brush = Brush.radialGradient(listOf(glow, glow.copy(alpha = 0f)), center = center, radius = radius)
                onDrawBehind { drawCircle(brush, radius = radius, center = center) }
            }
            .systemBarsPadding(),
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .widthIn(max = 480.dp)
                .padding(horizontal = Spacing.screen, vertical = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StaticBrandMark(Modifier.size(96.dp).entrance(entrances[0], 16.dp, scaleFrom = 0.9f))
            Spacer(Modifier.height(Spacing.lg))
            Column(Modifier.entrance(entrances[1], 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.onboarding_language_title_fa),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.onboarding_language_title_en),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(Spacing.huge))
            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                for ((index, language) in AppLanguage.entries.withIndex()) {
                    LanguageCard(
                        language = language,
                        selected = (chosen ?: suggested) == language,
                        enabled = chosen == null,
                        onClick = {
                            haptics.tick()
                            chosen = language
                        },
                        modifier = Modifier.entrance(entrances[2 + index], 28.dp),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xxl))
            Column(Modifier.entrance(entrances[4], 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Hint(stringResource(R.string.onboarding_language_hint_fa))
                Hint(stringResource(R.string.onboarding_language_hint_en))
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

/** One language, laid out in that language's own direction. */
@Composable
private fun LanguageCard(language: AppLanguage, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val persian = language == AppLanguage.PERSIAN
    val colors = MaterialTheme.colorScheme
    val motion = PlanBTheme.motion
    val border by animateColorAsState(if (selected) colors.primary else colors.outlineVariant, motion.standard(), label = "border")
    val container by animateColorAsState(
        if (selected) colors.primaryContainer.copy(alpha = 0.55f) else colors.surfaceContainerLowest,
        motion.standard(),
        label = "container",
    )
    val shape = RoundedCornerShape(Radius.xl)
    val interaction = remember { MutableInteractionSource() }
    CompositionLocalProvider(LocalLayoutDirection provides if (persian) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Surface(
            shape = shape,
            color = container,
            border = BorderStroke(if (selected) 2.dp else 1.dp, border),
            modifier = modifier
                .fillMaxWidth()
                .pressScale(interaction)
                .clip(shape)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick)
                .testTag("onboarding_language_${language.tag}"),
        ) {
            Row(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).background(
                        Brush.linearGradient(if (persian) listOf(colors.tertiary, colors.primary) else listOf(colors.primary, colors.secondary)),
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(if (persian) R.string.onboarding_language_glyph_fa else R.string.onboarding_language_glyph_en),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onPrimary,
                    )
                }
                Spacer(Modifier.width(Spacing.lg))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (persian) R.string.onboarding_language_name_fa else R.string.onboarding_language_name_en),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(if (persian) R.string.onboarding_language_detail_fa else R.string.onboarding_language_detail_en),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                SelectionMark(selected)
            }
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    val fill by animateFloatAsState(if (selected) 1f else 0f, PlanBTheme.motion.press(), label = "mark")
    Box(
        Modifier
            .size(26.dp)
            .drawBehind {
                val radius = size.minDimension / 2f
                drawCircle(colors.outline, radius = radius - 1.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()), alpha = 1f - fill)
                drawCircle(colors.primary, radius = radius * fill.coerceIn(0f, 1.1f))
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = null,
            tint = colors.onPrimary,
            modifier = Modifier.size(18.dp).graphicsLayer {
                alpha = fill.coerceIn(0f, 1f)
                scaleX = fill
                scaleY = fill
            },
        )
    }
}

/** The mark at rest (stacked layers), for screens that only show it. */
@Composable
internal fun StaticBrandMark(modifier: Modifier = Modifier) {
    val layers = rememberBrandLayers()
    Box(modifier) {
        val fill = Modifier.matchParentSize()
        BrandLayer(layers.shadow, fill)
        BrandLayer(layers.stem, fill)
        BrandLayer(layers.upperLobe, fill)
        BrandLayer(layers.lowerLobe, fill)
    }
}

private const val ENTRANCE_ITEMS = 5
private const val STAGGER_MS = 90L
private const val FADE_MS = 220
private const val SELECTION_FEEDBACK_MS = 260L
private const val EXIT_MS = 340
