package com.behnamjalali.planb.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Raw palette. Screens use [ColorScheme] roles or [PlannerColors], never these directly. */
internal object Palette {
    // Warm cream neutrals (light)
    val Cream50 = Color(0xFFFFFDF9)
    val Cream100 = Color(0xFFFBF7F2)
    val Cream200 = Color(0xFFF5EFE8)
    val Cream300 = Color(0xFFEFE8E0)
    val Cream400 = Color(0xFFE7DFD6)
    val Ink900 = Color(0xFF1F1B26)
    val Ink700 = Color(0xFF4A4455)
    val Ink500 = Color(0xFF6A6476)
    val Ink300 = Color(0xFFCDC6D3)
    val Ink200 = Color(0xFFE4DEE8)

    // Deep navy/charcoal neutrals (dark)
    val Night950 = Color(0xFF0F1118)
    val Night900 = Color(0xFF14161F)
    val Night850 = Color(0xFF191C27)
    val Night800 = Color(0xFF1E2230)
    val Night750 = Color(0xFF242939)
    val Night700 = Color(0xFF2B3143)
    val Night600 = Color(0xFF3A4157)
    val Mist100 = Color(0xFFECE9F3)
    val Mist300 = Color(0xFFB4AFC2)
    val Mist500 = Color(0xFF8A859A)

    // Lavender (primary)
    val Lavender700 = Color(0xFF5B4BC4)
    val Lavender600 = Color(0xFF6B5BD2)
    val Lavender200 = Color(0xFFC9C0FF)
    val Lavender100 = Color(0xFFEAE6FD)
    val Lavender900 = Color(0xFF241B5E)
    val LavenderDarkContainer = Color(0xFF3A3178)

    // Mint (secondary)
    val Mint700 = Color(0xFF23705F)
    val Mint200 = Color(0xFF9EE0CC)
    val Mint100 = Color(0xFFD7F3EA)
    val Mint900 = Color(0xFF0B3A2F)
    val MintDarkContainer = Color(0xFF1C4A40)

    // Peach (tertiary)
    val Peach700 = Color(0xFFA9512C)
    val Peach200 = Color(0xFFFFBE9F)
    val Peach100 = Color(0xFFFFE6D8)
    val Peach900 = Color(0xFF451A06)
    val PeachDarkContainer = Color(0xFF5E3019)

    val Error600 = Color(0xFFB3261E)
    val Error100 = Color(0xFFFADCD9)
    val Error200 = Color(0xFFFFB4AB)
    val ErrorDarkContainer = Color(0xFF7A1F1A)
}

internal val LightColors: ColorScheme = lightColorScheme(
    primary = Palette.Lavender600,
    onPrimary = Color.White,
    primaryContainer = Palette.Lavender100,
    onPrimaryContainer = Palette.Lavender900,
    inversePrimary = Palette.Lavender200,
    secondary = Palette.Mint700,
    onSecondary = Color.White,
    secondaryContainer = Palette.Mint100,
    onSecondaryContainer = Palette.Mint900,
    tertiary = Palette.Peach700,
    onTertiary = Color.White,
    tertiaryContainer = Palette.Peach100,
    onTertiaryContainer = Palette.Peach900,
    background = Palette.Cream100,
    onBackground = Palette.Ink900,
    surface = Palette.Cream100,
    onSurface = Palette.Ink900,
    surfaceVariant = Palette.Cream300,
    onSurfaceVariant = Palette.Ink700,
    surfaceTint = Palette.Lavender600,
    inverseSurface = Palette.Night800,
    inverseOnSurface = Palette.Mist100,
    error = Palette.Error600,
    onError = Color.White,
    errorContainer = Palette.Error100,
    onErrorContainer = Color(0xFF410E0B),
    outline = Palette.Ink500,
    outlineVariant = Palette.Ink200,
    scrim = Color.Black,
    surfaceBright = Palette.Cream50,
    surfaceDim = Palette.Cream300,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Palette.Cream50,
    surfaceContainer = Palette.Cream200,
    surfaceContainerHigh = Palette.Cream300,
    surfaceContainerHighest = Palette.Cream400,
)

internal val DarkColors: ColorScheme = darkColorScheme(
    primary = Palette.Lavender200,
    onPrimary = Palette.Lavender900,
    primaryContainer = Palette.LavenderDarkContainer,
    onPrimaryContainer = Palette.Lavender100,
    inversePrimary = Palette.Lavender600,
    secondary = Palette.Mint200,
    onSecondary = Palette.Mint900,
    secondaryContainer = Palette.MintDarkContainer,
    onSecondaryContainer = Palette.Mint100,
    tertiary = Palette.Peach200,
    onTertiary = Palette.Peach900,
    tertiaryContainer = Palette.PeachDarkContainer,
    onTertiaryContainer = Palette.Peach100,
    background = Palette.Night900,
    onBackground = Palette.Mist100,
    surface = Palette.Night900,
    onSurface = Palette.Mist100,
    surfaceVariant = Palette.Night750,
    onSurfaceVariant = Palette.Mist300,
    surfaceTint = Palette.Lavender200,
    inverseSurface = Palette.Mist100,
    inverseOnSurface = Palette.Night900,
    error = Palette.Error200,
    onError = Color(0xFF690005),
    errorContainer = Palette.ErrorDarkContainer,
    onErrorContainer = Palette.Error100,
    outline = Palette.Mist500,
    outlineVariant = Palette.Night600,
    scrim = Color.Black,
    surfaceBright = Palette.Night700,
    surfaceDim = Palette.Night950,
    surfaceContainerLowest = Palette.Night950,
    surfaceContainerLow = Palette.Night850,
    surfaceContainer = Palette.Night800,
    surfaceContainerHigh = Palette.Night750,
    surfaceContainerHighest = Palette.Night700,
)

/**
 * A pastel accent expressed as three roles that always meet contrast:
 * [container] background, [onContainer] text/icons on it, and [strong] for
 * small marks (dots, rings, bars) on regular surfaces.
 */
@Immutable
data class AccentTones(val container: Color, val onContainer: Color, val strong: Color)
