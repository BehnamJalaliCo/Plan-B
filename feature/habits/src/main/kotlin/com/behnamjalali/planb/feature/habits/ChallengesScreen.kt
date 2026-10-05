package com.behnamjalali.planb.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.data.repository.BadgeStatus
import com.behnamjalali.planb.core.designsystem.component.PlannerBottomSheet
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerPill
import com.behnamjalali.planb.core.designsystem.component.PlannerProgressBar
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.ChallengeDayState
import com.behnamjalali.planb.core.model.ChallengeProgress
import com.behnamjalali.planb.core.model.ChallengeRules
import com.behnamjalali.planb.core.model.ChallengeStatus
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProBadge
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProTeaser
import com.behnamjalali.planb.core.ui.metaSeparator
import com.behnamjalali.planb.core.ui.rememberProGuard
import java.time.temporal.ChronoUnit

@Composable
fun ChallengesDestination(onBack: () -> Unit, snackbarHostState: SnackbarHostState, viewModel: ChallengesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val isPro = LocalProAccess.current.isPro
    val resources = LocalResources.current
    // Statuses and new badges are brought up to date when the screen opens (Pro only).
    LaunchedEffect(isPro) { viewModel.refresh(isPro) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(
                resources.getString(
                    when (event) {
                        ChallengesEvent.Started -> R.string.habit_challenge_started
                        ChallengesEvent.Failed -> com.behnamjalali.planb.core.ui.R.string.ui_error_generic
                    },
                ),
            )
        }
    }
    ChallengesScreen(state, tab, onBack, viewModel::selectTab, viewModel::start, viewModel::abandon, viewModel::delete)
}

/**
 * Challenges and badges (Plan-B Pro #29). Running challenges show their days as dots (done,
 * missed, today, to come, not due); earlier ones can be tried again. The gallery shows every
 * badge with its progress. Without Pro, what was earned stays visible and a teaser offers new
 * challenges.
 */
