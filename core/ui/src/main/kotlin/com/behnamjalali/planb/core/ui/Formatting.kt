package com.behnamjalali.planb.core.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.behnamjalali.planb.core.common.NumberFormatter
import com.behnamjalali.planb.core.datetime.PlannerDateFormatter
import com.behnamjalali.planb.core.model.CalendarSystem
import java.time.DayOfWeek
import java.time.LocalDate

val LocalDateFormatter = staticCompositionLocalOf<PlannerDateFormatter> { error("No PlannerDateFormatter provided") }

/** The current local date; the app root updates it when the day changes. */
val LocalToday = staticCompositionLocalOf<LocalDate> { LocalDate.now() }

object PlannerLocals {
    val formatter: PlannerDateFormatter
        @Composable @ReadOnlyComposable get() = LocalDateFormatter.current
    val numbers: NumberFormatter
        @Composable @ReadOnlyComposable get() = LocalDateFormatter.current.numbers
    val today: LocalDate
        @Composable @ReadOnlyComposable get() = LocalToday.current
}

/** Builds a formatter bound to the current (localized) resources. */
@Composable
fun rememberDateFormatter(
    calendarSystem: CalendarSystem,
    firstDayOfWeek: DayOfWeek,
    persianDigits: Boolean,
): PlannerDateFormatter {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration, calendarSystem, firstDayOfWeek, persianDigits) {
        PlannerDateFormatter(
            resources = context.resources,
            calendarSystem = calendarSystem,
            firstDayOfWeek = firstDayOfWeek,
            numbers = NumberFormatter(persianDigits),
            use24Hour = persianDigits || DateFormat.is24HourFormat(context),
        )
    }
}
