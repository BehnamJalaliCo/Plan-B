package com.behnamjalali.planb.core.focus

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.behnamjalali.planb.core.data.wellbeing.WellbeingState
import com.behnamjalali.planb.core.model.FocusSession
import com.behnamjalali.planb.core.model.FocusStatus
import com.behnamjalali.planb.core.model.StrictMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The system's Do Not Disturb, behind an interface so the rules can be tested. */
interface DndController {
    /** Whether the user allowed Plan-B to change Do Not Disturb (Settings › Do Not Disturb access). */
    fun hasAccess(): Boolean

    /** The current interruption filter, or null when it cannot be read. */
    fun currentFilter(): Int?

    /** Sets the interruption filter; false when not allowed. */
    fun setFilter(filter: Int): Boolean
}

/** [DndController] over [NotificationManager]; needs `ACCESS_NOTIFICATION_POLICY` and the user's grant. */
@Singleton
class SystemDndController @Inject constructor(@ApplicationContext private val context: Context) : DndController {
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    override fun hasAccess(): Boolean = manager?.isNotificationPolicyAccessGranted == true

    override fun currentFilter(): Int? = manager?.currentInterruptionFilter?.takeIf { it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN }

    override fun setFilter(filter: Int): Boolean = runCatching {
        if (!hasAccess()) return false
        manager?.setInterruptionFilter(filter)
        true
    }.getOrDefault(false)

    companion object {
        /** The system screen where the user grants Do Not Disturb access to apps. */
        fun accessSettingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/**
 * Strict mode (Plan-B Pro #26): turns on Do Not Disturb ("priority only", so the user's own
 * exceptions such as alarms and starred contacts still ring) while a strict session runs, and
 * puts the previous filter back when it pauses, ends or is cancelled. The filter that was there
 * before is kept in [WellbeingState] (device-only), so it is restored even when the process
 * died in between; see [StrictMode.decide] for the rules.
 */
@Singleton
class StrictModeCoordinator @Inject constructor(
    private val dnd: DndController,
    private val state: WellbeingState,
) {
    private val mutex = Mutex()

    suspend fun apply(active: FocusSession?) = mutex.withLock {
        val wanted = active != null && active.status == FocusStatus.RUNNING && active.strict
        val hasAccess = dnd.hasAccess()
        when (val action = StrictMode.decide(wanted, state.strictMode(), if (hasAccess) dnd.currentFilter() else null, hasAccess)) {
            StrictMode.Action.None -> Unit
            is StrictMode.Action.Apply -> if (dnd.setFilter(action.filter)) state.setStrictMode(action.state)
            is StrictMode.Action.Restore -> {
                action.filter?.let(dnd::setFilter)
                state.setStrictMode(null)
            }
        }
    }
}
