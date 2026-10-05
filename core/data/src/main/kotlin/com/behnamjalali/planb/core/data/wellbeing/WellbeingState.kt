package com.behnamjalali.planb.core.data.wellbeing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.datastore.createPreferencesDataStore
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.StrictModeState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WellbeingStore

/**
 * Device-only state of Plan-B Pro habits and focus (#26, #27, #29): the Do Not Disturb filter to
 * put back after a strict focus session, the days Health Connect already checked a habit off,
 * and the last badge whose unlock was celebrated. Never exported or restored: it describes this
 * device (its system settings, its Health Connect) rather than the user's data.
 */
@Singleton
class WellbeingState @Inject constructor(@WellbeingStore private val store: DataStore<Preferences>) {
    private val data: Flow<Preferences> = store.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    // region strict mode (#26)
    suspend fun strictMode(): StrictModeState? {
        val p = data.first()
        val previous = p[Keys.dndPrevious] ?: return null
        val applied = p[Keys.dndApplied] ?: return null
        return StrictModeState(previous, applied)
    }

    suspend fun setStrictMode(state: StrictModeState?) {
        store.edit { p ->
            if (state == null) {
                p.remove(Keys.dndPrevious)
                p.remove(Keys.dndApplied)
            } else {
                p[Keys.dndPrevious] = state.previousFilter
                p[Keys.dndApplied] = state.appliedFilter
            }
        }
    }
    // endregion

    // region Health Connect (#27)
    /** Days Health Connect already checked [habitId] off. */
    suspend fun healthChecked(habitId: EntityId): Set<LocalDate> = parseDays(data.first()[healthKey(habitId)])

    /** Records [date] as checked off for [habitId]; days older than [keepFrom] are dropped. */
    suspend fun addHealthChecked(habitId: EntityId, date: LocalDate, keepFrom: LocalDate) {
        store.edit { p ->
            val days = (parseDays(p[healthKey(habitId)]) + date).filter { it >= keepFrom }.sorted()
            p[healthKey(habitId)] = days.joinToString(",") { it.toEpochDay().toString() }
        }
    }

    val lastHealthSync: Flow<Long> = data.map { it[Keys.healthLastSync] ?: 0L }

    suspend fun setLastHealthSync(at: Long) {
        store.edit { it[Keys.healthLastSync] = at }
    }
    // endregion

    // region badges (#29)
    /** Badges with an id up to this were celebrated (or existed before celebrations started). */
    val celebratedBadgeId: Flow<Long?> = data.map { it[Keys.celebrated] }

    suspend fun setCelebratedBadgeId(id: Long) {
        store.edit { p -> p[Keys.celebrated] = maxOf(id, p[Keys.celebrated] ?: 0L) }
    }
    // endregion

    private fun healthKey(habitId: EntityId) = stringPreferencesKey("health_checked_$habitId")

    private fun parseDays(raw: String?): Set<LocalDate> =
        raw.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull()?.let(LocalDate::ofEpochDay) }.toSet()

    private object Keys {
        val dndPrevious = intPreferencesKey("dnd_previous_filter")
        val dndApplied = intPreferencesKey("dnd_applied_filter")
        val healthLastSync = longPreferencesKey("health_last_sync")
        val celebrated = longPreferencesKey("badges_celebrated_through")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object WellbeingStoreModule {
    /** Device-only, not the user preferences file, so it is never exported. */
    private const val FILE = "planb_wellbeing_state"

    @Provides
    @Singleton
    @WellbeingStore
    fun provideWellbeingDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = createPreferencesDataStore(scope) { context.preferencesDataStoreFile(FILE) }
}
