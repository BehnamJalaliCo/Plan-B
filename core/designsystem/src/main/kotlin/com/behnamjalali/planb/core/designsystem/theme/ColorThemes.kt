package com.behnamjalali.planb.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.behnamjalali.planb.core.model.ColorTheme

/**
 * Plan-B Pro color themes (#33). Each theme replaces the three accent families (primary,
 * secondary, tertiary) and tints the neutrals, on top of the brand [LightColors]/[DarkColors],
 * so every role keeps the contrast rules of the classic palette. Midnight's dark variant is
 * true black for OLED screens.
 */
internal object ThemePalettes {
    /** One accent family for one mode: main color, its text, container and text on container. */
    @Immutable
    data class Family(val main: Color, val onMain: Color, val container: Color, val onContainer: Color)

    @Immutable
    data class Neutrals(
        val background: Color,
        val lowest: Color,
        val low: Color,
        val container: Color,
        val high: Color,
        val highest: Color,
        val outlineVariant: Color,
    )

    @Immutable
    data class Mode(
        val primary: Family,
        val secondary: Family,
        val tertiary: Family,
        val neutrals: Neutrals?,
        val hero: List<Color>,
    )

    @Immutable
    data class Spec(val light: Mode, val dark: Mode, val swatch: List<Color>)

    private val White = Color.White

    val Ocean = Spec(
        light = Mode(
            primary = Family(Color(0xFF0B6A8F), White, Color(0xFFCDEAF7), Color(0xFF002433)),
            secondary = Family(Color(0xFF2B6C70), White, Color(0xFFCFEDEF), Color(0xFF07302F)),
            tertiary = Family(Color(0xFF9A4D3B), White, Color(0xFFFFDBD1), Color(0xFF3B0A02)),
            neutrals = Neutrals(
                background = Color(0xFFF3F7FA), lowest = White, low = Color(0xFFF8FBFD), container = Color(0xFFEAF0F4),
                high = Color(0xFFE2E9EE), highest = Color(0xFFD9E2E8), outlineVariant = Color(0xFFD6DFE5),
            ),
            hero = listOf(Color(0xFFD8EEF9), Color(0xFFDCEFF1), Color(0xFFE6F1FA)),
        ),
        dark = Mode(
            primary = Family(Color(0xFF8BCFEF), Color(0xFF00344A), Color(0xFF0D4A66), Color(0xFFCDEAF7)),
            secondary = Family(Color(0xFF9AD5D9), Color(0xFF003739), Color(0xFF1C4D50), Color(0xFFCFEDEF)),
            tertiary = Family(Color(0xFFFFB4A0), Color(0xFF5C1A0C), Color(0xFF6B2F22), Color(0xFFFFDBD1)),
            neutrals = null,
            hero = listOf(Color(0xFF123247), Color(0xFF15343C), Color(0xFF1A2B44)),
        ),
        swatch = listOf(Color(0xFF0B6A8F), Color(0xFF8BCFEF), Color(0xFF2B6C70)),
    )

    val Forest = Spec(
        light = Mode(
            primary = Family(Color(0xFF2E6B3F), White, Color(0xFFCFEBD3), Color(0xFF07290F)),
            secondary = Family(Color(0xFF5B6531), White, Color(0xFFE2E9BE), Color(0xFF1C2100)),
            tertiary = Family(Color(0xFF8A5A1E), White, Color(0xFFFDE0BE), Color(0xFF2D1600)),
            neutrals = Neutrals(
                background = Color(0xFFF5F7F1), lowest = White, low = Color(0xFFFAFBF7), container = Color(0xFFECF0E6),
                high = Color(0xFFE4E9DD), highest = Color(0xFFDBE1D3), outlineVariant = Color(0xFFD9DFD1),
            ),
            hero = listOf(Color(0xFFDDF0DC), Color(0xFFE9EFD3), Color(0xFFF3ECD9)),
        ),
        dark = Mode(
            primary = Family(Color(0xFF9CD5A6), Color(0xFF00391A), Color(0xFF16502A), Color(0xFFCFEBD3)),
            secondary = Family(Color(0xFFC4CD95), Color(0xFF2D3407), Color(0xFF434B1C), Color(0xFFE2E9BE)),
            tertiary = Family(Color(0xFFF5BD7B), Color(0xFF482900), Color(0xFF673D07), Color(0xFFFDE0BE)),
            neutrals = null,
            hero = listOf(Color(0xFF173A24), Color(0xFF243520), Color(0xFF33301C)),
        ),
        swatch = listOf(Color(0xFF2E6B3F), Color(0xFF9CD5A6), Color(0xFF8A5A1E)),
    )

