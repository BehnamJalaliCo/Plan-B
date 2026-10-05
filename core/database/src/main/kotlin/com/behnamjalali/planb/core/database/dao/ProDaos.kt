package com.behnamjalali.planb.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.behnamjalali.planb.core.database.entity.ActivityLogEntity
import com.behnamjalali.planb.core.database.entity.AttachmentEntity
import com.behnamjalali.planb.core.database.entity.BadgeEntity
import com.behnamjalali.planb.core.database.entity.CalendarLinkEntity
import com.behnamjalali.planb.core.database.entity.ChallengeEntity
import com.behnamjalali.planb.core.database.entity.JournalEntryEntity
import com.behnamjalali.planb.core.database.entity.MoodEntryEntity
import com.behnamjalali.planb.core.database.entity.NoteLinkEntity
import com.behnamjalali.planb.core.database.entity.NoteVersionEntity
import com.behnamjalali.planb.core.database.entity.SavedFilterEntity
import com.behnamjalali.planb.core.database.entity.TaskDependencyEntity
import com.behnamjalali.planb.core.database.entity.TaskReminderEntity
import kotlinx.coroutines.flow.Flow

/*
 * DAOs for the tables added in schema v3. Date and time parameters are raw canonical numbers
 * (epoch day, epoch ms), like every other DAO.
 */

@Dao
interface TaskReminderDao {
    @Query("SELECT * FROM task_reminders WHERE task_id = :taskId ORDER BY id")
    fun observeForTask(taskId: Long): Flow<List<TaskReminderEntity>>

    @Query("SELECT * FROM task_reminders WHERE task_id = :taskId ORDER BY id")
    suspend fun forTask(taskId: Long): List<TaskReminderEntity>

    /** Extra reminders of open, live tasks (for rescheduling after boot). */
    @Query(
        "SELECT r.* FROM task_reminders r JOIN tasks t ON t.id = r.task_id " +
            "WHERE t.completed = 0 AND t.archived = 0 AND t.deleted_at IS NULL ORDER BY r.task_id, r.id",
    )
    suspend fun activeReminders(): List<TaskReminderEntity>

    @Query("SELECT COUNT(*) FROM task_reminders WHERE task_id = :taskId")
    suspend fun count(taskId: Long): Int

    @Insert suspend fun insert(reminder: TaskReminderEntity): Long
    @Insert suspend fun insertAll(reminders: List<TaskReminderEntity>)
    @Update suspend fun update(reminder: TaskReminderEntity)

    @Query("DELETE FROM task_reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM task_reminders WHERE task_id = :taskId")
    suspend fun deleteForTask(taskId: Long)

    /** Replaces all extra reminders of [taskId]. */
    @Transaction
    suspend fun replace(taskId: Long, reminders: List<TaskReminderEntity>) {
        deleteForTask(taskId)
        insertAll(reminders.map { it.copy(id = 0, taskId = taskId) })
    }
}

@Dao
interface TaskDependencyDao {
    /** Tasks that [taskId] waits for. */
    @Query("SELECT depends_on_task_id FROM task_dependencies WHERE task_id = :taskId")
    fun observeDependencies(taskId: Long): Flow<List<Long>>

    /** Tasks waiting for [taskId]. */
    @Query("SELECT task_id FROM task_dependencies WHERE depends_on_task_id = :taskId")
    fun observeDependents(taskId: Long): Flow<List<Long>>

    @Query("SELECT * FROM task_dependencies")
    suspend fun all(): List<TaskDependencyEntity>

    /** Every dependency (Gantt connectors, cycle checks in the editor). */
    @Query("SELECT * FROM task_dependencies")
    fun observeAll(): Flow<List<TaskDependencyEntity>>

    /** Tasks that [taskId] waits for. */
    @Query("SELECT depends_on_task_id FROM task_dependencies WHERE task_id = :taskId")
    suspend fun dependencies(taskId: Long): List<Long>

    @Query("DELETE FROM task_dependencies WHERE task_id = :taskId")
    suspend fun deleteForTask(taskId: Long)

    /** Ids of open, live tasks that [taskId] still waits for. */
    @Query(
        "SELECT d.depends_on_task_id FROM task_dependencies d JOIN tasks t ON t.id = d.depends_on_task_id " +
            "WHERE d.task_id = :taskId AND t.completed = 0 AND t.deleted_at IS NULL",
    )
    suspend fun openBlockers(taskId: Long): List<Long>

