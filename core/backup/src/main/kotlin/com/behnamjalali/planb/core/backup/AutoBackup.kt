package com.behnamjalali.planb.core.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.Dispatcher
import com.behnamjalali.planb.core.common.PlanBDispatcher
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.ProStatusSource
import com.behnamjalali.planb.core.datastore.createPreferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class AutoBackupFrequency(val days: Long) { DAILY(1), WEEKLY(7) }

/** Why the last automatic backup failed. Shown to the user in plain words, never as raw errors. */
enum class AutoBackupError {
    /** No folder chosen, or the folder (or the permission to it) is gone. */
    FOLDER_UNAVAILABLE,

    /** Writing the file failed (storage full, cloud folder offline, ...). */
    WRITE_FAILED,
}

/** Automatic backups (Plan-B Pro #37). Device-only: the folder permission belongs to this device. */
data class AutoBackupSettings(
    val enabled: Boolean = false,
    val frequency: AutoBackupFrequency = AutoBackupFrequency.DAILY,
    val chargingOnly: Boolean = false,
    /** A tree URI picked with ACTION_OPEN_DOCUMENT_TREE, with a persisted permission. */
    val folderUri: String? = null,
    val folderName: String? = null,
    val lastRunAt: Instant? = null,
    val lastSuccessAt: Instant? = null,
    /** Null after a successful run. */
    val lastError: AutoBackupError? = null,
    val lastFileName: String? = null,
)

/** File names of automatic backups, and which old ones to delete. */
object AutoBackupNaming {
    const val PREFIX = "Plan-B-auto-"
    const val KEEP = 21
    private val PATTERN = Regex("""^Plan-B-auto-(\d{8})-(\d{6})\.zip$""")
    private val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)

    /** `Plan-B-auto-20261004-100000.zip`: sorts by time, always with Latin digits. */
    fun fileName(at: Instant, zone: ZoneId): String = PREFIX + FORMAT.format(at.atZone(zone)) + ".zip"

    /** Only files with exactly our name pattern are ever counted or deleted. */
    fun isAutoBackup(name: String): Boolean = PATTERN.matches(name)

    /** Our backups beyond the newest [keep], oldest first; other files are never included. */
    fun toPrune(names: Collection<String>, keep: Int = KEEP): List<String> =
        names.filter(::isAutoBackup).distinct().sortedDescending().drop(keep).sorted()

    /** [name], or a later second if a file of that name already exists (two runs in one second). */
    fun unique(at: Instant, zone: ZoneId, existing: Set<String>): String {
        var time = at
        var name = fileName(time, zone)
        while (name in existing) {
            time = time.plusSeconds(1)
            name = fileName(time, zone)
        }
        return name
    }
}

/** A folder the user picked; the SAF implementation works with local and cloud providers. */
interface BackupFolder {
    /** File names in the folder (direct children only). */
    fun list(): List<String>
    fun write(name: String, block: (java.io.OutputStream) -> Unit)
    fun delete(name: String): Boolean
}

/** Opens a folder from its stored URI; null when it is gone or the permission was revoked. */
fun interface BackupFolders {
    fun open(uri: String): BackupFolder?
}

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoBackupStore

