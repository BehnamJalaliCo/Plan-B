package com.behnamjalali.planb.quick

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.behnamjalali.planb.MainActivity
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.data.repository.FocusRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.model.FocusStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-app links used by widgets, quick-settings tiles, launcher shortcuts and the watch. They
 * only ever open screens inside Plan-B (see `handleDeepLink`).
 */
object QuickLinks {
    val CAPTURE: Uri = "planb://open/capture".toUri()
    val NEW_TASK: Uri = "planb://open/new-task".toUri()
    val NEW_NOTE: Uri = "planb://open/new-note".toUri()
    val TODAY: Uri = "planb://open/today".toUri()
    val FOCUS: Uri = "planb://open/focus".toUri()
    val START_FOCUS: Uri = "planb://open/focus/start".toUri()
    val HABITS: Uri = "planb://open/habits".toUri()
    val CALENDAR: Uri = "planb://open/calendar".toUri()

    /** The Pro screen focused on a feature (opened only from the user's own tap). */
    fun pro(featureId: String): Uri = "planb://open/pro/$featureId".toUri()

    fun intent(context: Context, uri: Uri): Intent = Intent(Intent.ACTION_VIEW, uri)
        .setClass(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

/**
 * Starts, pauses or resumes the focus timer from outside the Focus screen (widget, tile,
 * watch), keeping the end-of-session alarm in sync exactly as the Focus screen does.
 */
@Singleton
class FocusControls @Inject constructor(
    private val focus: FocusRepository,
    private val reminders: ReminderScheduler,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) {
    private val lock = Mutex()

    suspend fun toggle() = lock.withLock {
        val active = focus.getActive()
        when (active?.status) {
            FocusStatus.RUNNING -> {
                focus.pause()
                reminders.cancelFocusEnd()
            }
            FocusStatus.PAUSED -> focus.resume()?.let { reminders.scheduleFocusEnd(time.now().plusMillis(it.remainingMillis(time.now()))) }
            else -> start()
        }
    }

    /** Starts a session of the user's default length unless one is running or paused. */
    suspend fun startIfIdle() = lock.withLock { if (focus.getActive() == null) start() }

    private suspend fun start() {
        val minutes = settings.current().focusMinutes
        val session = focus.start(minutes * 60_000L, null)
        reminders.scheduleFocusEnd(session.startedAt.plusMillis(session.plannedDurationMillis))
    }
}
