package com.behnamjalali.planb.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.backup.AppVersion
import com.behnamjalali.planb.core.backup.AutoBackupFrequency
import com.behnamjalali.planb.core.backup.AutoBackupPreferences
import com.behnamjalali.planb.core.backup.AutoBackupResult
import com.behnamjalali.planb.core.backup.AutoBackupRunner
import com.behnamjalali.planb.core.backup.AutoBackupScheduler
import com.behnamjalali.planb.core.backup.AutoBackupSettings
import com.behnamjalali.planb.core.backup.BackupFolder
import com.behnamjalali.planb.core.backup.BackupManager
import com.behnamjalali.planb.core.data.AttachmentFiles
import com.behnamjalali.planb.core.data.DocumentFiles
import com.behnamjalali.planb.core.data.SearchIndexMaintenance
import com.behnamjalali.planb.core.datastore.createPreferencesDataStore
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.util.Collections
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoBackupViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir: File = Files.createTempDirectory("auto").toFile()
    private val scheduled: MutableList<AutoBackupSettings> = Collections.synchronizedList(mutableListOf())
    private val written = Collections.synchronizedList(mutableListOf<String>())
    private var pro = true
    private lateinit var preferences: AutoBackupPreferences
    private lateinit var runner: AutoBackupRunner

    private val folder = object : BackupFolder {
        override fun list(): List<String> = written.toList()
        override fun write(name: String, block: (OutputStream) -> Unit) {
            block(OutputStream.nullOutputStream())
            written += name
        }
        override fun delete(name: String) = written.remove(name)
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        graph = TestDataGraph()
        val backup = BackupManager(
            graph.db, graph.db.backupDao(), graph.preferences, DocumentFiles(context, Dispatchers.IO),
            SearchIndexMaintenance(graph.db.backupDao(), graph.db.searchDao()), graph.reminders, graph.time,
            AppVersion("1.0.0", 1), AttachmentFiles(File(dir, "files")),
        )
        preferences = AutoBackupPreferences(createPreferencesDataStore(appScope) { File(dir, "auto.preferences_pb") })
        runner = AutoBackupRunner(backup, preferences, { folder }, { pro }, graph.time, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        appScope.cancel()
        graph.close()
        dir.deleteRecursively()
    }

    private fun viewModel() = main.track(AutoBackupViewModel(preferences, AutoBackupScheduler { scheduled += it }, runner, appScope))

    @Test
    fun pickingAFolder_enablesAndSchedules_andChangesReschedule() = runBlocking<Unit> {
        val vm = viewModel()
        main.keepCollecting(vm.settings)
        vm.folderPicked("content://tree/backups", "Backups")
        val enabled = vm.settings.awaitItem { it?.enabled == true }!!
        assertThat(enabled.folderName).isEqualTo("Backups")
        vm.update { it.copy(frequency = AutoBackupFrequency.WEEKLY, chargingOnly = true) }
        vm.settings.awaitItem { it?.frequency == AutoBackupFrequency.WEEKLY }
        vm.update { it.copy(enabled = false) }
        vm.settings.awaitItem { it?.enabled == false }
        withTimeout(10_000) { while (scheduled.size < 3) kotlinx.coroutines.delay(10) }
        assertThat(scheduled.map { it.enabled }).containsExactly(true, true, false).inOrder()
        assertThat(scheduled[1].chargingOnly).isTrue()
    }

    @Test
    fun backUpNow_writesOneFile_andReportsTheResult() = runBlocking<Unit> {
        preferences.update { it.copy(folderUri = "content://tree/backups") }
        val vm = viewModel()
        val result = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { vm.results.first() } }
        vm.backUpNow()
        assertThat(result.await()).isInstanceOf(AutoBackupResult.Success::class.java)
        assertThat(written).hasSize(1)
        assertThat(preferences.current().lastSuccessAt).isNotNull()
    }
}
