package com.behnamjalali.planb

import android.app.Application
import android.util.Log
import com.behnamjalali.planb.core.billing.EntitlementRepository
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class PlanBApplication : Application() {
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var reminders: ReminderScheduler
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var searchIndex: SearchIndexUpgrade
    @Inject lateinit var attachments: AttachmentMaintenance
    @Inject lateinit var entitlements: EntitlementRepository
    @Inject lateinit var trash: com.behnamjalali.planb.core.data.repository.TrashRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var personalization: PersonalizationSync
    @Inject lateinit var rituals: com.behnamjalali.planb.core.notifications.RitualReminders

    override fun onCreate() {
        super.onCreate()
        // The stored preferences decide the language and theme of the very first window, so
        // they are read before any activity starts (a small local file; corrupt data reads
        // as defaults). A fresh install gets the defaults: Persian, system theme.
        val stored = runBlocking { runCatching { settings.current() }.getOrDefault(UserSettings()) }
        AppLocales.applyStartupLanguage(this, stored.language)
        AppTheme.applyStartupNightMode(stored.themeMode)
        notifier.createChannels(notifier.localizedContext(stored.language.tag))
        // Widgets, launcher shortcuts and the watch follow data and Plan-B Pro changes.
        personalization.start()
        appScope.launch {
            // Alarms can be lost (force-stop, restore); re-sync them off the main thread.
            runCatching { reminders.rescheduleAll() }
            // Existing installs rebuild the search index once after a normalizer change.
            runCatching { searchIndex.rebuildIfOutdated() }.onFailure { Log.w(TAG, "Search index rebuild failed (${it.javaClass.simpleName})") }
            // Re-verify Plan-B Pro with Cafe Bazaar; offline, the cached entitlement applies.
            runCatching { entitlements.refresh() }
            // Attachment rows of deleted owners and files without a row are removed.
            runCatching { attachments.sweep() }.onFailure { Log.w(TAG, "Attachment sweep failed (${it.javaClass.simpleName})") }
            // Trash items older than 30 days go for good (also done daily in the background).
            runCatching { trash.purgeExpired() }
        }
        MaintenanceWorker.schedule(this)
        appScope.launch {
            // Ritual reminders (Plan-B Pro #8) follow their settings, also after a restore.
            settings.settings
                .map { listOf(it.dayPlan.morningReminder, it.dayPlan.morningTime, it.dayPlan.eveningReminder, it.dayPlan.eveningTime) }
                .distinctUntilChanged()
                .collect { runCatching { rituals.sync() } }
        }
    }

    private companion object {
        const val TAG = "PlanB"
    }
}
