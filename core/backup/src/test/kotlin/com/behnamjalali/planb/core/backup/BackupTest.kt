package com.behnamjalali.planb.core.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.data.DocumentFiles
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.data.SearchIndexMaintenance
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.entity.HabitEntity
import com.behnamjalali.planb.core.database.entity.NoteEntity
import com.behnamjalali.planb.core.database.entity.NotebookEntity
import com.behnamjalali.planb.core.database.entity.ProjectEntity
import com.behnamjalali.planb.core.database.entity.TagEntity
import com.behnamjalali.planb.core.database.entity.TaskEntity
import com.behnamjalali.planb.core.database.entity.TaskTagCrossRef
import com.behnamjalali.planb.core.datastore.UserPreferencesDataSource
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class NoOpReminders : ReminderScheduler {
    var rescheduled = 0
    override suspend fun syncTask(taskId: EntityId) = Unit
    override suspend fun syncEvent(eventId: EntityId) = Unit
    override suspend fun syncHabit(habitId: EntityId) = Unit
    override suspend fun cancelTask(taskId: EntityId) = Unit
    override suspend fun cancelEvent(eventId: EntityId) = Unit
    override suspend fun cancelHabit(habitId: EntityId) = Unit
    override suspend fun rescheduleAll() { rescheduled++ }
    override fun scheduleFocusEnd(at: Instant) = Unit
    override fun cancelFocusEnd() = Unit
}

