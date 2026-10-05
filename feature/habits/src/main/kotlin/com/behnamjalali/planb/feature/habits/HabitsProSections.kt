package com.behnamjalali.planb.feature.habits

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.behnamjalali.planb.core.data.wellbeing.HealthAvailability
import com.behnamjalali.planb.core.designsystem.component.PlannerBottomSheet
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerProgressBar
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.component.SettingsRow
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.health.HealthConnectPermissions
import com.behnamjalali.planb.core.model.ChallengeRules
import com.behnamjalali.planb.core.model.HealthHabits
import com.behnamjalali.planb.core.model.HealthMetric
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProBadge
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.rememberProGuard
import java.time.temporal.ChronoUnit

@Composable
internal fun healthLabel(metric: HealthMetric): String = stringResource(
    when (metric) {
        HealthMetric.STEPS -> R.string.habit_health_steps
        HealthMetric.SLEEP_MINUTES -> R.string.habit_health_sleep
        HealthMetric.HYDRATION_ML -> R.string.habit_health_water
        HealthMetric.ACTIVE_MINUTES -> R.string.habit_health_active
        HealthMetric.DISTANCE_METERS -> R.string.habit_health_distance
    },
)

@Composable
private fun healthData(metric: HealthMetric): String = stringResource(
    when (metric) {
        HealthMetric.STEPS -> R.string.habit_health_data_steps
        HealthMetric.SLEEP_MINUTES -> R.string.habit_health_data_sleep
        HealthMetric.HYDRATION_ML -> R.string.habit_health_data_water
        HealthMetric.ACTIVE_MINUTES -> R.string.habit_health_data_active
        HealthMetric.DISTANCE_METERS -> R.string.habit_health_data_distance
    },
)

/** An amount of [metric] in its display unit, with the user's digits. */
@Composable
internal fun healthValue(metric: HealthMetric, value: Long): String {
    val numbers = PlannerLocals.numbers
    return stringResource(
        when (metric) {
            HealthMetric.STEPS -> R.string.habit_health_value_steps
            HealthMetric.SLEEP_MINUTES -> R.string.habit_health_value_sleep
            HealthMetric.HYDRATION_ML -> R.string.habit_health_value_water
            HealthMetric.ACTIVE_MINUTES -> R.string.habit_health_value_active
            HealthMetric.DISTANCE_METERS -> R.string.habit_health_value_distance
        },
        numbers.localize(HealthUnits.format(metric, value)),
    )
}

/**
 * The habit editor's Health Connect section (Plan-B Pro #27): pick the data and a daily goal;
 * access is asked in context (an explanation, then Health Connect's own permission screen) for
 * just that kind of data. Devices without Health Connect get a plain explanation, Android 9–13
 * a way to install it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HealthSection(form: HabitForm, health: HealthEditorState, viewModel: HabitEditorViewModel) {
    val isPro = LocalProAccess.current.isPro
    val guard = rememberProGuard()
    val context = LocalContext.current
    var cannotOpen by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(HealthConnectPermissions.requestContract()) { viewModel.refreshHealth() }
    val metric = form.metric
    PlannerCard(Modifier.fillMaxWidth(), onClick = if (isPro) null else ({ guard.run(ProFeature.HEALTH_CONNECT) {} })) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.habit_health_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.habit_health_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!isPro) ProBadge()
        }
        if (!isPro) {
            // Kept from when Pro was active: shown, not editable.
            if (metric != null) {
                Spacer(Modifier.height(Spacing.sm))
                Text(healthLabel(metric) + com.behnamjalali.planb.core.ui.metaSeparator() + (form.thresholdValue?.let { healthValue(metric, it) } ?: form.healthThreshold), style = MaterialTheme.typography.bodyMedium)
            }
            return@PlannerCard
        }
        Spacer(Modifier.height(Spacing.sm))
        val unsupported = health.availability == HealthAvailability.UNSUPPORTED
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PlannerChip(stringResource(R.string.habit_health_off), metric == null, { viewModel.setHealthMetric(null) })
            if (!unsupported || metric != null) {
                HealthMetric.entries.forEach { m -> PlannerChip(healthLabel(m), metric == m, { viewModel.setHealthMetric(m) }) }
            }
        }
        when {
            unsupported -> Text(stringResource(R.string.habit_health_unsupported), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            metric == null -> Unit
            else -> {
                Spacer(Modifier.height(Spacing.sm))
                val range = HealthHabits.range(metric)
                PlannerTextField(
                    form.healthThreshold,
                    { v -> viewModel.update { it.copy(healthThreshold = v.take(8)) } },
                    stringResource(
                        when (metric) {
                            HealthMetric.STEPS -> R.string.habit_health_goal_steps
                            HealthMetric.SLEEP_MINUTES -> R.string.habit_health_goal_sleep
                            HealthMetric.HYDRATION_ML -> R.string.habit_health_goal_water
                            HealthMetric.ACTIVE_MINUTES -> R.string.habit_health_goal_active
                            HealthMetric.DISTANCE_METERS -> R.string.habit_health_goal_distance
                        },
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = if (metric == HealthMetric.SLEEP_MINUTES || metric == HealthMetric.DISTANCE_METERS) KeyboardType.Decimal else KeyboardType.Number),
                    isError = !form.thresholdValid,
                    supportingText = if (!form.thresholdValid) stringResource(R.string.habit_health_goal_invalid, healthValue(metric, range.first), healthValue(metric, range.last)) else null,
                )
                when (health.availability) {
                    HealthAvailability.NEEDS_INSTALL -> {
                        Text(stringResource(R.string.habit_health_install), style = MaterialTheme.typography.bodySmall)
                        PlannerButton(
                            stringResource(R.string.habit_health_install_button),
                            { cannotOpen = !HealthConnectPermissions.openInstall(context) },
                            style = PlannerButtonStyle.Tonal,
                        )
                    }
                    else -> if (health.granted) {
                        Text(stringResource(R.string.habit_health_connected), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PlannerButton(
                            stringResource(R.string.habit_health_open),
                            { cannotOpen = !HealthConnectPermissions.openHealthConnect(context) },
                            style = PlannerButtonStyle.Text,
                        )
                    } else {
                        Text(stringResource(R.string.habit_health_explain, healthData(metric)), style = MaterialTheme.typography.bodySmall)
                        PlannerButton(
                            stringResource(R.string.habit_health_allow),
                            { runCatching { request.launch(viewModel.healthPermissions()) }.onFailure { cannotOpen = true } },
                            style = PlannerButtonStyle.Tonal,
                        )
                    }
                }
                if (cannotOpen) Text(stringResource(R.string.habit_health_cannot_open), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * Plan-B Pro on the habit screen: today's Health Connect amount (#27), the running challenge or
 * a way to start one (#29) and the way into advanced statistics (#28). Free users see the
 * entries with a Pro badge; a tap opens the Pro screen.
 */