    val Sunset = Spec(
        light = Mode(
            primary = Family(Color(0xFFB0472A), White, Color(0xFFFFDBCF), Color(0xFF3A0B00)),
            secondary = Family(Color(0xFF8E4A62), White, Color(0xFFFFD9E3), Color(0xFF3A0720)),
            tertiary = Family(Color(0xFF7A5B00), White, Color(0xFFFFE08A), Color(0xFF261A00)),
            neutrals = null,
            hero = listOf(Color(0xFFFFE1D3), Color(0xFFFFDDE5), Color(0xFFFFEBC7)),
        ),
        dark = Mode(
            primary = Family(Color(0xFFFFB59D), Color(0xFF5E1700), Color(0xFF7D2D14), Color(0xFFFFDBCF)),
            secondary = Family(Color(0xFFFFB0C8), Color(0xFF561D33), Color(0xFF71334A), Color(0xFFFFD9E3)),
            tertiary = Family(Color(0xFFEFC33E), Color(0xFF3F2E00), Color(0xFF5B4300), Color(0xFFFFE08A)),
            neutrals = null,
            hero = listOf(Color(0xFF4A2318), Color(0xFF45202F), Color(0xFF3F3214)),
        ),
        swatch = listOf(Color(0xFFB0472A), Color(0xFFFFB59D), Color(0xFF8E4A62)),
    )

    val Blossom = Spec(
        light = Mode(
            primary = Family(Color(0xFF9A3F6E), White, Color(0xFFFFD8E8), Color(0xFF3D0024)),
            secondary = Family(Color(0xFF725574), White, Color(0xFFFBD7FB), Color(0xFF2A132D)),
            tertiary = Family(Color(0xFF3F6A8A), White, Color(0xFFD2E6F8), Color(0xFF001E31)),
            neutrals = Neutrals(
                background = Color(0xFFFBF5F7), lowest = White, low = Color(0xFFFEFAFB), container = Color(0xFFF4EBEF),
                high = Color(0xFFEEE3E8), highest = Color(0xFFE7DBE1), outlineVariant = Color(0xFFE6DAE0),
            ),
            hero = listOf(Color(0xFFFFE0EC), Color(0xFFF6E0F7), Color(0xFFE2ECF8)),
        ),
        dark = Mode(
            primary = Family(Color(0xFFFFAFD2), Color(0xFF5E113D), Color(0xFF7B2955), Color(0xFFFFD8E8)),
            secondary = Family(Color(0xFFDFBBDF), Color(0xFF412743), Color(0xFF593D5B), Color(0xFFFBD7FB)),
            tertiary = Family(Color(0xFFA7CAEA), Color(0xFF0B344F), Color(0xFF264B67), Color(0xFFD2E6F8)),
            neutrals = null,
            hero = listOf(Color(0xFF4A1E36), Color(0xFF3A2440), Color(0xFF1F3048)),
        ),
        swatch = listOf(Color(0xFF9A3F6E), Color(0xFFFFAFD2), Color(0xFF3F6A8A)),
    )

