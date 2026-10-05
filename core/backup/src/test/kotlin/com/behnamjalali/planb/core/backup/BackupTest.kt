package com.behnamjalali.planb.core.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.data.AttachmentFiles
import com.behnamjalali.planb.core.data.DocumentFiles
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.data.SearchIndexMaintenance
import com.behnamjalali.planb.core.data.repository.OfflineProjectRepository
import com.behnamjalali.planb.core.data.repository.OfflineTaskRepository
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.entity.ActivityLogEntity
import com.behnamjalali.planb.core.database.entity.AttachmentEntity
import com.behnamjalali.planb.core.database.entity.BadgeEntity
import com.behnamjalali.planb.core.database.entity.CalendarLinkEntity
import com.behnamjalali.planb.core.database.entity.ChallengeEntity
import com.behnamjalali.planb.core.database.entity.HabitEntity
import com.behnamjalali.planb.core.database.entity.JournalEntryEntity
import com.behnamjalali.planb.core.database.entity.MoodEntryEntity
import com.behnamjalali.planb.core.database.entity.NoteLinkEntity
import com.behnamjalali.planb.core.database.entity.NoteVersionEntity
import com.behnamjalali.planb.core.database.entity.SavedFilterEntity
import com.behnamjalali.planb.core.database.entity.TaskDependencyEntity
import com.behnamjalali.planb.core.database.entity.TaskReminderEntity
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
    val cancelled = mutableListOf<String>()
    override suspend fun syncTask(taskId: EntityId) = Unit
    override suspend fun syncEvent(eventId: EntityId) = Unit
    override suspend fun syncHabit(habitId: EntityId) = Unit
    override suspend fun cancelTask(taskId: EntityId) { cancelled += "task:$taskId" }
    override suspend fun cancelEvent(eventId: EntityId) { cancelled += "event:$eventId" }
    override suspend fun cancelHabit(habitId: EntityId) { cancelled += "habit:$habitId" }
    override suspend fun rescheduleAll() { rescheduled++ }
    override fun scheduleFocusEnd(at: Instant) = Unit
    override fun cancelFocusEnd() { cancelled += "focus" }
}

