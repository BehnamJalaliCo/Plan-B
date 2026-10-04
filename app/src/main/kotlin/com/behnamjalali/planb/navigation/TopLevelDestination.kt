package com.behnamjalali.planb.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.behnamjalali.planb.R
import com.behnamjalali.planb.feature.calendar.CalendarRoute
import com.behnamjalali.planb.feature.notebooks.NotebooksRoute
import com.behnamjalali.planb.feature.tasks.TasksRoute
import com.behnamjalali.planb.feature.today.TodayRoute
import kotlinx.serialization.Serializable

@Serializable
data object MoreRoute

enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val showsCapture: Boolean = true,
) {
    TODAY(TodayRoute, R.string.nav_today, Icons.Outlined.WbSunny, Icons.Rounded.WbSunny),
    CALENDAR(CalendarRoute, R.string.nav_calendar, Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    TASKS(TasksRoute, R.string.nav_tasks, Icons.Outlined.CheckCircle, Icons.Rounded.CheckCircle),
    NOTEBOOKS(NotebooksRoute, R.string.nav_notebooks, Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Rounded.MenuBook),
    MORE(MoreRoute, R.string.nav_more, Icons.Outlined.Widgets, Icons.Rounded.Widgets, showsCapture = false),
}