@Composable
fun ChallengesScreen(
    state: ChallengesUi,
    tab: Int,
    onBack: () -> Unit,
    onTab: (Int) -> Unit,
    onStart: (EntityId, Int) -> Unit,
    onGiveUp: (EntityId) -> Unit,
    onDelete: (EntityId) -> Unit,
) {
    val isPro = LocalProAccess.current.isPro
    val guard = rememberProGuard()
    var starting by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(title = stringResource(R.string.challenges_title), onBack = onBack)
        if (state.loading) {
            PlannerLoadingState()
            return@Column
        }
        val hasData = state.active.isNotEmpty() || state.finished.isNotEmpty() || state.earned > 0
        if (!isPro && !hasData) {
            ProTeaser(ProFeature.CHALLENGES, Modifier.padding(horizontal = Spacing.screen))
            return@Column
        }
        Row(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PlannerChip(stringResource(R.string.challenges_tab), tab == 0, { onTab(0) }, icon = Icons.Rounded.EmojiEvents)
            PlannerChip(stringResource(R.string.badges_tab), tab == 1, { onTab(1) })
        }
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.huge),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (!isPro) item(key = "read_only") { ProTeaser(ProFeature.CHALLENGES) }
            if (tab == 0) {
                item(key = "start") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(stringResource(R.string.challenges_rules), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            PlannerButton(
                                stringResource(R.string.challenges_start),
                                { guard.run(ProFeature.CHALLENGES) { starting = true } },
                                icon = Icons.Rounded.Add,
                                enabled = !isPro || state.habits.isNotEmpty(),
                            )
                            if (!isPro) ProBadge()
                        }
                        if (isPro && state.habits.isEmpty()) {
                            Text(
                                stringResource(if (state.active.isEmpty()) R.string.challenges_no_habits else R.string.challenges_all_busy),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (state.active.isEmpty() && state.finished.isEmpty()) {
                    item(key = "empty") { Text(stringResource(R.string.challenges_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (state.active.isNotEmpty()) item(key = "running_h") { PlannerSectionHeader(stringResource(R.string.challenges_running)) }
                items(state.active, key = { "c${it.challenge.id}" }) { ChallengeCard(it, isPro, onStart, onGiveUp, onDelete) }
                if (state.finished.isNotEmpty()) item(key = "earlier_h") { PlannerSectionHeader(stringResource(R.string.challenges_earlier)) }
                items(state.finished, key = { "c${it.challenge.id}" }) { ChallengeCard(it, isPro, onStart, onGiveUp, onDelete) }
            } else {
                item(key = "badges") { BadgeGallery(state.badges) }
            }
        }
    }
    if (starting) StartChallengeSheet(state.habits, onDismiss = { starting = false }) { habit, days ->
        starting = false
        onStart(habit, days)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartChallengeSheet(habits: List<Habit>, onDismiss: () -> Unit, onStart: (EntityId, Int) -> Unit) {
    var habitId by rememberSaveable { mutableStateOf(habits.firstOrNull()?.id) }
    var days by rememberSaveable { mutableStateOf(ChallengeRules.LENGTHS[1]) }
    val numbers = PlannerLocals.numbers
    PlannerBottomSheet(onDismiss = onDismiss) {
        Text(stringResource(R.string.challenges_new_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.md))
        Text(stringResource(R.string.challenges_pick_habit), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            habits.forEach { habit -> PlannerChip(habit.title, habitId == habit.id, { habitId = habit.id }, accent = PlanBTheme.colors.accent(habit.color)) }
        }
        Spacer(Modifier.height(Spacing.md))
        Text(stringResource(R.string.challenges_pick_length), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ChallengeRules.LENGTHS.forEach { length ->
                PlannerChip(pluralStringResource(R.plurals.challenges_length, length, numbers.format(length)), days == length, { days = length })
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        PlannerButton(
            stringResource(R.string.challenges_start_confirm),
            { habitId?.let { onStart(it, days) } },
            Modifier.fillMaxWidth(),
            enabled = habitId != null,
        )
        Spacer(Modifier.height(Spacing.md))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChallengeCard(
    progress: ChallengeProgress,
    isPro: Boolean,
    onStart: (EntityId, Int) -> Unit,
    onGiveUp: (EntityId) -> Unit,
    onDelete: (EntityId) -> Unit,
) {
    val c = progress.challenge
    val formatter = PlannerLocals.formatter
    val numbers = PlannerLocals.numbers
    val today = PlannerLocals.today
    var menu by remember { mutableStateOf(false) }
    PlannerCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.challenges_length, c.targetDays, numbers.format(c.targetDays)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                PlannerIconButton(Icons.Rounded.MoreVert, stringResource(R.string.challenge_menu), { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val running = c.status == ChallengeStatus.ACTIVE && progress.status != ChallengeStatus.ABANDONED
                    if (running) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.challenge_give_up)) }, onClick = { menu = false; onGiveUp(c.id) })
                    }
                    if (isPro && !running && c.habitId != null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.challenge_try_again)) }, onClick = { menu = false; onStart(c.habitId!!, c.targetDays) })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.challenge_delete)) }, onClick = { menu = false; onDelete(c.id) })
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        when (progress.status) {
            ChallengeStatus.COMPLETED -> PlannerPill(
                stringResource(R.string.challenge_completed, formatter.mediumDate(progress.completedOn ?: c.endDate)),
                icon = Icons.Rounded.Check,
                container = PlanBTheme.colors.success.copy(alpha = 0.18f),
                content = MaterialTheme.colorScheme.onSurface,
            )
            ChallengeStatus.FAILED -> {
                Text(stringResource(R.string.challenge_failed, formatter.mediumDate(progress.failedOn ?: c.startDate)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                if (c.status == ChallengeStatus.ACTIVE) {
                    Text(stringResource(R.string.challenge_failed_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ChallengeStatus.ABANDONED -> Text(stringResource(R.string.challenge_abandoned), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChallengeStatus.ACTIVE -> {
                val day = (ChronoUnit.DAYS.between(c.startDate, today) + 1).coerceIn(1, c.targetDays.toLong()).toInt()
                Text(
                    stringResource(R.string.challenge_day, numbers.format(day), numbers.format(c.targetDays)) + metaSeparator() + stringResource(R.string.challenge_ends, formatter.mediumDate(c.endDate)),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        val progressText = stringResource(R.string.challenge_progress, numbers.format(progress.done), numbers.format(progress.required))
        PlannerProgressBar(progress.fraction, Modifier.fillMaxWidth().semantics { contentDescription = progressText })
        Text(progressText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.sm))
        DayDots(progress)
    }
}

/** The challenge's days in reading order; one spoken summary, every day described for exploration. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayDots(progress: ChallengeProgress) {
    val numbers = PlannerLocals.numbers
    val done = progress.days.count { it.second == ChallengeDayState.DONE }
    val missed = progress.days.count { it.second == ChallengeDayState.MISSED }
    val left = progress.days.count { it.second == ChallengeDayState.FUTURE || it.second == ChallengeDayState.TODAY }
    val summary = stringResource(R.string.challenge_days_cd, numbers.format(done), numbers.format(missed), numbers.format(left))
    val scheme = MaterialTheme.colorScheme
    val success = PlanBTheme.colors.success
    FlowRow(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        progress.days.forEach { (_, state) ->
            val size = if (progress.days.size > 30) 10.dp else 14.dp
            Box(
                Modifier.size(size).background(
                    when (state) {
                        ChallengeDayState.DONE -> success
                        ChallengeDayState.MISSED -> scheme.error
                        ChallengeDayState.TODAY -> scheme.primary.copy(alpha = 0.45f)
                        ChallengeDayState.FUTURE -> scheme.surfaceContainerHighest
                        ChallengeDayState.REST -> scheme.outlineVariant.copy(alpha = 0.5f)
                    },
                    CircleShape,
                ),
            )
        }
    }
}

/** Every badge, earned first in each category order; tapping one shows how to earn it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgeGallery(badges: List<BadgeStatus>) {
    val numbers = PlannerLocals.numbers
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            stringResource(R.string.badges_summary, numbers.format(badges.count { it.earned }), numbers.format(badges.size)),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.badges_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            maxItemsInEachRow = 3,
        ) {
            badges.forEach { status -> BadgeCell(status, Modifier.weight(1f)) { open = status.badge.key } }
        }
    }
    badges.firstOrNull { it.badge.key == open }?.let { status -> BadgeDialog(status) { open = null } }
}

@Composable
private fun BadgeCell(status: BadgeStatus, modifier: Modifier, onClick: () -> Unit) {
    val formatter = PlannerLocals.formatter
    val numbers = PlannerLocals.numbers
    val name = badgeName(status.badge)
    val detail = status.earnedAt?.let { formatter.mediumDate(it.atZone(java.time.ZoneId.systemDefault()).toLocalDate()) }
        ?: stringResource(R.string.badge_progress, numbers.format(status.progress.current.coerceAtMost(status.badge.threshold)), numbers.format(status.badge.threshold))
    val description = if (status.earned) stringResource(R.string.badge_cd_earned, name, detail) else stringResource(R.string.badge_cd_locked, name, detail)
    PlannerCard(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        onClick = onClick,
        contentPadding = PaddingValues(Spacing.sm),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BadgeMedal(status.badge, status.earned, 52.dp)
            Spacer(Modifier.height(Spacing.xs))
            Text(name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun BadgeDialog(status: BadgeStatus, onDismiss: () -> Unit) {
    val formatter = PlannerLocals.formatter
    val numbers = PlannerLocals.numbers
    PlannerDialog(
        title = badgeName(status.badge),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.badge_close),
        onConfirm = onDismiss,
        dismissLabel = "",
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BadgeMedal(status.badge, status.earned, 72.dp)
            Text(badgeDescription(status.badge), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            val earnedAt = status.earnedAt
            if (earnedAt != null) {
                Text(stringResource(R.string.badge_earned_on, formatter.mediumDate(earnedAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate())), style = MaterialTheme.typography.labelLarge)
            } else {
                val text = stringResource(R.string.badge_progress, numbers.format(status.progress.current.coerceAtMost(status.badge.threshold)), numbers.format(status.badge.threshold))
                Text(stringResource(R.string.badge_locked), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PlannerProgressBar(status.progress.fraction, Modifier.fillMaxWidth().semantics { contentDescription = text })
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
