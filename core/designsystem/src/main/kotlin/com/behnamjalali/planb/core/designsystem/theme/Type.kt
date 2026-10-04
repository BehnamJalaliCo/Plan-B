package com.behnamjalali.planb.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.behnamjalali.planb.core.designsystem.R

/**
 * The app typeface: Anjoman Max (licensed, supplied at build time; see core/designsystem's build
 * file) covers Persian and Latin, so both languages share one family. Sizes are in sp to respect
 * font scaling. No letter spacing is applied: tracking breaks Persian cursive joining.
 */
val PlanBFont = FontFamily(
    Font(R.font.planb_regular, FontWeight.Normal),
    Font(R.font.planb_medium, FontWeight.Medium),
    Font(R.font.planb_semibold, FontWeight.SemiBold),
    Font(R.font.planb_bold, FontWeight.Bold),
)

private fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = PlanBFont,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
)

internal val PlanBTypography = Typography(
    displayLarge = style(40, 52, FontWeight.Bold),
    displayMedium = style(34, 44, FontWeight.Bold),
    displaySmall = style(30, 40, FontWeight.SemiBold),
    headlineLarge = style(28, 38, FontWeight.SemiBold),
    headlineMedium = style(24, 34, FontWeight.SemiBold),
    headlineSmall = style(21, 30, FontWeight.SemiBold),
    titleLarge = style(19, 28, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.Medium),
    titleSmall = style(14, 22, FontWeight.Medium),
    bodyLarge = style(16, 26, FontWeight.Normal),
    bodyMedium = style(14, 22, FontWeight.Normal),
    bodySmall = style(12, 18, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 18, FontWeight.Medium),
    labelSmall = style(11, 16, FontWeight.Medium),
)

/** Semantic aliases used by components. */
object PlannerType {
    val caption: TextStyle get() = PlanBTypography.bodySmall
}
