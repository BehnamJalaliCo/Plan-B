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
import com.behnamjalali.planb.core.data.repository.JournalRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The optional daily journal reminder (Plan-B Pro #25), built like the ritual reminders: one
 * inexact daily alarm at the chosen time, skipped on a day that already has a journal page,
 * re-armed after each delivery, after boot or a clock change and whenever the settings change.
 * Its request code and notification id is [REQUEST_CODE] (slot 0 of the `id × 8 + code`
 * scheme, id 3), which no item reminder, ritual or focus alarm uses.
 */
@Singleton
class JournalReminders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifier: Notifier,
    private val settings: SettingsRepository,
    private val journal: JournalRepository,
    private val time: TimeProvider,
) {
    private val alarmManager: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)

    suspend fun sync() {
        val s = settings.current().journal
        if (s.reminder) schedule(RitualReminders.nextAt(s.reminderTime, time.now(), time.zone()).toEpochMilli()) else cancel()
    }

    suspend fun deliver() {
        val s = settings.current()
        if (s.journal.reminder && journal.entryOn(time.today()) == null) {
            val localized = notifier.localizedContext(s.language.tag)
            notifier.showRitual(
                REQUEST_CODE,
                localized.getString(R.string.notif_journal_title),
                localized.getString(R.string.notif_journal_text),
                DeepLinks.journal(),
            )
        }
        sync()
    }

    private fun schedule(at: Long) {
        alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent())
    }

    private fun cancel() {
        alarmManager?.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, JournalReminderReceiver::class.java)
            .setAction(JournalReminderReceiver.ACTION_JOURNAL)
            .setData("planb-alarm://journal".toUri()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val REQUEST_CODE = 24
    }
}

/** Fires the journal reminder; not exported, reached only through our own immutable PendingIntent. */
@AndroidEntryPoint
class JournalReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var reminders: JournalReminders
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_JOURNAL) return
        val pending = goAsync()
        scope.launch {
            try {
                reminders.deliver()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not deliver the journal reminder (${e.javaClass.simpleName})")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_JOURNAL = "com.behnamjalali.planb.action.JOURNAL"
        private const val TAG = "PlanBJournal"
    }
}