    val Midnight = Spec(
        light = Mode(
            primary = Family(Color(0xFF3D5A98), White, Color(0xFFD9E2FF), Color(0xFF001944)),
            secondary = Family(Color(0xFF4F5B73), White, Color(0xFFD7E2FB), Color(0xFF0B1A2E)),
            tertiary = Family(Color(0xFF6A4F8C), White, Color(0xFFEBDCFF), Color(0xFF250A45)),
            neutrals = Neutrals(
                background = Color(0xFFF4F5F9), lowest = White, low = Color(0xFFF9FAFC), container = Color(0xFFEBEDF3),
                high = Color(0xFFE3E6EE), highest = Color(0xFFDADEE8), outlineVariant = Color(0xFFD8DCE6),
            ),
            hero = listOf(Color(0xFFDDE3F6), Color(0xFFE3E2F6), Color(0xFFE6ECF7)),
        ),
        dark = Mode(
            primary = Family(Color(0xFFB0C6FF), Color(0xFF0B2D63), Color(0xFF243F78), Color(0xFFD9E2FF)),
            secondary = Family(Color(0xFFBBC6DE), Color(0xFF253144), Color(0xFF3B475C), Color(0xFFD7E2FB)),
            tertiary = Family(Color(0xFFD4BBFF), Color(0xFF3A1D5A), Color(0xFF523872), Color(0xFFEBDCFF)),
            // True black backgrounds for OLED screens; raised surfaces stay just above black.
            neutrals = Neutrals(
                background = Color.Black, lowest = Color.Black, low = Color(0xFF0B0C10), container = Color(0xFF121318),
                high = Color(0xFF1A1C22), highest = Color(0xFF22242B), outlineVariant = Color(0xFF2E3038),
            ),
            hero = listOf(Color(0xFF0E1630), Color(0xFF15122A), Color(0xFF0B1A26)),
        ),
        swatch = listOf(Color.Black, Color(0xFFB0C6FF), Color(0xFF3D5A98)),
    )

    fun spec(theme: ColorTheme): Spec? = when (theme) {
        ColorTheme.CLASSIC -> null
        ColorTheme.OCEAN -> Ocean
        ColorTheme.FOREST -> Forest
        ColorTheme.SUNSET -> Sunset
        ColorTheme.BLOSSOM -> Blossom
        ColorTheme.MIDNIGHT -> Midnight
    }

    fun scheme(theme: ColorTheme, dark: Boolean): ColorScheme {
        val base = if (dark) DarkColors else LightColors
        val mode = spec(theme)?.let { if (dark) it.dark else it.light } ?: return base
        val p = mode.primary
        val s = mode.secondary
        val t = mode.tertiary
        val themed = base.copy(
            primary = p.main, onPrimary = p.onMain, primaryContainer = p.container, onPrimaryContainer = p.onContainer,
            inversePrimary = if (dark) lightPrimary(theme) else p.container,
            surfaceTint = p.main,
            secondary = s.main, onSecondary = s.onMain, secondaryContainer = s.container, onSecondaryContainer = s.onContainer,
            tertiary = t.main, onTertiary = t.onMain, tertiaryContainer = t.container, onTertiaryContainer = t.onContainer,
        )
        val n = mode.neutrals ?: return themed
        return themed.copy(
            background = n.background,
            surface = n.background,
            surfaceBright = n.low,
            surfaceDim = n.high,
            surfaceVariant = n.high,
            surfaceContainerLowest = n.lowest,
            surfaceContainerLow = n.low,
            surfaceContainer = n.container,
            surfaceContainerHigh = n.high,
            surfaceContainerHighest = n.highest,
            outlineVariant = n.outlineVariant,
        )
    }

    private fun lightPrimary(theme: ColorTheme): Color = spec(theme)?.light?.primary?.main ?: LightColors.primary

    fun hero(theme: ColorTheme, dark: Boolean): Brush? =
        spec(theme)?.let { Brush.linearGradient(if (dark) it.dark.hero else it.light.hero) }
}

/** Three colors that preview a theme in the theme picker (primary, accent, background hint). */
fun colorThemeSwatch(theme: ColorTheme): List<Color> =
    ThemePalettes.spec(theme)?.swatch ?: listOf(Palette.Lavender600, Palette.Lavender200, Palette.Mint700)

/** The primary color of [theme] in light or dark mode (for previews such as the icon picker). */
fun colorThemePrimary(theme: ColorTheme, dark: Boolean): Color = ThemePalettes.scheme(theme, dark).primary

/** The full Material color scheme of [theme] (for surfaces outside Compose UI, such as widgets). */
fun planBColorScheme(theme: ColorTheme, dark: Boolean): ColorScheme = ThemePalettes.scheme(theme, dark)
