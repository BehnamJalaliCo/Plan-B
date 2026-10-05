package com.behnamjalali.planb.core.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.datastore.createPreferencesDataStore
import com.behnamjalali.planb.core.model.EntityId
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NagStore

/**
 * Snoozes and dismissals of nagging reminders (Plan-B Pro #12), per task. Transient device
 * state in its own file, never part of a backup; entries older than two days are dropped.
 */
@Singleton
class NagStateStore @Inject constructor(@NagStore private val store: DataStore<Preferences>) {
    private val data get() = store.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    suspend fun state(taskId: EntityId): NagState {
        val prefs = data.first()
        return NagState(
            stoppedAt = prefs[stopKey(taskId)]?.let(Instant::ofEpochMilli),
            snoozedUntil = prefs[snoozeKey(taskId)]?.let(Instant::ofEpochMilli),
        )
    }

    /** "Snooze": earlier reminders stop nagging and one more comes at [until]. */
    suspend fun snooze(taskId: EntityId, now: Instant, until: Instant) = edit(now) {
        it[stopKey(taskId)] = now.toEpochMilli()
        it[snoozeKey(taskId)] = until.toEpochMilli()
    }

    /** The notification was swiped away: reminders that already fired stop nagging. */
    suspend fun stop(taskId: EntityId, now: Instant) = edit(now) { it[stopKey(taskId)] = now.toEpochMilli() }

    suspend fun clear(taskId: EntityId) {
        runCatchingSafely {
            store.edit {
                it.remove(stopKey(taskId))
                it.remove(snoozeKey(taskId))
            }
        }
    }

    /** Storage errors only lose a snooze or a dismissal; reminders keep working. */
    private suspend fun edit(now: Instant, block: (MutablePreferences) -> Unit) {
        runCatchingSafely {
            store.edit { prefs ->
                val oldest = now.minus(RETENTION).toEpochMilli()
                prefs.asMap().filterValues { (it as? Long)?.let { at -> at < oldest } == true }.keys.forEach { key ->
                    @Suppress("UNCHECKED_CAST")
                    prefs.remove(key as Preferences.Key<Any>)
                }
                block(prefs)
            }
        }
    }

    private fun stopKey(taskId: EntityId) = longPreferencesKey("stop_$taskId")
    private fun snoozeKey(taskId: EntityId) = longPreferencesKey("snooze_$taskId")

    private companion object {
        val RETENTION: Duration = Duration.ofDays(2)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NagStateStoreModule {
    /** Device-only, not the user preferences file, so it is never exported. */
    private const val FILE = "planb_nag_state"

    @Provides
    @Singleton
    @NagStore
    fun provideNagDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = createPreferencesDataStore(scope) { context.preferencesDataStoreFile(FILE) }
}
