package com.behnamjalali.planb.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector

data class MoreEntry(
    @StringRes val section: Int,
    @StringRes val label: Int,
    val icon: ImageVector,
    val route: Any,
    @StringRes val subtitle: Int? = null,
)

/** Secondary destinations, grouped by section. */
object MoreEntries {
    val all: List<MoreEntry> = listOf()
}
