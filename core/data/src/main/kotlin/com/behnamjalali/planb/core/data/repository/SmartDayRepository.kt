package com.behnamjalali.planb.core.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.toModel
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.dao.TaskDao
import com.behnamjalali.planb.core.database.entity.JournalEntryEntity
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.PlannedBlock
import com.behnamjalali.planb.core.model.PlannerIcon
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.Task
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Time blocks of tasks (`tasks.scheduled_start`/`scheduled_end`) written by "Plan my day"
 * and "Replan" (Plan-B Pro #5). Only the block changes; title, dates and reminders stay.
 */
interface DayPlanRepository {
    /** Open, live tasks (subtasks too) whose block overlaps [from, to), by start. */
    fun observeBlocks(from: Instant, to: Instant): Flow<List<Task>>

    /** Writes the blocks in one transaction; tasks that were completed or deleted meanwhile are skipped. Returns how many were written. */
    suspend fun applyBlocks(blocks: List<PlannedBlock>): Int

    /** Removes the block of each task. */
    suspend fun clearBlocks(taskIds: List<EntityId>)
}

@Singleton
class OfflineDayPlanRepository @Inject constructor(
    private val db: PlanBDatabase,
    private val taskDao: TaskDao,
    private val time: TimeProvider,
) : DayPlanRepository {
    override fun observeBlocks(from: Instant, to: Instant): Flow<List<Task>> {
        val sql = TaskDao.SELECT_WITH_COUNTS +
            " WHERE t.deleted_at IS NULL AND t.archived = 0 AND t.completed = 0 AND t.scheduled_start IS NOT NULL" +
            " AND t.scheduled_end IS NOT NULL AND t.scheduled_start < ? AND t.scheduled_end > ? ORDER BY t.scheduled_start, t.id"
        return taskDao.observeTasks(SimpleSQLiteQuery(sql, arrayOf(to.toEpochMilli(), from.toEpochMilli())))
            .map { rows -> rows.map { it.toModel() } }
    }

    override suspend fun applyBlocks(blocks: List<PlannedBlock>): Int {
        if (blocks.isEmpty()) return 0
        val now = time.now()
        return db.withTransaction {
            blocks.count { block ->
                require(block.end.isAfter(block.start)) { "A time block must end after it starts" }
                val entity = taskDao.getEntity(block.taskId)
                if (entity == null || entity.completed || entity.deletedAt != null) {
                    false
                } else {
                    taskDao.update(entity.copy(scheduledStart = block.start, scheduledEnd = block.end, updatedAt = now))
                    true
                }
            }
        }
    }

    override suspend fun clearBlocks(taskIds: List<EntityId>) {
        val now = time.now()
        db.withTransaction {
            taskIds.forEach { id ->
                val entity = taskDao.getEntity(id) ?: return@forEach
                if (entity.scheduledStart != null || entity.scheduledEnd != null) {
                    taskDao.update(entity.copy(scheduledStart = null, scheduledEnd = null, updatedAt = now))
                }
            }
        }
    }
}

/**
 * Ritual reflections (Plan-B Pro #8) stored where the daily journal (#25) reads them: the
 * journal page of a day is a regular note in a journal notebook, linked by a
 * `journal_entries` row (one per date). The morning intention and the evening reflection
 * are appended to that note, so nothing is stored twice and search, backup and export
 * already include them.
 */
interface RitualJournalRepository {
    /**
     * Appends a heading and a line of [text] to the journal page of [date], creating the
     * notebook [notebookTitle] (when no notebook has that title) and the page [pageTitle]
     * first when needed. [promptId] marks pages the rituals created. Returns the note id.
     */
    suspend fun append(date: LocalDate, heading: String, text: String, notebookTitle: String, pageTitle: String, promptId: String): EntityId

    /** The note holding the journal page of [date], if there is one. */
    suspend fun pageOf(date: LocalDate): EntityId?
}

@Singleton
class OfflineRitualJournalRepository @Inject constructor(
    private val db: PlanBDatabase,
    private val notes: NoteRepository,
    private val time: TimeProvider,
) : RitualJournalRepository {
    private val dao get() = db.journalDao()

    override suspend fun append(date: LocalDate, heading: String, text: String, notebookTitle: String, pageTitle: String, promptId: String): EntityId {
        require(text.isNotBlank()) { "A reflection must not be blank" }
        val blocks = listOf(
            NoteBlock(UUID.randomUUID().toString(), BlockType.HEADING, heading.trim()),
            NoteBlock(UUID.randomUUID().toString(), BlockType.TEXT, text.trim()),
        )
        val now = time.now()
        return db.withTransaction {
            val entry = dao.entryOn(date.toEpochDay())
            val page = entry?.let { notes.getNote(it.noteId) }
            // A locked page's body is encrypted: never append to (and so replace) it.
            check(page?.locked != true) { "The journal page of this day is locked" }
            if (page != null && page.deletedAt == null) {
                notes.saveNote(page.copy(document = page.document.copy(blocks = page.document.blocks + blocks)))
                dao.updateEntry(entry.copy(updatedAt = now))
                page.id
            } else {
                val notebook = notes.observeNotebooks(archived = false).first().firstOrNull { it.title.trim() == notebookTitle.trim() }?.id
                    ?: notes.saveNotebook(Notebook(title = notebookTitle, icon = PlannerIcon.BOOK, color = AccentColor.SAND))
                val id = notes.saveNote(Note(notebookId = notebook, title = pageTitle, document = NoteDocument(blocks = blocks)))
                if (entry != null) {
                    dao.updateEntry(entry.copy(noteId = id, updatedAt = now))
                } else {
                    dao.insertEntry(JournalEntryEntity(date = date, noteId = id, promptId = promptId, createdAt = now, updatedAt = now))
                }
                id
            }
        }
    }

    override suspend fun pageOf(date: LocalDate): EntityId? = dao.entryOn(date.toEpochDay())?.noteId
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SmartDayModule {
    @Binds abstract fun dayPlan(impl: OfflineDayPlanRepository): DayPlanRepository
    @Binds abstract fun ritualJournal(impl: OfflineRitualJournalRepository): RitualJournalRepository
}
