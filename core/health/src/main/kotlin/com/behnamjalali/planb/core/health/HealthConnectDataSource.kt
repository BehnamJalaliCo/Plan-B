package com.behnamjalali.planb.core.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.behnamjalali.planb.core.data.wellbeing.HealthAvailability
import com.behnamjalali.planb.core.data.wellbeing.HealthDataSource
import com.behnamjalali.planb.core.model.HealthMetric
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * [HealthDataSource] over the Health Connect client (Plan-B Pro #27). Reads only aggregated
 * daily totals (Health Connect removes duplicates across apps), with the read permission of
 * the one metric a habit uses. Every failure (Health Connect missing or removed, a permission
 * taken back, rate limits) reads as "no data".
 */
@Singleton
class HealthConnectDataSource @Inject constructor(@ApplicationContext private val context: Context) : HealthDataSource {
    private val client: HealthConnectClient? by lazy {
        runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }

    override fun availability(): HealthAvailability = when (runCatching { HealthConnectClient.getSdkStatus(context) }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_INSTALL
        else -> HealthAvailability.UNSUPPORTED
    }

    override fun permissionsFor(metric: HealthMetric): Set<String> = HealthConnectPermissions.forMetric(metric)

    override suspend fun grantedPermissions(): Set<String> {
        if (availability() != HealthAvailability.AVAILABLE) return emptySet()
        return safely { client?.permissionController?.getGrantedPermissions() } ?: emptySet()
    }

    override suspend fun total(metric: HealthMetric, from: Instant, to: Instant): Long? {
        val health = client ?: return null
        val range = TimeRangeFilter.between(from, to)
        return safely {
            when (metric) {
                HealthMetric.STEPS -> health.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range))[StepsRecord.COUNT_TOTAL]
                HealthMetric.SLEEP_MINUTES -> health.aggregate(AggregateRequest(setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL), range))[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()
                HealthMetric.HYDRATION_ML -> health.aggregate(AggregateRequest(setOf(HydrationRecord.VOLUME_TOTAL), range))[HydrationRecord.VOLUME_TOTAL]?.inMilliliters?.toLong()
                HealthMetric.ACTIVE_MINUTES -> health.aggregate(AggregateRequest(setOf(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL), range))[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes()
                HealthMetric.DISTANCE_METERS -> health.aggregate(AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), range))[DistanceRecord.DISTANCE_TOTAL]?.inMeters?.toLong()
            }
        }
    }

    private suspend fun <T> safely(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Health Connect read failed (${e.javaClass.simpleName})")
        null
    }

    private companion object {
        const val TAG = "PlanB"
    }
}

/** Health Connect permissions and screens for the habit editor (Plan-B Pro #27). */
object HealthConnectPermissions {
    /** Health Connect's package on Android 9–13 (also listed in the manifest's queries). */
    private const val PROVIDER = "com.google.android.apps.healthdata"

    /** The read permission of [metric]: one per metric, asked for only when a habit uses it. */
    fun forMetric(metric: HealthMetric): Set<String> = setOf(
        when (metric) {
            HealthMetric.STEPS -> HealthPermission.getReadPermission(StepsRecord::class)
            HealthMetric.SLEEP_MINUTES -> HealthPermission.getReadPermission(SleepSessionRecord::class)
            HealthMetric.HYDRATION_ML -> HealthPermission.getReadPermission(HydrationRecord::class)
            HealthMetric.ACTIVE_MINUTES -> HealthPermission.getReadPermission(ExerciseSessionRecord::class)
            HealthMetric.DISTANCE_METERS -> HealthPermission.getReadPermission(DistanceRecord::class)
        },
    )

    /** Health Connect's own permission screen: input the permissions, output the granted ones. */
    fun requestContract(): ActivityResultContract<Set<String>, Set<String>> = PermissionController.createRequestPermissionResultContract()

    /** Opens Health Connect (its data and app permissions); false when there is none. */
    fun openHealthConnect(context: Context): Boolean = start(context, runCatching { HealthConnectClient.getHealthConnectManageDataIntent(context) }.getOrNull())

    /** Opens the app store page to install or update Health Connect; false when no store can show it. */
    fun openInstall(context: Context): Boolean = start(
        context,
        Intent(Intent.ACTION_VIEW, "market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding".toUri())
            .putExtra("overlay", true)
            .putExtra("callerId", context.packageName),
    )

    private fun start(context: Context, intent: Intent?): Boolean {
        if (intent == null) return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthModule {
    @Binds abstract fun source(impl: HealthConnectDataSource): HealthDataSource
}
