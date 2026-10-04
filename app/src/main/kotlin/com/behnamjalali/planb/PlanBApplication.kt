package com.behnamjalali.planb

import android.app.Application
import android.util.Log
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.data.AttachmentMaintenance
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.data.SearchIndexUpgrade
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.model.UserSettings
import com.behnamjalali.planb.core.notifications.Notifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class PlanBApplication : Application() {
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var reminders: ReminderScheduler
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var searchIndex: SearchIndexUpgrade
    @Inject lateinit var attachments: AttachmentMaintenance
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // The stored preferences decide the language and theme of the very first window, so
        // they are read before any activity starts (a small local file; corrupt data reads
        // as defaults). A fresh install gets the defaults: Persian, system theme.
        val stored = runBlocking { runCatching { settings.current() }.getOrDefault(UserSettings()) }
        AppLocales.applyStartupLanguage(this, stored.language)
        AppTheme.applyStartupNightMode(stored.themeMode)
        notifier.createChannels(notifier.localizedContext(stored.language.tag))
        appScope.launch {
            // Alarms can be lost (force-stop, restore); re-sync them off the main thread.
            runCatching { reminders.rescheduleAll() }
            // Existing installs rebuild the search index once after a normalizer change.
            runCatching { searchIndex.rebuildIfOutdated() }.onFailure { Log.w(TAG, "Search index rebuild failed (${it.javaClass.simpleName})") }
            // Attachment rows of deleted owners and files without a row are removed.
            runCatching { attachments.sweep() }.onFailure { Log.w(TAG, "Attachment sweep failed (${it.javaClass.simpleName})") }
        }
    }

    private companion object {
        const val TAG = "PlanB"
    }
}
