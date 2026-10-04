package com.behnamjalali.planb.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Work
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.behnamjalali.planb.core.model.PlannerIcon

val PlannerIcon.vector: ImageVector
    get() = when (this) {
        PlannerIcon.FOLDER -> Icons.Rounded.Folder
        PlannerIcon.BRIEFCASE -> Icons.Rounded.Work
        PlannerIcon.BOOK -> Icons.AutoMirrored.Rounded.MenuBook
        PlannerIcon.HOME -> Icons.Rounded.Home
        PlannerIcon.HEART -> Icons.Rounded.Favorite
        PlannerIcon.STAR -> Icons.Rounded.Star
        PlannerIcon.ROCKET -> Icons.Rounded.RocketLaunch
        PlannerIcon.LIGHTBULB -> Icons.Rounded.Lightbulb
        PlannerIcon.SCHOOL -> Icons.Rounded.School
        PlannerIcon.FITNESS -> Icons.Rounded.FitnessCenter
        PlannerIcon.WATER -> Icons.Rounded.WaterDrop
        PlannerIcon.MEDITATION -> Icons.Rounded.SelfImprovement
        PlannerIcon.RUN -> Icons.AutoMirrored.Rounded.DirectionsRun
        PlannerIcon.SLEEP -> Icons.Rounded.Bedtime
        PlannerIcon.CODE -> Icons.Rounded.Code
        PlannerIcon.PALETTE -> Icons.Rounded.Brush
        PlannerIcon.MUSIC -> Icons.Rounded.MusicNote
        PlannerIcon.TRAVEL -> Icons.Rounded.Flight
        PlannerIcon.MONEY -> Icons.Rounded.Payments
        PlannerIcon.LEAF -> Icons.Rounded.Eco
    }

@Composable
fun PlannerIcon.label(): String = stringResource(
    when (this) {
        PlannerIcon.FOLDER -> R.string.ui_icon_folder
        PlannerIcon.BRIEFCASE -> R.string.ui_icon_briefcase
        PlannerIcon.BOOK -> R.string.ui_icon_book
        PlannerIcon.HOME -> R.string.ui_icon_home
        PlannerIcon.HEART -> R.string.ui_icon_heart
        PlannerIcon.STAR -> R.string.ui_icon_star
        PlannerIcon.ROCKET -> R.string.ui_icon_rocket
        PlannerIcon.LIGHTBULB -> R.string.ui_icon_lightbulb
        PlannerIcon.SCHOOL -> R.string.ui_icon_school
        PlannerIcon.FITNESS -> R.string.ui_icon_fitness
        PlannerIcon.WATER -> R.string.ui_icon_water
        PlannerIcon.MEDITATION -> R.string.ui_icon_meditation
        PlannerIcon.RUN -> R.string.ui_icon_run
        PlannerIcon.SLEEP -> R.string.ui_icon_sleep
        PlannerIcon.CODE -> R.string.ui_icon_code
        PlannerIcon.PALETTE -> R.string.ui_icon_palette
        PlannerIcon.MUSIC -> R.string.ui_icon_music
        PlannerIcon.TRAVEL -> R.string.ui_icon_travel
        PlannerIcon.MONEY -> R.string.ui_icon_money
        PlannerIcon.LEAF -> R.string.ui_icon_leaf
    },
)
