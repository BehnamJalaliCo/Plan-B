package com.behnamjalali.planb.core.data.repository

import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.database.dao.NoteDao
import com.behnamjalali.planb.core.database.dao.TaskDao
import com.behnamjalali.planb.core.model.TrashItem
import com.behnamjalali.planb.core.model.TrashItemType
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** The 30-day trash of tasks and notes (Plan-B Pro #38). */
interface TrashRepository {
    /** Everything in the trash, most recently deleted first. Note bodies are never read. */
    fun observeTrash(): Flow<List<TrashItem>>

    /** Puts the item back where it was, searchable and with its reminders scheduled again. */
    suspend fun restore(item: TrashItem)

    /** Deletes the item permanently. */
    suspend fun deleteForever(item: TrashItem)

    /** Deletes everything in the trash permanently; returns how many items went. */
    suspend fun emptyTrash(): Int

    /** Deletes items that have been in the trash for [TrashItem.RETENTION_DAYS] days; returns the count. */
    suspend fun purgeExpired(): Int
}

@Singleton
class OfflineTrashRepository @Inject constructor(
    private val tasks: OfflineTaskRepository,
    private val notes: OfflineNoteRepository,
    private val taskDao: TaskDao,
    private val noteDao: NoteDao,
    private val time: TimeProvider,
) : TrashRepository {
    override fun observeTrash(): Flow<List<TrashItem>> =
        combine(taskDao.observeTrashRows(), noteDao.observeTrashRows()) { taskRows, noteRows ->
            val items = taskRows.map { TrashItem(TrashItemType.TASK, it.id, it.title, Instant.ofEpochMilli(it.deletedAt), childCount = it.childCount) } +
                noteRows.map { TrashItem(TrashItemType.NOTE, it.id, it.title, Instant.ofEpochMilli(it.deletedAt), locked = it.locked) }
            items.sortedWith(compareByDescending<TrashItem> { it.deletedAt }.thenByDescending { it.id })
        }

    override suspend fun restore(item: TrashItem) = when (item.type) {
        TrashItemType.TASK -> tasks.restoreFromTrash(item.id)
        TrashItemType.NOTE -> notes.restoreFromTrash(item.id)
    }

    override suspend fun deleteForever(item: TrashItem) = when (item.type) {
        // A task goes with the subtasks that were trashed with it (by cascade).
        TrashItemType.TASK -> tasks.deletePermanently(listOf(item.id))
        TrashItemType.NOTE -> notes.deleteNotePermanently(item.id)
    }

    override suspend fun emptyTrash(): Int {
        val items = observeTrash().first()
        purge(Instant.ofEpochMilli(Long.MAX_VALUE))
        return items.size
    }

    override suspend fun purgeExpired(): Int = purge(time.now().minus(Duration.ofDays(TrashItem.RETENTION_DAYS)))

    private suspend fun purge(before: Instant): Int {
        val taskIds = tasks.trashedBefore(before)
        val noteIds = notes.trashedBefore(before)
        taskIds.chunked(CHUNK).forEach { tasks.deletePermanently(it) }
        noteIds.forEach { notes.deleteNotePermanently(it) }
        return taskIds.size + noteIds.size
    }

    private companion object {
        const val CHUNK = 500
    }
}
