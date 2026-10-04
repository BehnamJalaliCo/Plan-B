package com.behnamjalali.planb.feature.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.backup.AppVersion
import com.behnamjalali.planb.core.backup.BackupArchive
import com.behnamjalali.planb.core.backup.BackupCodec
import com.behnamjalali.planb.core.backup.BackupManager
import com.behnamjalali.planb.core.backup.DataTransfer
import com.behnamjalali.planb.core.data.AttachmentFiles
import com.behnamjalali.planb.core.data.DocumentFiles
import com.behnamjalali.planb.core.data.SearchIndexMaintenance
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph
    private lateinit var backup: BackupManager
    private lateinit var transfer: DataTransfer
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        graph = TestDataGraph()
        val files = DocumentFiles(context, Dispatchers.IO)
        backup = BackupManager(
            graph.db, graph.db.backupDao(), graph.preferences, files, SearchIndexMaintenance(graph.db.backupDao(), graph.db.searchDao()),
            graph.reminders, graph.time, AppVersion("1.0.0", 1), AttachmentFiles(java.nio.file.Files.createTempDirectory("files").toFile()),
        )
        transfer = DataTransfer(graph.db.backupDao(), graph.tasks, graph.projects, files)
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        appScope.cancel()
        graph.close()
    }

    private fun viewModel() = main.track(DataViewModel(backup, transfer, graph.settings, appScope))

    @Test
    fun deleteAll_finishes_evenWhenTheScreenIsLeft() = runBlocking<Unit> {
        graph.tasks.save(Task(title = "Doomed"))
        val viewModel = viewModel()
        viewModel.deleteAll()
        // Leaving the screen clears the ViewModel (and cancels viewModelScope) right away.
        main.clearViewModels()
        withTimeout(20_000) {
            while (graph.db.taskDao().count() > 0 || graph.reminders.rescheduledAll == 0) delay(20)
        }
    }

    @Test
    fun restoreResult_isKeptInState_untilShown() = runBlocking<Unit> {
        graph.settings.update { it.copy(language = AppLanguage.ENGLISH) }
        graph.tasks.save(Task(title = "Saved"))
        val archive = backup.snapshot()
        graph.settings.update { it.copy(language = AppLanguage.PERSIAN) }

        val viewModel = viewModel()
        // Nobody collects messages while the restore runs; the result must still arrive.
        restore(viewModel, archive)
        val state = viewModel.state.awaitItem { !it.busy && it.message != null }
        assertThat(state.message).isEqualTo(DataMessage.Restored(AppLanguage.ENGLISH))
        assertThat(graph.settings.current().language).isEqualTo(AppLanguage.ENGLISH)

        viewModel.messageShown(state.message!!)
        assertThat(viewModel.state.value.message).isNull()
    }

    /** Same steps as the screen: open and inspect the file, then confirm. */
    private suspend fun restore(viewModel: DataViewModel, archive: BackupArchive) {
        val file = File(Files.createTempDirectory("backup").toFile(), "backup.zip")
        file.outputStream().use { BackupCodec.write(archive, it) }
        viewModel.inspect(Uri.fromFile(file))
        viewModel.state.awaitItem { it.pendingRestore != null }
        viewModel.confirmRestore()
    }

    @Test
    fun notificationRow_requestsOnlyWhileAndroidStillShowsTheDialog() {
        val t = Build.VERSION_CODES.TIRAMISU
        fun action(sdk: Int = t, enabled: Boolean = false, granted: Boolean = false, requested: Boolean = false, rationale: Boolean = false) =
            notificationRowAction(sdk, enabled, granted, requested, rationale)

        assertThat(action()).isEqualTo(NotificationRowAction.REQUEST_PERMISSION)
        // Denied once: Android asks for a rationale and still shows the dialog.
        assertThat(action(requested = true, rationale = true)).isEqualTo(NotificationRowAction.REQUEST_PERMISSION)
        // Denied for good: the dialog would never appear again.
        assertThat(action(requested = true, rationale = false)).isEqualTo(NotificationRowAction.OPEN_SETTINGS)
        // Permission granted but notifications blocked in the system settings.
        assertThat(action(granted = true)).isEqualTo(NotificationRowAction.OPEN_SETTINGS)
        assertThat(action(enabled = true, granted = true)).isEqualTo(NotificationRowAction.OPEN_SETTINGS)
        assertThat(action(sdk = t - 1)).isEqualTo(NotificationRowAction.OPEN_SETTINGS)
    }
}
