package com.behnamjalali.planb.core.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.data.AttachmentFiles
import com.behnamjalali.planb.core.data.DocumentFiles
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.data.SearchIndexMaintenance
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.datastore.UserPreferencesDataSource
import com.behnamjalali.planb.core.datastore.createPreferencesDataStore
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Automatic backups (Plan-B Pro #37): naming, pruning to 21, scheduling and the runner. */
@RunWith(RobolectricTestRunner::class)
class AutoBackupTest {
    private val zone = ZoneId.of("Asia/Tehran")

    @Test
    fun names_sortByTime_andOnlyOurPatternCounts() {
        val name = AutoBackupNaming.fileName(Instant.parse("2026-10-04T06:30:05Z"), zone)
        assertThat(name).isEqualTo("Plan-B-auto-20261004-100005.zip")
        assertThat(AutoBackupNaming.isAutoBackup(name)).isTrue()
        listOf(
            "Plan-B-backup-2026-10-04.zip", "Plan-B-auto-20261004-100005 (1).zip", "plan-b-auto-20261004-100005.zip",
            "Plan-B-auto-20261004-100005.zip.bak", "notes.zip", "Plan-B-auto-2026104-100005.zip",
        ).forEach { assertThat(AutoBackupNaming.isAutoBackup(it)).isFalse() }
    }

    @Test
    fun prune_keepsTheNewest21_andNeverTouchesOtherFiles() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val ours = (0L until 25L).map { AutoBackupNaming.fileName(start.plus(Duration.ofDays(it)), zone) }
        val others = listOf("Plan-B-backup-2026-01-01.zip", "holiday.jpg", "Plan-B-auto-old (1).zip")
        val pruned = AutoBackupNaming.toPrune((ours + others).shuffled())
        assertThat(pruned).containsExactlyElementsIn(ours.take(4)).inOrder()
        assertThat(AutoBackupNaming.toPrune(ours.take(21))).isEmpty()
        assertThat(AutoBackupNaming.toPrune(others)).isEmpty()
    }

    @Test
    fun uniqueName_skipsExistingFiles() {
        val at = Instant.parse("2026-10-04T06:30:05Z")
        val taken = setOf(AutoBackupNaming.fileName(at, zone))
        assertThat(AutoBackupNaming.unique(at, zone, taken)).isEqualTo("Plan-B-auto-20261004-100006.zip")
    }

    @Test
    fun scheduling_followsFrequencyAndChargingOption() {
        assertThat(AutoBackupRequests.request(AutoBackupSettings(enabled = false, folderUri = "content://x"))).isNull()
        assertThat(AutoBackupRequests.request(AutoBackupSettings(enabled = true, folderUri = null))).isNull()

        val daily = AutoBackupRequests.request(AutoBackupSettings(enabled = true, folderUri = "content://x"))!!
        assertThat(daily.workSpec.intervalDuration).isEqualTo(TimeUnit.DAYS.toMillis(1))
        assertThat(daily.workSpec.constraints.requiresCharging()).isFalse()
        assertThat(daily.workSpec.constraints.requiresStorageNotLow()).isTrue()
        assertThat(daily.workSpec.workerClassName).isEqualTo(AutoBackupWorker::class.java.name)

        val weekly = AutoBackupRequests.request(
            AutoBackupSettings(enabled = true, folderUri = "content://x", frequency = AutoBackupFrequency.WEEKLY, chargingOnly = true),
        )!!
        assertThat(weekly.workSpec.intervalDuration).isEqualTo(TimeUnit.DAYS.toMillis(7))
        assertThat(weekly.workSpec.constraints.requiresCharging()).isTrue()
    }

    // region Runner

    private class MemoryFolder(initial: Collection<String>) : BackupFolder {
        val files = initial.associateWith { ByteArray(0) }.toMutableMap()
        var failWrites = false

        override fun list(): List<String> = files.keys.toList()
        override fun write(name: String, block: (OutputStream) -> Unit) {
            val out = ByteArrayOutputStream()
            files[name] = ByteArray(0)
            if (failWrites) error("disk full")
            block(out)
            files[name] = out.toByteArray()
        }
        override fun delete(name: String): Boolean = files.remove(name) != null
    }

    private class NoReminders : ReminderScheduler {
        override suspend fun syncTask(taskId: EntityId) = Unit
        override suspend fun syncEvent(eventId: EntityId) = Unit
        override suspend fun syncHabit(habitId: EntityId) = Unit
        override suspend fun cancelTask(taskId: EntityId) = Unit
        override suspend fun cancelEvent(eventId: EntityId) = Unit
        override suspend fun cancelHabit(habitId: EntityId) = Unit
        override suspend fun rescheduleAll() = Unit
        override fun scheduleFocusEnd(at: Instant) = Unit
        override fun cancelFocusEnd() = Unit
    }

    private lateinit var db: PlanBDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir: File = Files.createTempDirectory("auto-backup").toFile()
    private val time = FakeTimeProvider()
    private var pro = true
    private lateinit var preferences: AutoBackupPreferences
    private lateinit var manager: BackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PlanBDatabase::class.java).build()
        val prefs = UserPreferencesDataSource(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "p.preferences_pb") })
        manager = BackupManager(
            db, db.backupDao(), prefs, DocumentFiles(context, Dispatchers.IO), SearchIndexMaintenance(db.backupDao(), db.searchDao()),
            NoReminders(), time, AppVersion("1.0.0", 1), AttachmentFiles(File(dir, "files")),
        )
        preferences = AutoBackupPreferences(createPreferencesDataStore(scope) { File(dir, "auto.preferences_pb") })
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun runner(folder: BackupFolder?) =
        AutoBackupRunner(manager, preferences, { folder }, { pro }, time, Dispatchers.IO)

    @Test
    fun run_writesARestorableBackup_prunesTo21_andRecordsSuccess() = runTest {
        preferences.update { it.copy(enabled = true, folderUri = "content://folder") }
        val old = (1L..21L).map { AutoBackupNaming.fileName(time.now().minus(Duration.ofDays(it)), time.zone()) }
        val folder = MemoryFolder(old + "my-own-file.zip")

        val result = runner(folder).run()

        val name = (result as AutoBackupResult.Success).fileName
        assertThat(result.pruned).isEqualTo(1)
        assertThat(folder.files.keys.count(AutoBackupNaming::isAutoBackup)).isEqualTo(21)
        assertThat(folder.files).containsKey("my-own-file.zip")
        assertThat(folder.files).doesNotContainKey(old.last())
        val archive = BackupCodec.read(ByteArrayInputStream(folder.files.getValue(name)))
        assertThat(archive.manifest.backupFormatVersion).isEqualTo(BackupFormat.CURRENT)
        val settings = preferences.current()
        assertThat(settings.lastSuccessAt).isEqualTo(time.now())
        assertThat(settings.lastError).isNull()
        assertThat(settings.lastFileName).isEqualTo(name)
    }

    @Test
    fun failures_areRecorded_andLeaveNoHalfWrittenFile() = runTest {
        preferences.update { it.copy(enabled = true, folderUri = "content://folder") }
        assertThat(runner(null).run()).isEqualTo(AutoBackupResult.Failed(AutoBackupError.FOLDER_UNAVAILABLE))
        assertThat(preferences.current().lastError).isEqualTo(AutoBackupError.FOLDER_UNAVAILABLE)

        val folder = MemoryFolder(emptyList()).apply { failWrites = true }
        assertThat(runner(folder).run()).isEqualTo(AutoBackupResult.Failed(AutoBackupError.WRITE_FAILED))
        assertThat(folder.files).isEmpty()
        assertThat(preferences.current().lastError).isEqualTo(AutoBackupError.WRITE_FAILED)
    }

    @Test
    fun withoutPro_orSwitchedOff_nothingIsWritten() = runTest {
        val folder = MemoryFolder(emptyList())
        preferences.update { it.copy(enabled = false, folderUri = "content://folder") }
        assertThat(runner(folder).run()).isEqualTo(AutoBackupResult.Skipped)
        assertThat(runner(folder).run(manual = true)).isInstanceOf(AutoBackupResult.Success::class.java)
        pro = false
        folder.files.clear()
        preferences.update { it.copy(enabled = true) }
        assertThat(runner(folder).run()).isEqualTo(AutoBackupResult.Skipped)
        assertThat(folder.files).isEmpty()
    }

    // endregion
}
