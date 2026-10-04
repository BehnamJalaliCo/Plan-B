package com.behnamjalali.planb.core.designsystem.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.ColorTheme

/** Extended colors beyond Material roles. */
@Immutable
data class PlannerColors(
    val isDark: Boolean,
    val heroGradient: Brush,
    val cardBorder: Color,
    val success: Color,
    val warning: Color,
    val priorityHigh: Color,
    val priorityMedium: Color,
    val priorityLow: Color,
) {
    fun accent(accent: AccentColor): AccentTones = AccentPalette.tones(accent, isDark)
}

/** App-level experience preferences visible to all components. */
@Immutable
data class PlannerExperience(val motion: Motion, val hapticsEnabled: Boolean)

val LocalPlannerColors = staticCompositionLocalOf<PlannerColors> { error("PlanBTheme not applied") }
val LocalPlannerExperience = staticCompositionLocalOf { PlannerExperience(Motion(true), hapticsEnabled = true) }

private fun plannerColors(dark: Boolean, scheme: ColorScheme, theme: ColorTheme = ColorTheme.CLASSIC) = PlannerColors(
    isDark = dark,
    heroGradient = ThemePalettes.hero(theme, dark) ?: if (dark) {
        Brush.linearGradient(listOf(Color(0xFF2A2557), Color(0xFF1C2D45), Color(0xFF1B3A35)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFEDE7FF), Color(0xFFE3EEFA), Color(0xFFDDF4EC)))
    },
    cardBorder = scheme.outlineVariant.copy(alpha = if (dark) 0.6f else 0.9f),
    success = if (dark) Color(0xFF86D9C0) else Color(0xFF1F7A63),
    warning = if (dark) Color(0xFFFFC27A) else Color(0xFF9A5B00),
    priorityHigh = if (dark) Color(0xFFFF9E9E) else Color(0xFFC0392B),
    priorityMedium = if (dark) Color(0xFFFFC27A) else Color(0xFFB86E00),
    priorityLow = if (dark) Color(0xFF9CC4EE) else Color(0xFF3B74AE),
)

/** True when the system "Remove animations"/animator scale is zero. */
@Composable
private fun systemAnimationsDisabled(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

@Composable
fun PlanBTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    animationsEnabled: Boolean = true,
    hapticsEnabled: Boolean = true,
    colorTheme: ColorTheme = ColorTheme.CLASSIC,
    content: @Composable () -> Unit,
) {
    val scheme = remember(darkTheme, colorTheme) { ThemePalettes.scheme(colorTheme, darkTheme) }
    val colors = remember(darkTheme, colorTheme) { plannerColors(darkTheme, scheme, colorTheme) }
    val motionEnabled = animationsEnabled && !systemAnimationsDisabled()
    val experience = remember(motionEnabled, hapticsEnabled) { PlannerExperience(Motion(motionEnabled), hapticsEnabled) }
    CompositionLocalProvider(
        LocalPlannerColors provides colors,
        LocalPlannerExperience provides experience,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = PlanBTypography,
            shapes = PlanBShapes,
            content = content,
        )
    }
}

/** Convenience accessors: `PlanBTheme.colors`, `PlanBTheme.motion`. */
object PlanBTheme {
    val colors: PlannerColors
        @Composable @ReadOnlyComposable get() = LocalPlannerColors.current
    val motion: Motion
        @Composable @ReadOnlyComposable get() = LocalPlannerExperience.current.motion
    val experience: PlannerExperience
        @Composable @ReadOnlyComposable get() = LocalPlannerExperience.current
}
