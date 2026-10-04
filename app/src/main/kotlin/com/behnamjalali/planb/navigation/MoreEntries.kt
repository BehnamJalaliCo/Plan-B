package com.behnamjalali.planb.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import com.behnamjalali.planb.R
import com.behnamjalali.planb.feature.focus.FocusRoute
import com.behnamjalali.planb.feature.goals.GoalsRoute
import com.behnamjalali.planb.feature.pro.PaywallRoute
import com.behnamjalali.planb.feature.habits.HabitsRoute
import com.behnamjalali.planb.feature.projects.ProjectsRoute
import com.behnamjalali.planb.feature.reports.StatisticsRoute
import com.behnamjalali.planb.feature.review.ReviewRoute
import com.behnamjalali.planb.feature.search.SearchRoute
import com.behnamjalali.planb.feature.settings.SettingsRoute
import com.behnamjalali.planb.feature.templates.TemplatesRoute

data class MoreEntry(
    @StringRes val section: Int,
    @StringRes val label: Int,
    val icon: ImageVector,
    val route: Any,
    @StringRes val subtitle: Int? = null,
)

/** Secondary destinations, grouped by section. */
object MoreEntries {
    val all: List<MoreEntry> = listOf(
        MoreEntry(R.string.more_section_pro, R.string.more_pro, Icons.Rounded.WorkspacePremium, PaywallRoute(), R.string.more_pro_sub),
        MoreEntry(R.string.more_section_plan, R.string.more_projects, Icons.Rounded.RocketLaunch, ProjectsRoute, R.string.more_projects_sub),
        MoreEntry(R.string.more_section_plan, R.string.more_templates, Icons.Rounded.Dashboard, TemplatesRoute, R.string.more_templates_sub),
        MoreEntry(R.string.more_section_plan, R.string.more_search, Icons.Rounded.Search, SearchRoute, R.string.more_search_sub),
        MoreEntry(R.string.more_section_grow, R.string.more_habits, Icons.Rounded.Repeat, HabitsRoute, R.string.more_habits_sub),
        MoreEntry(R.string.more_section_grow, R.string.more_goals, Icons.Rounded.Flag, GoalsRoute, R.string.more_goals_sub),
        MoreEntry(R.string.more_section_grow, R.string.more_focus, Icons.Rounded.Timer, FocusRoute, R.string.more_focus_sub),
        MoreEntry(R.string.more_section_grow, R.string.more_review, Icons.Rounded.Insights, ReviewRoute, R.string.more_review_sub),
        MoreEntry(R.string.more_section_grow, R.string.more_statistics, Icons.Rounded.QueryStats, StatisticsRoute, R.string.more_statistics_sub),
        MoreEntry(R.string.more_section_app, R.string.more_settings, Icons.Rounded.Settings, SettingsRoute, R.string.more_settings_sub),
    )
}
