package com.behnamjalali.planb

import android.app.Application
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.notifications.Notifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class PlanBApplication : Application() {
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var reminders: ReminderScheduler
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Persian is the default language on a fresh install. Once the user (or the
        // system per-app language setting) picks a language, that choice is kept.
        AppLocales.applyDefaultIfUnset()
        notifier.createChannels()
        // Alarms can be lost (force-stop, restore); re-sync them off the main thread.
        appScope.launch { runCatching { reminders.rescheduleAll() } }
    }
}
