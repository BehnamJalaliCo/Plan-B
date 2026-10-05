package com.behnamjalali.planb

import android.util.Log
import com.behnamjalali.planb.core.billing.EntitlementRepository
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.platform.DataChangeWatcher
import com.behnamjalali.planb.core.data.repository.AchievementRepository
import com.behnamjalali.planb.core.data.wellbeing.HealthHabitSync
import com.behnamjalali.planb.core.focus.FocusProControls
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Plan-B Pro habits and focus in the background of the app (#26, #27, #29):
 * - at start, strict mode's Do Not Disturb and the ambient sound catch up with the session in the
 *   database (a session that ended while the process was gone gets the user's filter back);
 * - while the user has Pro, badges and challenge statuses follow every change of their data;
 * - when the app comes to the foreground, habits linked to Health Connect are checked off
 *   (Health Connect is only read while Plan-B is in use).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WellbeingSync @Inject constructor(
    private val changes: DataChangeWatcher,
    private val achievements: AchievementRepository,
    private val entitlements: EntitlementRepository,
    private val focus: FocusProControls,
    private val health: HealthHabitSync,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch { runCatchingSafely { focus.refresh() }.onFailure { Log.w(TAG, "Focus effects not restored (${it.javaClass.simpleName})") } }
        scope.launch {
            entitlements.isPro.distinctUntilChanged()
                .flatMapLatest { pro -> if (pro) merge(flowOf(Unit), changes.achievementChanges) else emptyFlow() }
                .collect { runCatchingSafely { achievements.evaluate() }.onFailure { Log.w(TAG, "Badges not evaluated (${it.javaClass.simpleName})") } }
        }
    }

    /** The app came to the foreground (throttled inside the sync). */
    fun onForeground() {
        scope.launch { runCatchingSafely { health.sync() } }
    }

    private companion object {
        const val TAG = "PlanB"
    }
}
