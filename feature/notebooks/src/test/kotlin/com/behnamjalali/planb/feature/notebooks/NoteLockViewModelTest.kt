package com.behnamjalali.planb.feature.notebooks

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.data.DocumentFiles
import com.behnamjalali.planb.core.data.repository.OfflineNoteRepository
import com.behnamjalali.planb.core.data.repository.TemplateRepository
import com.behnamjalali.planb.core.data.repository.TemplateResult
import com.behnamjalali.planb.core.data.security.NoteVault
import com.behnamjalali.planb.core.data.security.SecurityPreferences
import com.behnamjalali.planb.core.datastore.createPreferencesDataStore
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.PlannerTemplate
import com.behnamjalali.planb.core.model.TemplatePayload
import com.behnamjalali.planb.core.model.TemplateType
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Locked notes in the editor (Plan-B Pro #36). */
@RunWith(RobolectricTestRunner::class)
class NoteLockViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph
    private lateinit var notes: OfflineNoteRepository
    private lateinit var vault: NoteVault
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir: File = Files.createTempDirectory("lock").toFile()

    private object NoTemplates : TemplateRepository {
        override fun observeTemplates(): Flow<List<PlannerTemplate>> = flowOf(emptyList())
        override fun builtInTemplates(): List<PlannerTemplate> = emptyList()
        override suspend fun saveCustom(title: String, type: TemplateType, payload: TemplatePayload, id: EntityId): EntityId = 0
        override suspend fun saveNoteAsTemplate(note: Note): EntityId = 0
        override suspend fun rename(id: EntityId, title: String) = Unit
        override suspend fun delete(id: EntityId) = Unit
        override suspend fun apply(template: PlannerTemplate, dateLabel: String, notebookTitle: String) = TemplateResult(TemplateType.NOTE, 0)
    }

    @Before
    fun setUp() {
        graph = TestDataGraph()
        val prefs = SecurityPreferences(createPreferencesDataStore(scope) { File(dir, "s.preferences_pb") })
        vault = NoteVault(prefs, 1_000, Dispatchers.Default)
        val db = graph.db
        notes = OfflineNoteRepository(db, db.noteDao(), db.noteDraftDao(), db.tagDao(), db.searchDao(), graph.time, graph.history, vault)
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun viewModel(id: EntityId) = main.track(
        NoteEditorViewModel(
            SavedStateHandle(mapOf("noteId" to id)), notes, NoTemplates,
            DocumentFiles(ApplicationProvider.getApplicationContext(), Dispatchers.IO), main.scope, vault, null,
        ),
    ).also { it.start("Notes") }

    @Test
    fun lockingWithoutPassphrase_asksToSetOne_thenEncrypts_andHidesWhenTheVaultCloses() = runBlocking<Unit> {
        val notebook = notes.ensureDefaultNotebook("Notes")
        val id = notes.saveNote(Note(notebookId = notebook, title = "Bank", document = NoteDocument(blocks = listOf(NoteBlock("1", BlockType.TEXT, "PIN zebra")))))
        val vm = viewModel(id)
        vm.state.awaitItem { !it.loading }

        vm.lockNote()
        vm.state.awaitItem { it.passphraseDialog == PassphraseDialog.SETUP }
        vm.setUpPassphrase("a long passphrase".toCharArray())
        val locked = vm.state.awaitItem { it.locked && it.passphraseDialog == null && !it.busy }
        assertThat(locked.blocks.single().value.text).isEqualTo("PIN zebra")
        assertThat(graph.db.noteDao().getNote(id)!!.content).doesNotContain("zebra")

        // Edits are saved encrypted.
        val block = locked.blocks.single()
        vm.onBlockChange(block.id, TextFieldValue("PIN zebra 2", TextRange(11)))
        vm.save()
        assertThat(graph.db.noteDao().getNote(id)!!.content).doesNotContain("zebra")
        assertThat(notes.lockedContent(id).plainText()).contains("PIN zebra 2")

        vault.lock()
        val hidden = vm.state.awaitItem { it.needsUnlock }
        assertThat(hidden.blocks).isEmpty()
    }

    @Test
    fun openingALockedNote_needsThePassphrase_andAWrongOneIsRejected() = runBlocking<Unit> {
        val notebook = notes.ensureDefaultNotebook("Notes")
        val id = notes.saveNote(Note(notebookId = notebook, title = "Diary", document = NoteDocument(blocks = listOf(NoteBlock("1", BlockType.TEXT, "dear diary")))))
        vault.setUp("diary passphrase".toCharArray())
        notes.lockNote(id)
        vault.lock()

        val vm = viewModel(id)
        val closed = vm.state.awaitItem { !it.loading }
        assertThat(closed.needsUnlock).isTrue()
        assertThat(closed.blocks).isEmpty()

        vm.requestUnlock()
        vm.unlock("wrong passphrase".toCharArray())
        vm.state.awaitItem { it.wrongPassphrase }
        vm.unlock("diary passphrase".toCharArray())
        val open = vm.state.awaitItem { !it.needsUnlock && it.passphraseDialog == null }
        assertThat(open.blocks.single().value.text).isEqualTo("dear diary")

        vm.removeLock()
        vm.state.awaitItem { !it.locked }
        assertThat(graph.db.noteDao().getNote(id)!!.encryptedPayload).isNull()
    }
}
