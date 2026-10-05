package com.behnamjalali.planb.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.core.ui.PlannerLocals
import java.time.LocalDate

/** What of Iran's official calendar is drawn (Plan-B Pro #2): settings and Pro together. */
@Immutable
data class ShownDecorations(val holidays: Boolean, val occasions: Boolean, val hijri: Boolean) {
    val any: Boolean get() = holidays || occasions || hijri

    companion object {
        val None = ShownDecorations(holidays = false, occasions = false, hijri = false)
    }
}

@Composable
internal fun shownDecorations(state: CalendarUiState): ShownDecorations =
    if (!LocalProAccess.current.isPro) {
        ShownDecorations.None
    } else {
        ShownDecorations(state.decorations.holidays, state.decorations.occasions, state.decorations.hijriDate)
    }

/** A day is drawn as a day off: an official holiday, or Friday in the Jalali calendar. */
internal fun CalendarUiState.offDay(date: LocalDate, shown: ShownDecorations): Boolean = shown.holidays && isOffDay(date)

/** Names of the occasions of [date] that are shown, holidays first. */
@Composable
internal fun occasionNames(state: CalendarUiState, date: LocalDate, shown: ShownDecorations): List<Pair<String, Boolean>> =
    state.occasionsOn(date)
        .filter { (it.holiday && shown.holidays) || (!it.holiday && shown.occasions) }
        .map { stringResource(it.occasion.title) to it.holiday }
        .distinct()

/**
 * The lines under a day's title: official holidays (in the error color), other occasions, and
 * the Hijri date with the reminder that lunar dates may move by a day.
 */
@Composable
internal fun IranDayDetails(state: CalendarUiState, date: LocalDate, shown: ShownDecorations, modifier: Modifier = Modifier) {
    if (!shown.any) return
    val names = occasionNames(state, date, shown)
    val lunarShown = state.occasionsOn(date).any { it.lunar && ((it.holiday && shown.holidays) || (!it.holiday && shown.occasions)) }
    val hijri = state.hijri?.takeIf { shown.hijri && date == state.selected }
    if (names.isEmpty() && hijri == null) return
    val formatter = PlannerLocals.formatter
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        names.forEach { (name, holiday) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Icon(
                    if (holiday) Icons.Rounded.Celebration else Icons.Rounded.Event,
                    contentDescription = null,
                    tint = if (holiday) scheme.error else scheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.sm),
                )
                Text(
                    if (holiday) stringResource(R.string.calendar_holiday_named, name) else name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (holiday) scheme.error else scheme.onSurfaceVariant,
                )
            }
        }
        if (hijri != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Icon(Icons.Rounded.DarkMode, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(IconSize.sm))
                Text(formatter.hijriDate(hijri), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
        if (hijri != null || lunarShown) {
            Text(
                stringResource(R.string.calendar_lunar_note),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.outline,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
    }
}
