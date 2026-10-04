package com.behnamjalali.planb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.R
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.component.SettingsRow
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.navigation.MoreEntries
import com.behnamjalali.planb.navigation.MoreEntry

/** Secondary navigation hub for features that do not fit the bottom bar. */
@Composable
fun MoreScreen(
    onNavigate: (Any) -> Unit,
    contentPadding: PaddingValues,
    entries: List<MoreEntry> = MoreEntries.all,
) {
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(title = stringResource(R.string.more_title))
        LazyColumn(
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            entries.groupBy { it.section }.forEach { (section, list) ->
                item(key = "s_$section") { PlannerSectionHeader(stringResource(section)) }
                items(list, key = { it.label }) { entry ->
                    SettingsRow(
                        title = stringResource(entry.label),
                        subtitle = entry.subtitle?.let { stringResource(it) },
                        icon = entry.icon,
                        onClick = { onNavigate(entry.route) },
                    )
                }
            }
        }
    }
}

