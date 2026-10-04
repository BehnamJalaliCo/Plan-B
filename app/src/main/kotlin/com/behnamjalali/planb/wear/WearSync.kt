package com.behnamjalali.planb.wear

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.behnamjalali.planb.R
import com.behnamjalali.planb.core.billing.EntitlementRepository
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.HabitStats
import com.behnamjalali.planb.core.model.TaskView
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * The Wear OS companion's protocol (Plan-B Pro #35). The phone publishes one data item with
 * today's open tasks and habits, already localized; the watch sends messages to complete a
 * task, check in a habit or ask for a refresh. Shared with the `wear` module by value.
 */
object WearProtocol {
    const val PATH_TODAY = "/planb/today"
    const val PATH_COMPLETE_TASK = "/planb/complete-task"
    const val PATH_CHECK_HABIT = "/planb/check-habit"
    const val PATH_REFRESH = "/planb/refresh"

    const val KEY_PRO = "pro"
    const val KEY_RTL = "rtl"
    const val KEY_UPDATED = "updated"
    const val KEY_TASKS = "tasks"
    const val KEY_HABITS = "habits"
    const val KEY_ID = "id"
    const val KEY_TITLE = "title"
    const val KEY_DONE = "done"
    const val KEY_LABEL_TASKS = "label_tasks"
    const val KEY_LABEL_HABITS = "label_habits"
    const val KEY_LABEL_EMPTY = "label_empty"
    const val KEY_LABEL_LOCKED = "label_locked"
}

/**
 * Publishes today's tasks and habits to a paired watch. Every Wearable API call is guarded:
 * on devices without Google Play services (common in Iran) nothing is attempted and the app
 * works exactly as before; failures are swallowed (the watch keeps its last copy).
 */
@Singleton
class WearSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tasks: TaskRepository,
    private val habits: HabitRepository,
    private val settings: SettingsRepository,
    private val entitlements: EntitlementRepository,
    private val time: TimeProvider,
) {
    private val lock = Mutex()

    /** True only when Google Play services are installed and usable. */
    val available: Boolean by lazy {
        runCatching { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS }.getOrDefault(false)
    }

    suspend fun publish() {
        if (!available) return
        lock.withLock {
            runCatching {
                // Nothing to do without a paired watch.
                if (Wearable.getNodeClient(context).connectedNodes.await().isEmpty()) return@runCatching
                val request = PutDataMapRequest.create(WearProtocol.PATH_TODAY)
                fill(request.dataMap)
                Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent()).await()
            }.onFailure { Log.w(TAG, "Wear sync skipped (${it.javaClass.simpleName})") }
        }
    }

    private suspend fun fill(map: DataMap) {
        val s = settings.current()
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(s.language.tag)) }
        val res = context.createConfigurationContext(config).resources
        val pro = entitlements.current().isPro
        map.putBoolean(WearProtocol.KEY_PRO, pro)
        map.putBoolean(WearProtocol.KEY_RTL, res.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL)
        map.putLong(WearProtocol.KEY_UPDATED, time.now().toEpochMilli())
        map.putString(WearProtocol.KEY_LABEL_TASKS, res.getString(R.string.wear_tasks))
        map.putString(WearProtocol.KEY_LABEL_HABITS, res.getString(R.string.wear_habits))
        map.putString(WearProtocol.KEY_LABEL_EMPTY, res.getString(R.string.wear_empty))
        map.putString(WearProtocol.KEY_LABEL_LOCKED, res.getString(R.string.wear_locked))
        if (!pro) {
            map.putDataMapArrayList(WearProtocol.KEY_TASKS, arrayListOf())
            map.putDataMapArrayList(WearProtocol.KEY_HABITS, arrayListOf())
            return
        }
        val today = time.today()
        val open = tasks.observeTasks(TaskFilter(view = TaskView.TODAY, today = today, limit = MAX_ITEMS)).first()
        map.putDataMapArrayList(
            WearProtocol.KEY_TASKS,
            ArrayList(open.map { DataMap().apply { putLong(WearProtocol.KEY_ID, it.id); putString(WearProtocol.KEY_TITLE, it.title) } }),
        )
        val todayHabits = habits.observeHabits(today, today).first().filter { HabitStats.isScheduled(it.habit, today) }
        map.putDataMapArrayList(
            WearProtocol.KEY_HABITS,
            ArrayList(
                todayHabits.take(MAX_ITEMS).map { h ->
                    DataMap().apply {
                        putLong(WearProtocol.KEY_ID, h.habit.id)
                        putString(WearProtocol.KEY_TITLE, h.habit.title)
                        putBoolean(WearProtocol.KEY_DONE, HabitStats.isDone(h.habit, h.amounts, today))
                    }
                },
            ),
        )
    }

    /** Handles a message from the watch (only from Plan-B's own watch app, same package and key). */
    suspend fun handle(path: String, payload: ByteArray) {
        if (!entitlements.current().isPro) return publish()
        val id = payload.toString(Charsets.UTF_8).toLongOrNull()
        runCatching {
            when (path) {
                WearProtocol.PATH_COMPLETE_TASK -> if (id != null) tasks.setCompleted(id, true)
                WearProtocol.PATH_CHECK_HABIT -> if (id != null) {
                    val habit = habits.getHabit(id) ?: return@runCatching
                    val today = time.today()
                    val amount = habits.amountOn(id, today)
                    if (HabitStats.isDone(habit, mapOf(today to amount), today)) habits.checkIn(id, today, -amount) else habits.checkIn(id, today, 1)
                }
            }
        }
        publish()
    }

    private companion object {
        const val TAG = "PlanB"
        const val MAX_ITEMS = 20
    }
}
