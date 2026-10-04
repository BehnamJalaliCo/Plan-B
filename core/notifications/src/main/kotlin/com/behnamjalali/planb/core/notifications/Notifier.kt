package com.behnamjalali.planb.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Deep links understood by the app's navigation. */
object DeepLinks {
    const val SCHEME = "planb"
    fun task(id: Long): Uri = "$SCHEME://open/task/$id".toUri()
    fun event(id: Long): Uri = "$SCHEME://open/event/$id".toUri()
    fun habit(id: Long): Uri = "$SCHEME://open/habit/$id".toUri()
    fun focus(): Uri = "$SCHEME://open/focus".toUri()
}

@Singleton
class Notifier @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /**
     * A context whose resources use the app language [languageTag]. Needed outside activities
     * (application start, receivers): before API 33 the application context does not carry
     * the per-app language.
     */
    // Both languages are always installed: the app bundle disables language splits (app/build.gradle.kts).
    @SuppressLint("AppBundleLocaleChanges")
    fun localizedContext(languageTag: String): Context =
        context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(languageTag)) },
        )

    /**
     * Creates the channels, or renames existing ones, using [localized] resources. Call it
     * again whenever the app language changes so the system settings show current names.
     */
    fun createChannels(localized: Context = context) {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, localized.getString(R.string.notif_channel_reminders), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = localized.getString(R.string.notif_channel_reminders_desc)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_FOCUS, localized.getString(R.string.notif_channel_focus), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = localized.getString(R.string.notif_channel_focus_desc) },
        )
    }

    private fun openIntent(uri: Uri, requestCode: Int): PendingIntent {
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
            .setAction(Intent.ACTION_VIEW)
            .setData(uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setPackage(context.packageName)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * Shows a reminder. Lock screens get a generic public version so private
     * planning details are not exposed (NotificationCompat.VISIBILITY_PRIVATE).
     */
    fun showReminder(kind: ReminderKind, id: Long, title: String, text: String, uri: Uri, localized: Context = context) {
        if (!manager.areNotificationsEnabled()) return
        val notificationId = notificationId(kind, id)
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_planb)
            .setContentTitle(localized.getString(R.string.app_label_fallback))
            .setContentText(localized.getString(R.string.notif_public_text))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_planb)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(if (kind == ReminderKind.EVENT) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(openIntent(uri, notificationId))
            .build()
        runCatching { manager.notify(notificationId, notification) }
    }

    fun showFocusComplete(localized: Context = context) {
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_stat_planb)
            .setContentTitle(localized.getString(R.string.notif_focus_title))
            .setContentText(localized.getString(R.string.notif_focus_text))
            .setAutoCancel(true)
            .setContentIntent(openIntent(DeepLinks.focus(), FOCUS_NOTIFICATION_ID))
            .build()
        runCatching { manager.notify(FOCUS_NOTIFICATION_ID, notification) }
    }

    fun cancel(kind: ReminderKind, id: Long) = manager.cancel(notificationId(kind, id))

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_FOCUS = "focus"
        private const val FOCUS_NOTIFICATION_ID = 4

        /** Stable per item; ids are unique within a kind so this cannot collide across kinds. */
        fun notificationId(kind: ReminderKind, id: Long): Int = ((id * 8 + kind.code) % Int.MAX_VALUE).toInt()
    }
}