@Composable
internal fun HabitProSection(state: HabitDetailUiState, pro: HabitProActions) {
    val isPro = LocalProAccess.current.isPro
    val guard = rememberProGuard()
    val habit = state.item?.habit ?: return
    val numbers = PlannerLocals.numbers
    var choosing by rememberSaveable { mutableStateOf(false) }
    habit.healthMetric?.let { metric ->
        PlannerCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    val goal = habit.healthThreshold
                    val reading = state.health
                    Text(
                        when {
                            reading != null && goal != null -> stringResource(R.string.habit_health_today, healthValue(metric, reading.value), healthValue(metric, goal))
                            goal != null -> stringResource(R.string.habit_health_linked, healthValue(metric, goal))
                            else -> healthLabel(metric)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (reading == null && isPro) {
                        Text(stringResource(R.string.habit_health_no_access), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (reading != null && goal != null) {
                        Spacer(Modifier.height(Spacing.xs))
                        PlannerProgressBar((reading.value.toFloat() / goal).coerceIn(0f, 1f))
                    }
                }
                if (isPro && state.health != null) {
                    com.behnamjalali.planb.core.designsystem.component.PlannerIconButton(Icons.Rounded.Sync, stringResource(R.string.habit_health_sync), pro.onSyncHealth)
                }
            }
        }
    }
    val challenge = state.challenge
    if (challenge != null) {
        val c = challenge.challenge
        val day = (ChronoUnit.DAYS.between(c.startDate, state.today) + 1).coerceIn(1, c.targetDays.toLong()).toInt()
        val text = stringResource(R.string.habit_challenge_running, numbers.format(c.targetDays), numbers.format(day))
        PlannerCard(Modifier.fillMaxWidth(), onClick = pro.onOpenChallenges) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Column(Modifier.weight(1f)) {
                    Text(text, style = MaterialTheme.typography.titleSmall)
                    val progressText = stringResource(R.string.challenge_progress, numbers.format(challenge.done), numbers.format(challenge.required))
                    Text(progressText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.xs))
                    PlannerProgressBar(challenge.fraction, Modifier.semantics { contentDescription = progressText })
                }
            }
        }
    } else if (!habit.archived) {
        SettingsRow(
            title = stringResource(R.string.habit_challenge_start),
            icon = Icons.Rounded.EmojiEvents,
            onClick = { guard.run(ProFeature.CHALLENGES) { choosing = true } },
            trailing = if (isPro) null else ({ ProBadge() }),
        )
    }
    SettingsRow(
        title = stringResource(R.string.habit_stats_open),
        subtitle = stringResource(R.string.habit_stats_open_sub),
        icon = Icons.Rounded.QueryStats,
        onClick = { guard.run(ProFeature.HABIT_STATS) { pro.onOpenStats() } },
        trailing = if (isPro) null else ({ ProBadge() }),
    )
    if (choosing) {
        PlannerBottomSheet(onDismiss = { choosing = false }) {
            Text(stringResource(R.string.challenges_new_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.challenges_rules), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ChallengeRules.LENGTHS.forEach { length ->
                    PlannerButton(
                        pluralStringResource(R.plurals.challenges_length, length, numbers.format(length)),
                        {
                            choosing = false
                            pro.onStartChallenge(length)
                        },
                        Modifier.fillMaxWidth(),
                        style = PlannerButtonStyle.Tonal,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}