@RunWith(RobolectricTestRunner::class)
class BackupTest {
    private lateinit var db: PlanBDatabase
    private lateinit var manager: BackupManager
    private lateinit var prefs: UserPreferencesDataSource
    private val reminders = NoOpReminders()
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val filesRoot: File = Files.createTempDirectory("files").toFile()
    private val attachments = AttachmentFiles(filesRoot)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PlanBDatabase::class.java).build()
        val dir = Files.createTempDirectory("prefs").toFile()
        prefs = UserPreferencesDataSource(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "p.preferences_pb") })
        manager = BackupManager(
            db, db.backupDao(), prefs, DocumentFiles(context, Dispatchers.IO), SearchIndexMaintenance(db.backupDao(), db.searchDao()),
            reminders, FakeTimeProvider(), AppVersion("1.0.0", 1), attachments,
        )
    }

    @After
    fun tearDown() {
        db.close()
        filesRoot.deleteRecursively()
    }

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
    fun oversizedArchive_isRejectedBeforeExhaustingMemory() {
        // Highly compressible content: tiny on disk, large when inflated (a zip bomb in miniature).
        val bytes = zip("manifest.json" to manifestV1, "database.json" to " ".repeat(64 * 1024))
        val error = assertThrows(BackupException.Corrupt::class.java) {
            BackupCodec.read(ByteArrayInputStream(bytes), maxTotalBytes = 16 * 1024)
        }
        assertThat(error.message).contains("too large")
        assertThat(bytes.size).isLessThan(4 * 1024)
    }

    @Test
    fun outOfMemoryWhileReading_isReportedAsDamaged() {
        val exhausting = object : java.io.InputStream() {
            override fun read(): Int = throw OutOfMemoryError("test")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw OutOfMemoryError("test")
        }
        val error = assertThrows(BackupException.Corrupt::class.java) { BackupCodec.read(exhausting) }
        assertThat(error.message).contains("too large")
        assertThat(BackupFormat.MAX_ENTRY_BYTES).isAtMost(32L * 1024 * 1024)
    }

    @Test
    fun newerDatabaseSchema_isRejected() {
        val newer = PlanBDatabase.VERSION + 1
        val bytes = zip(
            "manifest.json" to """{"backupFormatVersion":1,"appVersion":"9","createdAt":1,"databaseSchemaVersion":$newer}""",
            "database.json" to "{}",
        )
        val e = assertThrows(BackupException.NewerDatabase::class.java) { BackupCodec.read(ByteArrayInputStream(bytes)) }
        assertThat(e.schemaVersion).isEqualTo(newer)
        // The current and older schemas are accepted.
        val current = zip(
            "manifest.json" to """{"backupFormatVersion":1,"appVersion":"1","createdAt":1,"databaseSchemaVersion":${PlanBDatabase.VERSION}}""",
            "database.json" to "{}",
        )
        assertThat(BackupCodec.read(ByteArrayInputStream(current)).manifest.databaseSchemaVersion).isEqualTo(PlanBDatabase.VERSION)
    }

    @Test
    fun deleteAllData_cancelsRemindersOfDeletedItems() = runBlocking {
        val dao = db.backupDao()
        dao.insertTasks(listOf(task(1, "with reminder").copy(reminderOffsetMinutes = 10), task(2, "without")))
        dao.insertHabits(listOf(HabitEntity(3, "آب", "water", "powder_blue", "DAILY", 1, "", java.time.LocalTime.of(9, 0), LocalDate.of(2026, 1, 1), t0, t0, false)))

        manager.deleteAllData()

        assertThat(db.taskDao().count()).isEqualTo(0)
        assertThat(reminders.cancelled).containsAtLeast("task:1", "habit:3", "focus")
        assertThat(reminders.cancelled).doesNotContain("task:2")
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
        val sameTagName = BackupDatabase(tags = listOf(TagDto(1, "کار"), TagDto(2, "کار")))
        assertThrows(BackupException.Invalid::class.java) { BackupValidator.validate(sameTagName) }
        // The database compares tag names with COLLATE NOCASE (ASCII letters only).
        val caseVariants = BackupDatabase(tags = listOf(TagDto(1, "Work"), TagDto(2, "work")))
        assertThrows(BackupException.Invalid::class.java) { BackupValidator.validate(caseVariants) }
        val nonAsciiCase = BackupDatabase(tags = listOf(TagDto(1, "Äpfel"), TagDto(2, "äpfel")))
        BackupValidator.validate(nonAsciiCase) // distinct for NOCASE, like in the database
        val duplicateLink = BackupDatabase(tasks = listOf(TaskDto(1, "a")), tags = listOf(TagDto(1, "x")), taskTags = listOf(RefDto(1, 1), RefDto(1, 1)))
        assertThrows(BackupException.Invalid::class.java) { BackupValidator.validate(duplicateLink) }
    }

    @Test
    fun restoreFailure_rollsBack_andKeepsCurrentData() = runBlocking {
        seed()
        val before = db.backupDao().tasks()
        // Valid data whose insert fails half-way through the transaction (a test-only trigger
        // stands in for any database error).
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_restore BEFORE INSERT ON tasks WHEN NEW.title = 'boom' BEGIN SELECT RAISE(ABORT, 'boom'); END",
        )
        val bad = BackupArchive(
            BackupManifest(1, "1.0.0", createdAt = 1),
            BackupDatabase(tags = listOf(TagDto(1, "Work")), tasks = listOf(TaskDto(1, "x"), TaskDto(2, "boom"))),
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
    fun csv_guardsFormulaCells_andRoundTripsThem() {
        val values = listOf("=HYPERLINK(\"x\")", "+1", "-5", "@SUM(A1)", "\tTab", "'=already quoted", "'plain apostrophe", "safe")
        val text = Csv.write(listOf("title"), values.map { listOf(it) })
        val lines = text.removePrefix(0xFEFF.toChar().toString()).split("\r\n")
        // Quoted because of the inner quotes; the cell content still starts with the apostrophe.
        assertThat(lines[1]).startsWith("\"'=")
        assertThat(lines[2]).isEqualTo("'+1")
        assertThat(lines[3]).isEqualTo("'-5")
        assertThat(lines[4]).isEqualTo("'@SUM(A1)")
        assertThat(lines.map { it.trimStart('"') }.none { it.startsWith("=") || it.startsWith("+") || it.startsWith("@") }).isTrue()
        assertThat(Csv.parse(text).drop(1).map { it.single() }).isEqualTo(values)
    }

    @Test
    fun markdownZip_entryNamesCannotEscapeTheirFolder() = runBlocking {
        val dao = db.backupDao()
        dao.insertNotebooks(listOf(NotebookEntity(1, "..", "book", "lavender", 0, t0, t0, false)))
        dao.insertNotes(
            listOf(
                NoteEntity(1, 1, null, "../../evil", NoteDocument().encode(), "blocks-v1", false, false, 0, t0, t0, false),
                NoteEntity(2, 1, null, "...", NoteDocument().encode(), "blocks-v1", false, false, 1, t0, t0, false),
            ),
        )
        val file = File(Files.createTempDirectory("export").toFile(), "notes.zip")
        transfer().exportNotesMarkdownZip(Uri.fromFile(file))
        val names = java.util.zip.ZipFile(file).use { zip -> zip.entries().toList().map { it.name } }
        assertThat(names).hasSize(2)
        names.forEach { name ->
            val segments = name.split('/')
            assertThat(segments).hasSize(2)
            segments.forEach { assertThat(it).doesNotContain("..") }
            assertThat(segments.none { it.isBlank() || it.startsWith(".") }).isTrue()
        }
    }

    @Test
    fun csv_roundTripsPersianAndQuotes() {
        val text = Csv.write(listOf("title", "notes"), listOf(listOf("خرید \"نان\", شیر", "line1\nline2")))
        val rows = Csv.parse(text)
        assertThat(rows[1]).containsExactly("خرید \"نان\", شیر", "line1\nline2").inOrder()
    }

    private fun transfer(): DataTransfer {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val time = FakeTimeProvider()
        return DataTransfer(
            db.backupDao(),
            OfflineTaskRepository(db, db.taskDao(), db.tagDao(), db.searchDao(), time, reminders),
            OfflineProjectRepository(db, db.projectDao(), db.tagDao(), db.searchDao(), time),
            DocumentFiles(context, Dispatchers.IO),
        )
    }

    @Test
    fun taskExport_thenImport_addsCopiesLinkedToProjects_withoutOverwriting() = runBlocking {
        seed()
        val transfer = transfer()
        for (extension in listOf("csv", "json")) {
            val file = File(Files.createTempDirectory("export").toFile(), "tasks.$extension")
            val uri = Uri.fromFile(file)
            val before = db.backupDao().tasks()
            val exported = if (extension == "csv") transfer.exportTasksCsv(uri) else transfer.exportTasksJson(uri)
            assertThat(exported).isEqualTo(before.size)

            val result = transfer.importTasks(uri)
            assertThat(result.imported).isEqualTo(before.size)
            val after = db.backupDao().tasks()
            // Originals untouched, copies added.
            assertThat(after.size).isEqualTo(before.size * 2)
            assertThat(after.filter { it.id in before.map { b -> b.id } }).containsExactlyElementsIn(before)
            // Copies of project tasks are linked to the existing project by name, and the
            // subtask copy is linked to the copy of its parent.
            val copies = after.filterNot { it.id in before.map { b -> b.id } }
            assertThat(copies.count { it.projectId == 1L }).isEqualTo(before.count { it.projectId == 1L })
            val subtaskCopies = copies.filter { it.title == "زیرکار" }
            assertThat(subtaskCopies).isNotEmpty()
            subtaskCopies.forEach { sub ->
                assertThat(copies.single { it.id == sub.parentTaskId }.title).isEqualTo(before.single { it.id == 1L }.title)
            }
        }
        assertThat(db.backupDao().projects()).hasSize(1)
    }

    @Test
    fun csvImport_unknownProject_createsIt_andSkipsInvalidRows() = runBlocking {
        val file = File(Files.createTempDirectory("import").toFile(), "tasks.csv")
        file.writeText("title,due_date,project\nخرید,۱۴۰۵-۰۱-۰۱x,\nPlan trip,2026-10-05,سفر\n,2026-10-05,\n")
        val result = transfer().importTasks(Uri.fromFile(file))
        assertThat(result.imported).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(2)
        val project = db.backupDao().projects().single()
        assertThat(project.title).isEqualTo("سفر")
        assertThat(db.backupDao().tasks().single().projectId).isEqualTo(project.id)
    }

    // region Format 2: schema v3 tables and attachment files

    private fun seedV3() = runBlocking<Unit> {
        seed()
        val dao = db.backupDao()
        dao.insertTasks(listOf(task(10, "مرحلهٔ بعد").copy(deadline = LocalDate.of(2026, 4, 1), nag = true, scheduledStart = t0, scheduledEnd = t0.plusSeconds(1800))))
        dao.insertTaskReminders(listOf(TaskReminderEntity(1, 1, "OFFSET", 60, null), TaskReminderEntity(2, 1, "ABSOLUTE", null, t0)))
        dao.insertTaskDependencies(listOf(TaskDependencyEntity(10, 1)))
        dao.insertNotes(listOf(NoteEntity(2, 1, null, "قفل", NoteDocument().encode(), "blocks-v1", false, false, 1, t0, t0, false, locked = true, encryptedPayload = byteArrayOf(1, 0, -1, 7))))
        dao.insertNoteVersions(listOf(NoteVersionEntity(1, 1, t0, "نسخهٔ قدیم", "{}", 2)))
        dao.insertNoteLinks(listOf(NoteLinkEntity(1, 2)))
        dao.insertMoodEntries(listOf(MoodEntryEntity(1, LocalDate.of(2026, 1, 2), java.time.LocalTime.of(21, 0), 4, 3, "work", 1, t0, t0)))
        dao.insertJournalEntries(listOf(JournalEntryEntity(1, LocalDate.of(2026, 1, 2), 1, "gratitude", t0, t0)))
        dao.insertChallenges(listOf(ChallengeEntity(1, "HABIT_STREAK", "۲۱ روز آب", 21, LocalDate.of(2026, 1, 1), 1, "ACTIVE", null, t0, t0)))
        dao.insertBadges(listOf(BadgeEntity(1, "first_week", t0)))
        dao.insertActivityLog(listOf(ActivityLogEntity(1, "TASK", 1, "CREATED", t0, "کار شمارهٔ 1")))
        dao.insertCalendarLinks(listOf(CalendarLinkEntity(1, "TASK", 1, 3, 77, t0, 5, "abc")))
        dao.insertSavedFilters(listOf(SavedFilterEntity(1, "فوری", "star", "rose", "{\"version\":1}", 0, t0, t0)))
        attachments.directory.mkdirs()
        attachments.file("photo.jpg").writeBytes(ByteArray(3000) { (it % 251).toByte() })
        dao.insertAttachments(listOf(AttachmentEntity(1, "NOTE", 1, "IMAGE", "photo.jpg", "عکس.jpg", "image/jpeg", 3000, null, 640, 480, "متن تصویر", null, 0, t0)))
    }

    private fun zipBytes(archive: BackupArchive): ByteArray = ByteArrayOutputStream().also { BackupCodec.write(archive, it) }.toByteArray()

    private fun readStaged(bytes: ByteArray, maxAttachmentBytes: Long = BackupFormat.MAX_ATTACHMENTS_TOTAL_BYTES) =
        BackupCodec.read(ByteArrayInputStream(bytes), stagingDirectory = attachments.newStagingDirectory(), maxAttachmentBytes = maxAttachmentBytes)

    /** Plan-B Pro planning (#4, #10–#14) data as the features write it survives a backup and restore. */
    @Test
    fun planningData_roundTrips_andExtraOnlyRemindersAreCancelledOnRestore() = runBlocking<Unit> {
        seed()
        val dao = db.backupDao()
        val rule = com.behnamjalali.planb.core.model.RecurrenceRule(
            com.behnamjalali.planb.core.model.RecurrenceFrequency.MONTHLY,
            weekdays = setOf(java.time.DayOfWeek.MONDAY), setPosition = 2, calendarSystem = com.behnamjalali.planb.core.model.CalendarSystem.JALALI,
        )
        val afterDone = com.behnamjalali.planb.core.model.RecurrenceRule(
            com.behnamjalali.planb.core.model.RecurrenceFrequency.DAILY, interval = 3,
            basis = com.behnamjalali.planb.core.model.RecurrenceBasis.COMPLETION, count = 4,
        )
        dao.insertTasks(
            listOf(
                task(20, "گزارش ماهانه").copy(deadline = LocalDate.of(2026, 3, 25), nag = true, recurrence = rule.encode()),
                task(21, "آبیاری").copy(recurrence = afterDone.encode()),
            ),
        )
        dao.insertTaskReminders(
            listOf(
                TaskReminderEntity(1, 20, "DEADLINE", 1440, null),
                TaskReminderEntity(2, 20, "NAG", 15, null),
                TaskReminderEntity(3, 21, "ABSOLUTE", null, t0),
            ),
        )
        dao.insertTaskDependencies(listOf(TaskDependencyEntity(20, 1), TaskDependencyEntity(21, 20)))
        val filter = com.behnamjalali.planb.core.model.SmartFilter(
            projectIds = setOf(1), priorities = setOf(com.behnamjalali.planb.core.model.Priority.HIGH),
            dateRange = com.behnamjalali.planb.core.model.SmartDateRange.NEXT_7_DAYS, hasDeadline = true, text = "گزارش",
            sort = com.behnamjalali.planb.core.model.TaskSort.DEADLINE,
        )
        dao.insertSavedFilters(listOf(SavedFilterEntity(5, "فوری", "rocket", "rose", com.behnamjalali.planb.core.model.SmartFilterCodec.encode(filter), 2, t0, t0)))
        val before = listOf(dao.tasks(), dao.taskReminders(), dao.taskDependencies(), dao.savedFilters())

        val archive = roundTrip(manager.snapshot())
        manager.restore(archive)

        assertThat(listOf(dao.tasks(), dao.taskReminders(), dao.taskDependencies(), dao.savedFilters())).isEqualTo(before)
        val restored = dao.tasks().associateBy { it.id }
        assertThat(com.behnamjalali.planb.core.model.RecurrenceRule.decode(restored.getValue(20).recurrence)).isEqualTo(rule)
        assertThat(com.behnamjalali.planb.core.model.RecurrenceRule.decode(restored.getValue(21).recurrence)).isEqualTo(afterDone)
        assertThat(restored.getValue(20).deadline).isEqualTo(LocalDate.of(2026, 3, 25))
        assertThat(restored.getValue(20).nag).isTrue()
        assertThat(com.behnamjalali.planb.core.model.SmartFilterCodec.decode(dao.savedFilters().single().query)).isEqualTo(filter)
        // Tasks that only had extra reminders or nagging had their alarms cleared before the restore.
        assertThat(reminders.cancelled).containsAtLeast("task:20", "task:21")
    }

    @Test
    fun v3TablesAndAttachments_roundTripThroughTheZip() = runBlocking<Unit> {
        seedV3()
        val dao = db.backupDao()
        val tasksBefore = dao.tasks()
        val notesBefore = dao.notes()
        val tablesBefore = listOf(
            dao.taskReminders(), dao.taskDependencies(), dao.noteVersions(), dao.noteLinks(), dao.moodEntries(), dao.journalEntries(),
            dao.challenges(), dao.badges(), dao.activityLog(), dao.calendarLinks(), dao.savedFilters(), dao.attachments(),
        )
        val bytes = zipBytes(manager.snapshot())
        val names = java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { z -> generateSequence { z.nextEntry?.name }.toList() }
        assertThat(names).contains("attachments/photo.jpg")

        db.backupDao().clearAll()
        attachments.deleteAll()
        val archive = readStaged(bytes)
        assertThat(archive.manifest.backupFormatVersion).isEqualTo(2)
        assertThat(archive.manifest.databaseSchemaVersion).isEqualTo(PlanBDatabase.VERSION)
        manager.restore(archive)

        assertThat(dao.tasks()).containsExactlyElementsIn(tasksBefore)
        assertThat(dao.notes()).containsExactlyElementsIn(notesBefore)
        val tablesAfter = listOf(
            dao.taskReminders(), dao.taskDependencies(), dao.noteVersions(), dao.noteLinks(), dao.moodEntries(), dao.journalEntries(),
            dao.challenges(), dao.badges(), dao.activityLog(), dao.calendarLinks(), dao.savedFilters(), dao.attachments(),
        )
        assertThat(tablesAfter).isEqualTo(tablesBefore)
        assertThat(attachments.file("photo.jpg").readBytes()).isEqualTo(ByteArray(3000) { (it % 251).toByte() })
        // The staging folder became the attachment folder; nothing is left behind.
        assertThat(archive.stagingDirectory!!.exists()).isFalse()
        // The locked note is found by title only.
        assertThat(db.searchDao().search("قفل*", 10).map { it.entityId }).containsExactly(2L)
    }

    @Test
    fun attachmentRowWithoutItsFile_failsValidation() = runBlocking<Unit> {
        seedV3()
        val archive = manager.snapshot()
        val withoutFiles = BackupCodec.read(ByteArrayInputStream(zipBytes(archive))) // no staging folder: files are skipped
        val error = assertThrows(BackupException.Invalid::class.java) { runBlocking { manager.restore(withoutFiles) } }
        assertThat(error.message).contains("no file")
        assertThat(db.backupDao().attachments()).hasSize(1)
        assertThat(attachments.file("photo.jpg").exists()).isTrue()
    }

    @Test
    fun snapshot_leavesOutAttachmentsWithoutFileOrOwner() = runBlocking<Unit> {
        seedV3()
        db.backupDao().insertAttachments(
            listOf(
                AttachmentEntity(2, "NOTE", 1, "FILE", "gone.pdf", "", "application/pdf", 1, null, null, null, null, null, 0, t0),
                AttachmentEntity(3, "TASK", 999, "FILE", "orphan.pdf", "", "application/pdf", 1, null, null, null, null, null, 0, t0),
            ),
        )
        attachments.file("orphan.pdf").writeText("x")
        val archive = manager.snapshot()
        assertThat(archive.database.attachments.map { it.fileName }).containsExactly("photo.jpg")
        BackupValidator.validate(archive.database, archive.attachmentFiles.keys, requireAttachmentFiles = true)
    }

    @Test
    fun attachmentEntryNames_cannotEscapeTheStagingFolder() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, text) in listOf(
                "manifest.json" to manifestV1, "database.json" to "{}", "attachments/../evil.txt" to "x", "attachments/a/b.txt" to "x",
                "attachments/.hidden" to "x", "attachments/ok.jpg" to "fine",
            )) {
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        val archive = readStaged(out.toByteArray())
        assertThat(archive.attachmentFiles.keys).containsExactly("ok.jpg")
        assertThat(archive.stagingDirectory!!.list()!!.toList()).containsExactly("ok.jpg")
        assertThat(File(archive.stagingDirectory!!.parentFile, "evil.txt").exists()).isFalse()
        assertThat(File(filesRoot, "evil.txt").exists()).isFalse()
    }

    @Test
    fun oversizedAttachments_areRejected() = runBlocking<Unit> {
        seedV3()
        val bytes = zipBytes(manager.snapshot())
        val error = assertThrows(BackupException.Corrupt::class.java) { readStaged(bytes, maxAttachmentBytes = 1000) }
        assertThat(error.message).contains("attachment too large")
    }

    @Test
    fun failedRestore_keepsCurrentDataAndAttachments() = runBlocking<Unit> {
        seedV3()
        val good = readStaged(zipBytes(manager.snapshot()))
        // Change the current state, then make the restore fail half-way.
        db.backupDao().insertTags(listOf(TagEntity(2, "جدید", "rose")))
        attachments.file("new.png").writeText("current")
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_restore BEFORE INSERT ON badges BEGIN SELECT RAISE(ABORT, 'boom'); END",
        )
        assertThrows(BackupException.RestoreFailed::class.java) { runBlocking { manager.restore(good) } }
        assertThat(db.backupDao().tags().map { it.name }).containsExactly("کار", "جدید")
        assertThat(attachments.file("new.png").readText()).isEqualTo("current")
        assertThat(attachments.file("photo.jpg").exists()).isTrue()
    }

    @Test
    fun v3References_areValidated() {
        val base = BackupDatabase(tasks = listOf(TaskDto(1, "a"), TaskDto(2, "b")), notebooks = listOf(NotebookDto(1, "n")), notes = listOf(NoteDto(1, 1)))
        BackupValidator.validate(base.copy(taskDependencies = listOf(RefDto(2, 1))))
        val invalid = listOf(
            base.copy(taskDependencies = listOf(RefDto(1, 1))),
            base.copy(taskDependencies = listOf(RefDto(1, 9))),
            base.copy(taskReminders = listOf(TaskReminderDto(1, 9))),
            base.copy(noteLinks = listOf(RefDto(1, 5))),
            base.copy(attachments = listOf(AttachmentDto(1, "NOTE", 7, fileName = "a.jpg"))),
            base.copy(attachments = listOf(AttachmentDto(1, "NOTE", 1, fileName = "../a.jpg"))),
            base.copy(attachments = listOf(AttachmentDto(1, "PLANET", 1, fileName = "a.jpg"))),
            base.copy(journalEntries = listOf(JournalEntryDto(1, 5, 1), JournalEntryDto(2, 5, 1))),
            base.copy(moodEntries = listOf(MoodEntryDto(1, 5, noteId = 3))),
            base.copy(challenges = listOf(ChallengeDto(1, "HABIT_STREAK", startDate = 1, habitId = 4))),
            base.copy(badges = listOf(BadgeDto(1, "k"), BadgeDto(2, "k"))),
            base.copy(notes = listOf(NoteDto(1, 1, encryptedPayload = "not base64!"))),
            base.copy(calendarLinks = listOf(CalendarLinkDto(1, "TASK", 1, 2, 3), CalendarLinkDto(2, "TASK", 1, 2, 4))),
        )
        invalid.forEach { db -> assertThrows(BackupException.Invalid::class.java) { BackupValidator.validate(db) } }
    }

    @Test
    fun deleteAllData_removesAttachmentFiles() = runBlocking<Unit> {
        seedV3()
        manager.deleteAllData()
        assertThat(db.backupDao().attachments()).isEmpty()
        assertThat(attachments.directory.list().orEmpty()).isEmpty()
    }

    @Test
    fun format1Backup_restoresWithNeutralValuesForNewFields() = runBlocking<Unit> {
        val bytes = zip(
            "manifest.json" to """{"backupFormatVersion":1,"appVersion":"1.0.1","createdAt":1,"databaseSchemaVersion":2}""",
            "database.json" to """{"tasks":[{"id":5,"title":"قدیمی","reminderOffsetMinutes":10}],"habits":[{"id":1,"title":"h","startDate":1}],"focusSessions":[{"id":1,"startedAt":1,"plannedDurationMillis":10}]}""",
        )
        manager.restore(readStaged(bytes))
        val task = db.backupDao().tasks().single()
        assertThat(task.deadline).isNull()
        assertThat(task.nag).isFalse()
        assertThat(task.deletedAt).isNull()
        assertThat(db.backupDao().habits().single().healthMetric).isNull()
        assertThat(db.backupDao().focusSessions().single().strict).isFalse()
    }

    // endregion
}
