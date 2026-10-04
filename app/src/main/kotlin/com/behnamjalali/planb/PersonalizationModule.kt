package com.behnamjalali.planb

import com.behnamjalali.planb.core.billing.EntitlementRepository
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.data.platform.AppIconSwitcher
import com.behnamjalali.planb.core.data.platform.DataChangeWatcher
import com.behnamjalali.planb.core.data.platform.WidgetUpdater
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.launcher.IconSwitchHook
import com.behnamjalali.planb.launcher.LauncherIconSwitcher
import com.behnamjalali.planb.quick.ShortcutPublisher
import com.behnamjalali.planb.wear.WearSync
import com.behnamjalali.planb.widget.GlanceWidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Platform hooks of Plan-B Pro personalization (widgets, app icons), bound to app implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PersonalizationModule {
    @Binds abstract fun widgetUpdater(impl: GlanceWidgetUpdater): WidgetUpdater
    @Binds abstract fun appIconSwitcher(impl: LauncherIconSwitcher): AppIconSwitcher

    companion object {
        @Provides
        fun iconSwitchHook(
            shortcuts: ShortcutPublisher,
            entitlements: EntitlementRepository,
            @ApplicationScope scope: CoroutineScope,
        ): IconSwitchHook = IconSwitchHook { scope.launch { shortcuts.publish(entitlements.current().isPro) } }
    }
}

/**
 * Keeps what lives outside the app's window in step with the data: widgets after data,
 * language or Pro changes, and the Pro launcher shortcuts after Pro changes. Everything runs on
 * the application scope and only reacts to changes (no polling).
 */
@Singleton
class PersonalizationSync @Inject constructor(
    private val changes: DataChangeWatcher,
    private val widgets: WidgetUpdater,
    private val shortcuts: ShortcutPublisher,
    private val entitlements: EntitlementRepository,
    private val settings: SettingsRepository,
    private val wear: WearSync,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            changes.changes.collect {
                widgets.requestUpdate()
                wear.publish()
            }
        }
        scope.launch {
            entitlements.isPro.collect { pro ->
                shortcuts.publish(pro)
                widgets.requestUpdate()
                wear.publish()
            }
        }
        scope.launch {
            settings.settings.map { listOf(it.language, it.calendarSystem, it.firstDayOfWeek, it.usePersianDigits) }
                .distinctUntilChanged().drop(1).collect {
                    widgets.requestUpdate()
                    wear.publish()
                }
        }
    }
}
