package com.behnamjalali.planb

import android.content.res.Configuration
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.appcompat.app.AppCompatDelegate
import com.behnamjalali.planb.core.model.ThemeMode

/** Keeps the window (background, system bar icons) in line with the app's own theme setting. */
object AppTheme {
    fun nightMode(mode: ThemeMode): Int = when (mode) {
        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
    }

    /** At process start, before any activity exists, so the first window already uses the right resources. */
    fun applyStartupNightMode(mode: ThemeMode) = AppCompatDelegate.setDefaultNightMode(nightMode(mode))

    /**
     * After the user changes the theme. Only once [applyStartupNightMode] installed a mode
     * (not in hosts that skip the application class, such as tests): changing it recreates
     * the activity.
     */
    fun applyChangedNightMode(mode: ThemeMode) {
        val current = AppCompatDelegate.getDefaultNightMode()
        if (current != AppCompatDelegate.MODE_NIGHT_UNSPECIFIED && current != nightMode(mode)) {
            AppCompatDelegate.setDefaultNightMode(nightMode(mode))
        }
    }

    fun isDark(mode: ThemeMode, configuration: Configuration): Boolean = when (mode) {
        ThemeMode.SYSTEM -> configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    /** Transparent status bar whose icons contrast with the app theme, not the system theme. */
    fun statusBarStyle(dark: Boolean): SystemBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }

    /** Same scrims as the androidx defaults, chosen by the app theme. */
    fun navigationBarStyle(dark: Boolean): SystemBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark }

    private val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
    private val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
}
