package com.behnamjalali.planb.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.behnamjalali.planb.core.designsystem.R

/**
 * Vazirmatn (SIL OFL 1.1) covers Persian and Latin with harmonized metrics, so
 * both languages share one family. Sizes are in sp to respect font scaling.
 */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Vazirmatn,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

internal val PlanBTypography = Typography(
    displayLarge = style(40, 52, FontWeight.Bold, -0.01),
    displayMedium = style(34, 44, FontWeight.Bold, -0.01),
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
