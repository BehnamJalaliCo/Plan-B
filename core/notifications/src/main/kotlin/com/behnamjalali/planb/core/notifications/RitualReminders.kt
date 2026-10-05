package com.behnamjalali.planb.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.model.UserSettings
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The two daily rituals (Plan-B Pro #8). [requestCode] is both the alarm's request code and
 * the notification id: slot 0 of the `id × 8 + code` scheme (ids 1 and 2), which no item
 * reminder, focus alarm or reminder button uses.
 */
enum class Ritual(val requestCode: Int, val path: String) {
    MORNING(8, "morning"),
    EVENING(16, "evening"),
    ;

    companion object {
        fun fromPath(path: String?): Ritual? = entries.firstOrNull { it.path == path }
    }
}

/**
 * Optional daily reminders for the morning and evening rituals, at the times chosen in the
 * working-hours settings. One repeating moment each, re-armed after every delivery, after
 * boot or a clock change, and whenever the settings change. A reminder is skipped on a day
 * the ritual was already done, and posts nothing while notifications are off.
 */
@Singleton
class RitualReminders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifier: Notifier,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) {
    private val alarmManager: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)

    /** Arms or cancels both reminders from the stored settings. */
    suspend fun sync() {
        val s = settings.current()
        Ritual.entries.forEach { ritual -> sync(ritual, s) }
    }

    private fun sync(ritual: Ritual, s: UserSettings) {
        val (enabled, at) = config(ritual, s)
        if (enabled) schedule(ritual, nextAt(at, time.now(), time.zone())) else cancel(ritual)
    }

    /** Called when the alarm fires: posts the reminder unless the ritual is done today, then re-arms. */
    suspend fun deliver(ritual: Ritual) {
        val s = settings.current()
        val (enabled, _) = config(ritual, s)
        if (enabled) {
            val today = time.today()
            val done = when (ritual) {
                Ritual.MORNING -> s.rituals.morningDoneOn == today
                Ritual.EVENING -> s.rituals.eveningDoneOn == today
            }
            if (!done) {
                val localized = notifier.localizedContext(s.language.tag)
                val (title, text) = when (ritual) {
                    Ritual.MORNING -> R.string.notif_ritual_morning_title to R.string.notif_ritual_morning_text
                    Ritual.EVENING -> R.string.notif_ritual_evening_title to R.string.notif_ritual_evening_text
                }
                notifier.showRitual(ritual.requestCode, localized.getString(title), localized.getString(text), DeepLinks.ritual(ritual.path))
            }
        }
        sync(ritual, s)
    }

    private fun config(ritual: Ritual, s: UserSettings): Pair<Boolean, LocalTime> = when (ritual) {
        Ritual.MORNING -> s.dayPlan.morningReminder to s.dayPlan.morningTime
        Ritual.EVENING -> s.dayPlan.eveningReminder to s.dayPlan.eveningTime
    }

    private fun schedule(ritual: Ritual, at: Instant) {
        val manager = alarmManager ?: return
        // A gentle nudge, not a deadline: an inexact alarm is enough and needs no permission.
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pendingIntent(ritual))
    }

    private fun cancel(ritual: Ritual) {
        alarmManager?.cancel(pendingIntent(ritual))
    }

    private fun pendingIntent(ritual: Ritual): PendingIntent {
        val intent = Intent(context, RitualReminderReceiver::class.java)
            .setAction(RitualReminderReceiver.ACTION_RITUAL)
            .setData("planb-alarm://ritual/${ritual.path}".toUri())
            .putExtra(RitualReminderReceiver.EXTRA_RITUAL, ritual.path)
        return PendingIntent.getBroadcast(
            context,
            ritual.requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        /**
         * The next moment the wall clock in [zone] shows [at], strictly after [now]. A time in a
         * skipped daylight-saving hour moves forward (java.time rules).
         */
        fun nextAt(at: LocalTime, now: Instant, zone: ZoneId): Instant {
            val today = now.atZone(zone).toLocalDate()
            val candidate = ZonedDateTime.of(today, at, zone).toInstant()
            return if (candidate.isAfter(now)) candidate else ZonedDateTime.of(today.plusDays(1), at, zone).toInstant()
        }
    }
}

/** Fires a ritual reminder; not exported, reached only through our own immutable PendingIntents. */
@AndroidEntryPoint
class RitualReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var rituals: RitualReminders
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RITUAL) return
        val ritual = Ritual.fromPath(intent.getStringExtra(EXTRA_RITUAL)) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                rituals.deliver(ritual)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not deliver a ritual reminder (${e.javaClass.simpleName})")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_RITUAL = "com.behnamjalali.planb.action.RITUAL"
        const val EXTRA_RITUAL = "ritual"
        private const val TAG = "PlanBRituals"
    }
}
