package com.behnamjalali.planb.core.calendarsync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.datastore.UserPreferencesDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Keeps device calendar sync running while it is on: a sync at start, after changes on either
 * side (the calendar provider's change notifications and Room's invalidation of
 * `calendar_events`, debounced), and periodically through WorkManager ([CalendarSyncWorker])
 * for changes made while Plan-B is not running.
 */
@Singleton
class CalendarSyncController @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val preferences: UserPreferencesDataSource,
    private val engine: CalendarSyncEngine,
    private val store: DeviceCalendarStore,
    private val db: PlanBDatabase,
    private val time: TimeProvider,
) {
    @Volatile private var remoteNoticedAt: Instant? = null

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            preferences.calendarSync.map { it.enabled }.distinctUntilChanged().collectLatest { enabled ->
                CalendarSyncWorker.apply(context, enabled)
                if (!enabled) return@collectLatest
                runCatchingSafely { engine.sync() }
                val remote = store.changes().onEach { if (remoteNoticedAt == null) remoteNoticedAt = time.now() }
                val local = db.invalidationTracker.createFlow("calendar_events", emitInitialState = false).map { }
                merge(remote, local).debounce(DEBOUNCE_MS).collect {
                    val noticed = remoteNoticedAt
                    remoteNoticedAt = null
                    runCatchingSafely { engine.sync(noticed) }
                }
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 2_000L
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface CalendarSyncEntryPoint {
    fun engine(): CalendarSyncEngine
}

/** The periodic sync for changes made on the device while Plan-B was not running. */
class CalendarSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val engine = EntryPointAccessors.fromApplication(applicationContext, CalendarSyncEntryPoint::class.java).engine()
        runCatchingSafely { engine.sync() }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "planb_calendar_sync"
        private const val HOURS = 1L

        /** Best-effort: WorkManager may be unavailable (tests without its initializer). */
        fun apply(context: Context, enabled: Boolean) {
            runCatching {
                val work = WorkManager.getInstance(context)
                if (enabled) {
                    work.enqueueUniquePeriodicWork(
                        UNIQUE_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        PeriodicWorkRequestBuilder<CalendarSyncWorker>(HOURS, TimeUnit.HOURS).build(),
                    )
                } else {
                    work.cancelUniqueWork(UNIQUE_NAME)
                }
            }
        }
    }
}

/** The device calendar provider; app tests replace this module with an in-memory fake. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceCalendarStoreModule {
    @Binds abstract fun store(impl: ContentResolverCalendarStore): DeviceCalendarStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarSyncModule {
    @Binds abstract fun source(impl: CalendarSyncRepository): DeviceCalendarSource

    companion object {
        @Provides
        @Named(CalendarSyncEngine.PACKAGE_NAME)
        fun packageName(@ApplicationContext context: Context): String = context.packageName
    }
}
