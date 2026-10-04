package com.behnamjalali.planb.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.behnamjalali.planb.core.model.ColorTheme
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Every theme keeps readable text on its main roles (WCAG AA, 4.5:1). */
class ColorThemesTest {
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb).toDouble() / minOf(la, lb)
    }

    @Test
    fun everyThemeMeetsTextContrast() {
        for (theme in ColorTheme.entries) {
            for (dark in listOf(false, true)) {
                val s = ThemePalettes.scheme(theme, dark)
                val pairs = mapOf(
                    "onPrimary/primary" to (s.onPrimary to s.primary),
                    "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
                    "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
                    "onTertiaryContainer/tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
                    "onBackground/background" to (s.onBackground to s.background),
                    "onSurfaceVariant/surfaceContainer" to (s.onSurfaceVariant to s.surfaceContainer),
                    "primary/background" to (s.primary to s.background),
                )
                pairs.forEach { (name, pair) ->
                    assertWithMessage("$theme dark=$dark $name").that(contrast(pair.first, pair.second)).isAtLeast(4.5)
                }
            }
        }
    }

    @Test
    fun midnightDarkIsTrueBlack() {
        val s = ThemePalettes.scheme(ColorTheme.MIDNIGHT, dark = true)
        assertWithMessage("background").that(s.background).isEqualTo(Color.Black)
    }

    @Test
    fun classicIsTheBrandPalette() {
        assertWithMessage("light").that(ThemePalettes.scheme(ColorTheme.CLASSIC, false)).isEqualTo(LightColors)
        assertWithMessage("dark").that(ThemePalettes.scheme(ColorTheme.CLASSIC, true)).isEqualTo(DarkColors)
    }
}