@RunWith(RobolectricTestRunner::class)
class BackupTest {
    private lateinit var db: PlanBDatabase
    private lateinit var manager: BackupManager
    private lateinit var prefs: UserPreferencesDataSource
    private val reminders = NoOpReminders()
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PlanBDatabase::class.java).build()
        val dir = Files.createTempDirectory("prefs").toFile()
        prefs = UserPreferencesDataSource(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "p.preferences_pb") })
        manager = BackupManager(
            db, db.backupDao(), prefs, DocumentFiles(context, Dispatchers.IO), SearchIndexMaintenance(db.backupDao(), db.searchDao()),
            reminders, FakeTimeProvider(), AppVersion("1.0.0", 1),
        )
    }

    @After
    fun tearDown() = db.close()

    private fun task(id: Long, title: String, project: Long? = null, parent: Long? = null) = TaskEntity(
        id, title, "", "TODO", false, 0, null, LocalDate.of(2026, 3, 21), null, null, null, project, parent, null, null, null, null,
        "یادداشت mixed", id, t0, t0, null, false,
    )

    private fun seed(taskCount: Int = 3) = runBlocking {
        val dao = db.backupDao()
        dao.insertTags(listOf(TagEntity(1, "کار", "mint")))
        dao.insertProjects(listOf(ProjectEntity(1, "پروژهٔ Plan-B", "", "rose", "rocket", "ACTIVE", "TASKS", 0f, null, null, 0, t0, t0, false)))
        dao.insertTasks((1L..taskCount).map { task(it, "کار شمارهٔ $it — task $it 🎯", project = 1) })
        dao.insertTasks(listOf(task(taskCount + 1L, "زیرکار", parent = 1)))
        dao.insertTaskTags(listOf(TaskTagCrossRef(1, 1)))
        dao.insertNotebooks(listOf(NotebookEntity(1, "دفتر", "book", "lavender", 0, t0, t0, false)))
        val doc = NoteDocument(blocks = listOf(NoteBlock("a", BlockType.CHECKLIST, "خرید نان", true), NoteBlock("b", BlockType.TEXT, "Hello سلام (۱۲۳) https://example.com")))
        dao.insertNotes(listOf(NoteEntity(1, 1, null, "یادداشت", doc.encode(), "blocks-v1", true, false, 0, t0, t0, false)))
        dao.insertHabits(listOf(HabitEntity(1, "آب", "water", "powder_blue", "DAILY", 8, "لیوان", null, LocalDate.of(2026, 1, 1), t0, t0, false)))
    }

    private fun roundTrip(archive: BackupArchive): BackupArchive {
        val out = ByteArrayOutputStream()
        BackupCodec.write(archive, out)
        return BackupCodec.read(ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun emptyDatabase_roundTrips() = runBlocking {
        val archive = roundTrip(manager.snapshot())
        assertThat(archive.manifest.backupFormatVersion).isEqualTo(BackupFormat.CURRENT)
        assertThat(archive.database.tasks).isEmpty()
        manager.restore(archive)
        assertThat(db.backupDao().tasks()).isEmpty()
    }

    @Test
    fun normalDatabase_withPersianAndMixedText_restoresExactly() = runBlocking {
        seed()
        prefs.update { it.copy(language = AppLanguage.ENGLISH, focusMinutes = 40) }
        val archive = roundTrip(manager.snapshot())
        val before = db.backupDao().tasks()
        val notesBefore = db.backupDao().notes()
        db.backupDao().clearAll()
        prefs.update { it.copy(language = AppLanguage.PERSIAN, focusMinutes = 25) }

        manager.restore(archive)

        assertThat(db.backupDao().tasks()).containsExactlyElementsIn(before)
        assertThat(db.backupDao().notes()).containsExactlyElementsIn(notesBefore)
        assertThat(db.backupDao().taskTags()).hasSize(1)
        assertThat(prefs.current().language).isEqualTo(AppLanguage.ENGLISH)
        assertThat(prefs.current().focusMinutes).isEqualTo(40)
        // Search index rebuilt for restored data
        assertThat(db.searchDao().search("نان*", 10).map { it.entityId }).containsExactly(1L)
        assertThat(reminders.rescheduled).isEqualTo(1)
    }

    @Test
    fun largeDatabase_roundTrips() = runBlocking {
        seed(taskCount = 5_000)
        val archive = roundTrip(manager.snapshot())
        assertThat(archive.database.tasks).hasSize(5_001)
        manager.restore(archive)
        assertThat(db.taskDao().count()).isEqualTo(5_001)
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, text) ->
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val manifestV1 = """{"backupFormatVersion":1,"appVersion":"0.9.0","createdAt":1}"""

    @Test
    fun oldFormat_withMissingOptionalFieldsAndTables_imports() = runBlocking {
        // An older writer: no preferences/metadata files, no templates/focus arrays, minimal task fields.
        val bytes = zip(
            "manifest.json" to manifestV1,
            "database.json" to """{"tasks":[{"id":5,"title":"قدیمی"}],"notebooks":[{"id":2,"title":"N"}],"notes":[{"id":3,"notebookId":2}],"unknownFutureTable":[1,2]}""",
        )
        val archive = BackupCodec.read(ByteArrayInputStream(bytes))
        assertThat(archive.preferences).isEmpty()
        manager.restore(archive)
        val restored = db.backupDao().tasks().single()
        assertThat(restored.title).isEqualTo("قدیمی")
        assertThat(restored.status).isEqualTo("TODO")
        assertThat(db.backupDao().notes().single().title).isEmpty()
    }

    @Test
    fun corruptJson_isRejected() {
        val bytes = zip("manifest.json" to manifestV1, "database.json" to """{"tasks":[{"id":1,"title":""")
        assertThrows(BackupException.Corrupt::class.java) { BackupCodec.read(ByteArrayInputStream(bytes)) }
    }

    @Test
    fun corruptZip_isRejected() {
        val good = zip("manifest.json" to manifestV1, "database.json" to "{}")
        val truncated = good.copyOf(good.size / 2)
        assertThrows(BackupException::class.java) { BackupCodec.read(ByteArrayInputStream(truncated)) }
        assertThrows(BackupException::class.java) { BackupCodec.read(ByteArrayInputStream("not a zip at all".toByteArray())) }
    }

    @Test
    fun futureFormat_isRejected() {
        val bytes = zip("manifest.json" to """{"backupFormatVersion":99,"appVersion":"9","createdAt":1}""", "database.json" to "{}")
        val e = assertThrows(BackupException.UnsupportedVersion::class.java) { BackupCodec.read(ByteArrayInputStream(bytes)) }
        assertThat(e.version).isEqualTo(99)
    }

    @Test
    fun otherApplication_isRejected() {
        val bytes = zip("manifest.json" to """{"backupFormatVersion":1,"appVersion":"1","createdAt":1,"application":"com.other"}""", "database.json" to "{}")
        assertThrows(BackupException.NotABackup::class.java) { BackupCodec.read(ByteArrayInputStream(bytes)) }
    }

    @Test
    fun duplicateIds_andBrokenReferences_failValidation() {
        val dup = BackupDatabase(tasks = listOf(TaskDto(1, "a"), TaskDto(1, "b")))
        assertThrows(BackupException.Invalid::class.java) { BackupValidator.validate(dup) }
        val orphan = BackupDatabase(tasks = listOf(TaskDto(1, "a", projectId = 9)))
        assertThrows(BackupException.Invalid::class.java) { BackupValidator.validate(orphan) }
        val cycle = BackupDatabase(tasks = listOf(TaskDto(1, "a", parentTaskId = 2), TaskDto(2, "b", parentTaskId = 1)))
        assertThrows(BackupException.Invalid::class.java) { BackupValidator.validate(cycle) }
    }

    @Test
    fun restoreFailure_rollsBack_andKeepsCurrentData() = runBlocking {
        seed()
        val before = db.backupDao().tasks()
        // Passes validation but violates the unique (case-insensitive) tag name index during insert.
        val bad = BackupArchive(
            BackupManifest(1, "1.0.0", createdAt = 1),
            BackupDatabase(tags = listOf(TagDto(1, "Work"), TagDto(2, "work")), tasks = listOf(TaskDto(1, "x"))),
            emptyMap(),
            BackupMetadata(),
        )
        assertThrows(BackupException.RestoreFailed::class.java) { runBlocking { manager.restore(bad) } }
        assertThat(db.backupDao().tasks()).containsExactlyElementsIn(before)
        assertThat(db.backupDao().tags().single().name).isEqualTo("کار")
    }

    @Test
    fun subtasksBeforeParents_inArchive_areOrdered() = runBlocking {
        val archive = BackupArchive(
            BackupManifest(1, "1", createdAt = 1),
            BackupDatabase(tasks = listOf(TaskDto(2, "child", parentTaskId = 1), TaskDto(1, "parent"))),
            emptyMap(),
            BackupMetadata(),
        )
        manager.restore(archive)
        assertThat(db.taskDao().count()).isEqualTo(2)
    }

    @Test
    fun csv_roundTripsPersianAndQuotes() {
        val text = Csv.write(listOf("title", "notes"), listOf(listOf("خرید \"نان\", شیر", "line1\nline2")))
        val rows = Csv.parse(text)
        assertThat(rows[1]).containsExactly("خرید \"نان\", شیر", "line1\nline2").inOrder()
    }
}