/** Settings and the last result, in a device-only file (`planb_auto_backup`), never exported. */
@Singleton
class AutoBackupPreferences @Inject constructor(@AutoBackupStore private val store: DataStore<Preferences>) {
    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val frequency = stringPreferencesKey("frequency")
        val chargingOnly = booleanPreferencesKey("charging_only")
        val folderUri = stringPreferencesKey("folder_uri")
        val folderName = stringPreferencesKey("folder_name")
        val lastRunAt = longPreferencesKey("last_run_at")
        val lastSuccessAt = longPreferencesKey("last_success_at")
        val lastError = stringPreferencesKey("last_error")
        val lastFileName = stringPreferencesKey("last_file_name")
    }

    val settings: Flow<AutoBackupSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.settings() }
        .distinctUntilChanged()

    suspend fun current(): AutoBackupSettings = settings.first()

    private fun Preferences.settings() = AutoBackupSettings(
        enabled = this[Keys.enabled] ?: false,
        frequency = this[Keys.frequency]?.let { n -> AutoBackupFrequency.entries.firstOrNull { it.name == n } } ?: AutoBackupFrequency.DAILY,
        chargingOnly = this[Keys.chargingOnly] ?: false,
        folderUri = this[Keys.folderUri],
        folderName = this[Keys.folderName],
        lastRunAt = this[Keys.lastRunAt]?.let(Instant::ofEpochMilli),
        lastSuccessAt = this[Keys.lastSuccessAt]?.let(Instant::ofEpochMilli),
        lastError = this[Keys.lastError]?.let { n -> AutoBackupError.entries.firstOrNull { it.name == n } },
        lastFileName = this[Keys.lastFileName],
    )

    suspend fun update(transform: (AutoBackupSettings) -> AutoBackupSettings): AutoBackupSettings {
        var result = AutoBackupSettings()
        store.edit { p ->
            val next = transform(p.settings())
            result = next
            p[Keys.enabled] = next.enabled
            p[Keys.frequency] = next.frequency.name
            p[Keys.chargingOnly] = next.chargingOnly
            p.set(Keys.folderUri, next.folderUri)
            p.set(Keys.folderName, next.folderName)
            p.set(Keys.lastRunAt, next.lastRunAt?.toEpochMilli())
            p.set(Keys.lastSuccessAt, next.lastSuccessAt?.toEpochMilli())
            p.set(Keys.lastError, next.lastError?.name)
            p.set(Keys.lastFileName, next.lastFileName)
        }
        return result
    }

    private fun <T> androidx.datastore.preferences.core.MutablePreferences.set(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AutoBackupStoreModule {
    private const val FILE = "planb_auto_backup"

    @Provides
    @Singleton
    @AutoBackupStore
    fun provideAutoBackupDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = createPreferencesDataStore(scope) { context.preferencesDataStoreFile(FILE) }

    @Provides
    fun provideBackupFolders(@ApplicationContext context: Context): BackupFolders =
        BackupFolders { uri -> SafBackupFolder.open(context, uri) }
}

/** The outcome of one run. */
sealed interface AutoBackupResult {
    data class Success(val fileName: String, val pruned: Int) : AutoBackupResult
    data class Failed(val error: AutoBackupError) : AutoBackupResult

    /** Not Pro (any more), or switched off: nothing was written. */
    data object Skipped : AutoBackupResult
}

/**
 * Writes one backup ZIP (the same file as a manual backup) into the chosen folder, then deletes
 * our oldest automatic backups beyond the newest [AutoBackupNaming.KEEP]. Files that do not
 * match our name pattern are never touched.
 */
@Singleton
class AutoBackupRunner @Inject constructor(
    private val backup: BackupManager,
    private val preferences: AutoBackupPreferences,
    private val folders: BackupFolders,
    private val pro: ProStatusSource,
    private val time: TimeProvider,
    @Dispatcher(PlanBDispatcher.IO) private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()

    /** [manual] runs even when the schedule is off ("Back up now"). */
    suspend fun run(manual: Boolean = false): AutoBackupResult = mutex.withLock {
        val settings = preferences.current()
        if (!runCatching { pro.isPro() }.getOrDefault(false)) return@withLock AutoBackupResult.Skipped
        if (!manual && !settings.enabled) return@withLock AutoBackupResult.Skipped
        val now = time.now()
        val result = try {
            withContext(io) { write(settings, now) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AutoBackupResult.Failed(AutoBackupError.WRITE_FAILED)
        }
        preferences.update { current ->
            when (result) {
                is AutoBackupResult.Success -> current.copy(lastRunAt = now, lastSuccessAt = now, lastError = null, lastFileName = result.fileName)
                is AutoBackupResult.Failed -> current.copy(lastRunAt = now, lastError = result.error)
                AutoBackupResult.Skipped -> current
            }
        }
        result
    }

    private suspend fun write(settings: AutoBackupSettings, now: Instant): AutoBackupResult {
        val folder = settings.folderUri?.let { runCatching { folders.open(it) }.getOrNull() }
            ?: return AutoBackupResult.Failed(AutoBackupError.FOLDER_UNAVAILABLE)
        val existing = runCatching { folder.list() }.getOrElse { return AutoBackupResult.Failed(AutoBackupError.FOLDER_UNAVAILABLE) }
        val name = AutoBackupNaming.unique(now, time.zone(), existing.toSet())
        val archive = backup.snapshot()
        try {
            folder.write(name) { BackupCodec.write(archive, it) }
        } catch (e: Exception) {
            // A half-written file must not count as one of the 21 backups.
            runCatching { folder.delete(name) }
            throw e
        }
        // Pruning only ever deletes files that match our own pattern.
        val pruned = AutoBackupNaming.toPrune(existing + name).count { runCatching { folder.delete(it) }.getOrDefault(false) }
        return AutoBackupResult.Success(name, pruned)
    }
}