    /** Callers must reject self-dependencies and cycles before inserting. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(dependency: TaskDependencyEntity)

    @Query("DELETE FROM task_dependencies WHERE task_id = :taskId AND depends_on_task_id = :dependsOnTaskId")
    suspend fun delete(taskId: Long, dependsOnTaskId: Long)
}

@Dao
interface SavedFilterDao {
    @Query("SELECT * FROM saved_filters ORDER BY sort_order, id")
    fun observeAll(): Flow<List<SavedFilterEntity>>

    @Query("SELECT * FROM saved_filters WHERE id = :id")
    suspend fun get(id: Long): SavedFilterEntity?

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM saved_filters")
    suspend fun maxSortOrder(): Long

    @Insert suspend fun insert(filter: SavedFilterEntity): Long
    @Update suspend fun update(filter: SavedFilterEntity)

    @Query("UPDATE saved_filters SET sort_order = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Long)

    @Query("DELETE FROM saved_filters WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface NoteVersionDao {
    /** Newest first; content is included, so keep [limit] small for lists. */
    @Query("SELECT * FROM note_versions WHERE note_id = :noteId ORDER BY created_at DESC, id DESC LIMIT :limit")
    fun observeForNote(noteId: Long, limit: Int = 100): Flow<List<NoteVersionEntity>>

    @Query("SELECT * FROM note_versions WHERE id = :id")
    suspend fun get(id: Long): NoteVersionEntity?

    @Insert suspend fun insert(version: NoteVersionEntity): Long

    @Query("DELETE FROM note_versions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM note_versions WHERE note_id = :noteId")
    suspend fun deleteForNote(noteId: Long)

    /** Keeps only the newest [keep] versions of [noteId]. */
    @Query(
        "DELETE FROM note_versions WHERE note_id = :noteId AND id NOT IN " +
            "(SELECT id FROM note_versions WHERE note_id = :noteId ORDER BY created_at DESC, id DESC LIMIT :keep)",
    )
    suspend fun prune(noteId: Long, keep: Int)
}

@Dao
interface NoteLinkDao {
    @Query("SELECT to_note_id FROM note_links WHERE from_note_id = :noteId")
    fun observeOutgoing(noteId: Long): Flow<List<Long>>

    /** Backlinks: live notes that link to [noteId]. */
    @Query(
        "SELECT l.from_note_id FROM note_links l JOIN notes n ON n.id = l.from_note_id " +
            "WHERE l.to_note_id = :noteId AND n.deleted_at IS NULL",
    )
    fun observeBacklinks(noteId: Long): Flow<List<Long>>

    /** Every link between live notes (graph view). */
    @Query(
        "SELECT l.* FROM note_links l JOIN notes a ON a.id = l.from_note_id JOIN notes b ON b.id = l.to_note_id " +
            "WHERE a.deleted_at IS NULL AND b.deleted_at IS NULL",
    )
    fun observeGraph(): Flow<List<NoteLinkEntity>>

