package com.behnamjalali.planb.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.platform.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Re-renders every Plan-B widget that is on a home screen. Nothing happens (no work, no
 * alarm) when the user has not added any widget. While widgets exist, an inexact alarm at the
 * next midnight refreshes them for the new day (no exact-alarm permission needed).
 */
@Singleton
class GlanceWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val time: TimeProvider,
) : WidgetUpdater {
    override fun requestUpdate() {
        scope.launch { updateNow() }
    }

    suspend fun updateNow() {
        val manager = GlanceAppWidgetManager(context)
        var any = false
        for (widget in WIDGETS) {
            val placed = runCatching { manager.getGlanceIds(widget.javaClass).isNotEmpty() }.getOrDefault(false)
            if (placed) {
                any = true
                runCatching { widget.updateAll(context) }
            }
        }
        if (any) scheduleMidnight() else cancelMidnight()
    }

    private fun refreshIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_MIDNIGHT,
        Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleMidnight() {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val next = time.today().plusDays(1).atStartOfDay(time.zone()).toInstant().toEpochMilli() + MIDNIGHT_SLACK_MS
        runCatching { alarms.set(AlarmManager.RTC, next, refreshIntent()) }
    }

    private fun cancelMidnight() {
        runCatching { context.getSystemService(AlarmManager::class.java)?.cancel(refreshIntent()) }
    }

    companion object {
        val WIDGETS: List<GlanceAppWidget> = listOf(TodayWidget(), QuickAddWidget(), HabitsWidget(), FocusWidget(), CalendarWidget())
        const val ACTION_REFRESH = "com.behnamjalali.planb.widget.REFRESH"
        private const val REQUEST_MIDNIGHT = 7_001
        private const val MIDNIGHT_SLACK_MS = 60_000L
    }
}

/** Refreshes widgets at midnight and when the clock, date or time zone changes. */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val entry = context.widgetEntryPoint()
        // Runs on the application scope; the broadcast is finished when the update is done.
        entry.appScope().launch {
            try {
                entry.updater().updateNow()
            } finally {
                pending.finish()
            }
        }
    }
}
