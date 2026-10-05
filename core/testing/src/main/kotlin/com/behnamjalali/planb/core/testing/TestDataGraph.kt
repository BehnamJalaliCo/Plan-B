package com.behnamjalali.planb.core.testing

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.data.DataHistory
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.data.repository.OfflineActivityRepository
import com.behnamjalali.planb.core.data.repository.OfflineTrashRepository
import com.behnamjalali.planb.core.data.repository.DataStoreSettingsRepository
import com.behnamjalali.planb.core.data.repository.FtsSearchRepository
import com.behnamjalali.planb.core.data.repository.OfflineEventRepository
import com.behnamjalali.planb.core.data.repository.OfflineFocusRepository
import com.behnamjalali.planb.core.data.repository.OfflineHabitRepository
import com.behnamjalali.planb.core.data.repository.OfflineNoteRepository
import com.behnamjalali.planb.core.data.repository.OfflineProjectRepository
import com.behnamjalali.planb.core.data.repository.OfflineTaskRepository
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.datastore.UserPreferencesDataSource
import com.behnamjalali.planb.core.model.EntityId
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.Collections
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** ReminderScheduler fake that records every call instead of touching AlarmManager. */
class RecordingReminderScheduler : ReminderScheduler {
    val synced: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val cancelled: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Ordered log of focus-alarm calls: "schedule:<instant>" or "cancel". */
    val focusCalls: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Volatile var rescheduledAll = 0

    /** The currently scheduled focus end alarm, or null when none is pending. */
    @Volatile var focusEnd: Instant? = null

    override suspend fun syncTask(taskId: EntityId) { synced += "task:$taskId" }
    override suspend fun syncEvent(eventId: EntityId) { synced += "event:$eventId" }
    override suspend fun syncHabit(habitId: EntityId) { synced += "habit:$habitId" }
    override suspend fun cancelTask(taskId: EntityId) { cancelled += "task:$taskId" }
    override suspend fun cancelEvent(eventId: EntityId) { cancelled += "event:$eventId" }
    override suspend fun cancelHabit(habitId: EntityId) { cancelled += "habit:$habitId" }
    override suspend fun rescheduleAll() { rescheduledAll++ }
    override fun scheduleFocusEnd(at: Instant) {
        focusEnd = at
        focusCalls += "schedule:$at"
    }
    override fun cancelFocusEnd() {
        focusEnd = null
        focusCalls += "cancel"
    }
}

/**
 * Real repositories over an in-memory Room database and a temp-file DataStore,
 * for ViewModel tests that exercise the actual data layer. Requires Robolectric.
 * Call [close] in @After.
 */
class TestDataGraph(
    val time: FakeTimeProvider = FakeTimeProvider(),
    val reminders: RecordingReminderScheduler = RecordingReminderScheduler(),
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsDir: File = Files.createTempDirectory("planb-prefs").toFile()

    val db: PlanBDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        PlanBDatabase::class.java,
    ).setQueryExecutor(executor).setTransactionExecutor(executor).build()

    val preferences = UserPreferencesDataSource(
        PreferenceDataStoreFactory.create(scope = dataStoreScope) { File(prefsDir, "test.preferences_pb") },
    )

    /** Plan-B Pro for the trash and activity history; off like a free user unless a test sets it. */
    @Volatile var pro: Boolean = false
    val history = DataHistory(db, time) { pro }

    val tasks = OfflineTaskRepository(db, db.taskDao(), db.tagDao(), db.searchDao(), time, reminders, history)
    val projects = OfflineProjectRepository(db, db.projectDao(), db.tagDao(), db.searchDao(), time, history)
    private val filesDir: File = Files.createTempDirectory("planb-files").toFile()

    /** Attachment files in a temporary folder; images are stored as they are ([CopyImageProcessor]). */
    val attachmentFiles = com.behnamjalali.planb.core.data.AttachmentFiles(filesDir)
    val notes = OfflineNoteRepository(db, db.noteDao(), db.noteDraftDao(), db.tagDao(), db.searchDao(), time, history, attachmentFiles = attachmentFiles)
    val attachments = com.behnamjalali.planb.core.data.repository.OfflineAttachmentRepository(db, attachmentFiles, CopyImageProcessor(), time)
    val habits = OfflineHabitRepository(db, db.habitDao(), db.searchDao(), time, reminders, history)
    val events = OfflineEventRepository(db, db.eventDao(), db.searchDao(), time, reminders, history)
    val trash = OfflineTrashRepository(tasks, notes, db.taskDao(), db.noteDao(), time)
    val activity = OfflineActivityRepository(db.activityLogDao())
    val focus = OfflineFocusRepository(db, db.focusDao(), time)
    val search = FtsSearchRepository(
        db.searchDao(), db.taskDao(), db.projectDao(), db.noteDao(), db.habitDao(), db.goalDao(), db.eventDao(), db.attachmentDao(),
    )
    val settings = DataStoreSettingsRepository(preferences)
    val planning = com.behnamjalali.planb.core.data.repository.OfflineTaskPlanningRepository(db, reminders)
    val smartLists = com.behnamjalali.planb.core.data.repository.OfflineSmartListRepository(db, db.taskDao(), time)

    fun close() {
        db.close()
        executor.shutdown()
        dataStoreScope.cancel()
        prefsDir.deleteRecursively()
        filesDir.deleteRecursively()
    }
}

/** Stores images unchanged and reports a fixed size (unit tests do not decode bitmaps). */
class CopyImageProcessor(private val width: Int = 640, private val height: Int = 480) : com.behnamjalali.planb.core.data.ImageProcessor {
    override fun store(source: File, target: File): com.behnamjalali.planb.core.data.StoredImage {
        if (source.length() == 0L) throw com.behnamjalali.planb.core.data.UnsupportedImageException()
        source.copyTo(target, overwrite = true)
        return com.behnamjalali.planb.core.data.StoredImage("image/jpeg", width, height)
    }
}