    @Query("DELETE FROM note_links WHERE from_note_id = :noteId")
    suspend fun deleteOutgoing(noteId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(links: List<NoteLinkEntity>)

    /** Replaces the outgoing links of [noteId] (call after each save); self-links are dropped. */
    @Transaction
    suspend fun replaceOutgoing(noteId: Long, targets: Collection<Long>) {
        deleteOutgoing(noteId)
        insertAll(targets.filter { it != noteId }.distinct().map { NoteLinkEntity(noteId, it) })
    }
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE owner_type = :ownerType AND owner_id = :ownerId ORDER BY sort_order, id")
    fun observeForOwner(ownerType: String, ownerId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE owner_type = :ownerType AND owner_id = :ownerId ORDER BY sort_order, id")
    suspend fun forOwner(ownerType: String, ownerId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun get(id: Long): AttachmentEntity?

    @Query("SELECT file_name FROM attachments")
    suspend fun allFileNames(): List<String>

    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM attachments")
    fun observeTotalBytes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM attachments")
    suspend fun totalBytes(): Long

    @Query("SELECT COUNT(*) FROM attachments")
    suspend fun count(): Int

    /** Owners of a type that have attachments (the note attachment cleanup walks these). */
    @Query("SELECT DISTINCT owner_id FROM attachments WHERE owner_type = :ownerType")
    suspend fun ownerIds(ownerType: String): List<Long>

    @Insert suspend fun insert(attachment: AttachmentEntity): Long
    @Update suspend fun update(attachment: AttachmentEntity)

    @Query("UPDATE attachments SET ocr_text = :text WHERE id = :id")
    suspend fun setOcrText(id: Long, text: String?)

    @Query("UPDATE attachments SET transcript = :text WHERE id = :id")
    suspend fun setTranscript(id: Long, text: String?)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Removes rows whose owner no longer exists (owners are polymorphic, so no cascade).
     * Run it in every transaction that deletes tasks, notes, notebooks, events or mood
     * entries; the files themselves are removed by the attachment file sweep.
     */
    @Query(
        "DELETE FROM attachments WHERE " +
            "(owner_type = 'TASK' AND owner_id NOT IN (SELECT id FROM tasks)) OR " +
            "(owner_type = 'NOTE' AND owner_id NOT IN (SELECT id FROM notes)) OR " +
            "(owner_type = 'EVENT' AND owner_id NOT IN (SELECT id FROM calendar_events)) OR " +
            "(owner_type = 'MOOD' AND owner_id NOT IN (SELECT id FROM mood_entries))",
    )
    suspend fun deleteOrphans(): Int
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries WHERE date >= :from AND date <= :to ORDER BY date")
    fun observeEntries(from: Long, to: Long): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE date = :date")
    suspend fun entryOn(date: Long): JournalEntryEntity?

    @Insert suspend fun insertEntry(entry: JournalEntryEntity): Long
    @Update suspend fun updateEntry(entry: JournalEntryEntity)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("SELECT * FROM mood_entries WHERE date >= :from AND date <= :to ORDER BY date, time, id")
    fun observeMoods(from: Long, to: Long): Flow<List<MoodEntryEntity>>

    @Query("SELECT * FROM mood_entries WHERE id = :id")
    suspend fun getMood(id: Long): MoodEntryEntity?

    @Insert suspend fun insertMood(entry: MoodEntryEntity): Long
    @Update suspend fun updateMood(entry: MoodEntryEntity)

    @Query("DELETE FROM mood_entries WHERE id = :id")
    suspend fun deleteMood(id: Long)
}

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenges WHERE status = :status ORDER BY start_date DESC, id DESC")
    fun observeByStatus(status: String): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges ORDER BY start_date DESC, id DESC")
    fun observeAll(): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges WHERE id = :id")
    suspend fun get(id: Long): ChallengeEntity?

    @Insert suspend fun insert(challenge: ChallengeEntity): Long
    @Update suspend fun update(challenge: ChallengeEntity)

    @Query("DELETE FROM challenges WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM badges ORDER BY earned_at DESC")
    fun observeBadges(): Flow<List<BadgeEntity>>

    /** Returns -1 when the badge was already earned (the first award is kept). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun award(badge: BadgeEntity): Long
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_log ORDER BY at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_log WHERE entity_type = :entityType AND entity_id = :entityId ORDER BY at DESC, id DESC")
    fun observeForEntity(entityType: String, entityId: Long): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_log WHERE entity_type = :entityType ORDER BY at DESC, id DESC LIMIT :limit")
    fun observeRecentOfType(entityType: String, limit: Int): Flow<List<ActivityLogEntity>>

    /** The newest entry of one item (to merge a burst of edits into one entry). */
    @Query("SELECT * FROM activity_log WHERE entity_type = :entityType AND entity_id = :entityId ORDER BY at DESC, id DESC LIMIT 1")
    suspend fun latestFor(entityType: String, entityId: Long): ActivityLogEntity?

    @Insert suspend fun insert(entry: ActivityLogEntity): Long

    /** Moves an entry to [at] with a new [summary] (a later edit of the same item). */
    @Query("UPDATE activity_log SET at = :at, summary = :summary WHERE id = :id")
    suspend fun touch(id: Long, at: Long, summary: String)

    @Query("DELETE FROM activity_log WHERE entity_type = :entityType AND entity_id = :entityId")
    suspend fun deleteForEntity(entityType: String, entityId: Long)

    @Query("SELECT COUNT(*) FROM activity_log")
    suspend fun count(): Int

    /** Retention: keeps only the newest [keep] entries. */
    @Query("DELETE FROM activity_log WHERE id IN (SELECT id FROM activity_log ORDER BY at DESC, id DESC LIMIT -1 OFFSET :keep)")
    suspend fun pruneToNewest(keep: Int): Int

    /** Retention: drops entries older than [before] (epoch ms). */
    @Query("DELETE FROM activity_log WHERE at < :before")
    suspend fun deleteOlderThan(before: Long): Int
}

@Dao
interface CalendarLinkDao {
    @Query("SELECT * FROM calendar_links WHERE calendar_id = :calendarId")
    suspend fun forCalendar(calendarId: Long): List<CalendarLinkEntity>

    @Query("SELECT * FROM calendar_links WHERE local_type = :localType AND local_id = :localId")
    suspend fun forLocal(localType: String, localId: Long): CalendarLinkEntity?

    @Query("SELECT * FROM calendar_links WHERE calendar_id = :calendarId AND external_event_id = :externalEventId")
    suspend fun forExternal(calendarId: Long, externalEventId: Long): CalendarLinkEntity?

    @Query("SELECT * FROM calendar_links")
    fun observeAll(): Flow<List<CalendarLinkEntity>>

    @Upsert suspend fun upsert(link: CalendarLinkEntity): Long

    @Query("DELETE FROM calendar_links WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM calendar_links WHERE calendar_id = :calendarId")
    suspend fun deleteForCalendar(calendarId: Long)
}
