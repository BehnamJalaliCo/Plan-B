package com.behnamjalali.planb.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.UserSettings
import com.behnamjalali.planb.core.ui.LocalDateFormatter
import com.behnamjalali.planb.core.ui.LocalToday
import com.behnamjalali.planb.core.ui.rememberDateFormatter
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.robolectric.RuntimeEnvironment

/** One screenshot variant: language × theme × font scale. */
data class Variant(val language: AppLanguage, val dark: Boolean, val fontScale: Float = 1f) {
    val suffix: String
        get() = buildString {
            append(if (language == AppLanguage.PERSIAN) "fa" else "en")
            append(if (dark) "_dark" else "_light")
            if (fontScale != 1f) append("_font").append((fontScale * 100).toInt())
        }

    override fun toString(): String = suffix

    companion object {
        val STANDARD = listOf(
            Variant(AppLanguage.PERSIAN, dark = false),
            Variant(AppLanguage.PERSIAN, dark = true),
            Variant(AppLanguage.ENGLISH, dark = false),
            Variant(AppLanguage.ENGLISH, dark = true),
        )
        val LARGE_FONT = listOf(
            Variant(AppLanguage.PERSIAN, dark = false, fontScale = 1.5f),
            Variant(AppLanguage.ENGLISH, dark = false, fontScale = 1.5f),
        )
    }
}

/** Repository-root screenshot folder; the same files feed docs/UI_GALLERY.md. */
private val screenshotRoot: File by lazy {
    var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (dir != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
    File(requireNotNull(dir) { "Repository root not found" }, "artifacts/screenshots")
}

/** Applies locale/RTL qualifiers before content is set (must be called first). */
fun applyQualifiers(variant: Variant) {
    val locale = if (variant.language == AppLanguage.PERSIAN) "fa-rIR-ldrtl" else "en-rUS-ldltr"
    val night = if (variant.dark) "night" else "notnight"
    RuntimeEnvironment.setQualifiers("+$locale-$night")
    RuntimeEnvironment.setFontScale(variant.fontScale)
}

@Composable
fun ScreenshotTheme(variant: Variant, fixtures: Fixtures, content: @Composable () -> Unit) {
    val settings = UserSettings(language = variant.language)
    PlanBTheme(darkTheme = variant.dark, animationsEnabled = false) {
        val formatter = rememberDateFormatter(settings.calendarSystem, settings.firstDayOfWeek, settings.usePersianDigits)
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDateFormatter provides formatter,
            LocalToday provides fixtures.today,
            LocalDensity provides Density(density.density, variant.fontScale),
        ) {
            // Mirrors the app Scaffold: background + onBackground content color.
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
        }
    }
}

fun ComposeContentTestRule.captureScreen(folder: String, name: String, variant: Variant, content: @Composable (Fixtures) -> Unit) {
    applyQualifiers(variant)
    val fixtures = Fixtures(variant.language)
    setContent { ScreenshotTheme(variant, fixtures) { content(fixtures) } }
    waitForIdle()
    onRoot().captureRoboImage(File(screenshotRoot, "$folder/${name}_${variant.suffix}.png").path)
}
