package com.behnamjalali.planb

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.behnamjalali.planb.core.billing.EntitlementRepository
import com.behnamjalali.planb.core.data.ProStatusSource
import com.behnamjalali.planb.core.data.repository.ActivityRepository
import com.behnamjalali.planb.core.data.repository.TrashRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/** Plan-B Pro for the data layer (trash, activity history, automatic backups), from the entitlement. */
@Module
@InstallIn(SingletonComponent::class)
object ProStatusModule {
    @Provides
    fun provideProStatus(entitlements: EntitlementRepository): ProStatusSource = ProStatusSource { entitlements.isPro.first() }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface MaintenanceEntryPoint {
    fun trash(): TrashRepository
    fun activity(): ActivityRepository
}

/**
 * Daily housekeeping (Plan-B Pro #38): items older than 30 days leave the trash for good, and
 * the activity history is trimmed to its cap. It runs for everyone, so a trash filled while Pro
 * was active is still emptied on time.
 */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, MaintenanceEntryPoint::class.java)
        runCatching { entry.trash().purgeExpired() }
        runCatching { entry.activity().prune() }
        return Result.success()
    }

    companion object {
        private const val NAME = "planb_maintenance"

        /** Best-effort: WorkManager may be unavailable (tests without its initializer). */
        fun schedule(context: Context) {
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS).build(),
                )
            }
        }
    }
}
