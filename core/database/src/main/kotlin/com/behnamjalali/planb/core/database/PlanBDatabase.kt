package com.behnamjalali.planb.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.behnamjalali.planb.core.database.dao.BackupDao
import com.behnamjalali.planb.core.database.dao.EventDao
import com.behnamjalali.planb.core.database.dao.FocusDao
import com.behnamjalali.planb.core.database.dao.GoalDao
import com.behnamjalali.planb.core.database.dao.HabitDao
import com.behnamjalali.planb.core.database.dao.NoteDao
import com.behnamjalali.planb.core.database.dao.NoteDraftDao
import com.behnamjalali.planb.core.database.dao.ProjectDao
import com.behnamjalali.planb.core.database.dao.SearchDao
import com.behnamjalali.planb.core.database.dao.TagDao
import com.behnamjalali.planb.core.database.dao.TaskDao
import com.behnamjalali.planb.core.database.dao.TemplateDao
import com.behnamjalali.planb.core.database.entity.CalendarEventEntity
import com.behnamjalali.planb.core.database.entity.FocusSessionEntity
import com.behnamjalali.planb.core.database.entity.GoalEntity
import com.behnamjalali.planb.core.database.entity.GoalMilestoneEntity
import com.behnamjalali.planb.core.database.entity.HabitCompletionEntity
import com.behnamjalali.planb.core.database.entity.HabitEntity
import com.behnamjalali.planb.core.database.entity.NoteDraftEntity
import com.behnamjalali.planb.core.database.entity.NoteEntity
import com.behnamjalali.planb.core.database.entity.NoteTagCrossRef
import com.behnamjalali.planb.core.database.entity.NotebookEntity
import com.behnamjalali.planb.core.database.entity.NotebookSectionEntity
import com.behnamjalali.planb.core.database.entity.PlannerTemplateEntity
import com.behnamjalali.planb.core.database.entity.ProjectEntity
import com.behnamjalali.planb.core.database.entity.ProjectMilestoneEntity
import com.behnamjalali.planb.core.database.entity.ProjectTagCrossRef
import com.behnamjalali.planb.core.database.entity.SearchIndexEntity
import com.behnamjalali.planb.core.database.entity.TagEntity
import com.behnamjalali.planb.core.database.entity.TaskEntity
import com.behnamjalali.planb.core.database.entity.TaskTagCrossRef

@Database(
    version = PlanBDatabase.VERSION,
    exportSchema = true,
    entities = [
        TaskEntity::class,
        TagEntity::class,
        TaskTagCrossRef::class,
        ProjectEntity::class,
        ProjectTagCrossRef::class,
        ProjectMilestoneEntity::class,
        NotebookEntity::class,
        NotebookSectionEntity::class,
        NoteEntity::class,
        NoteTagCrossRef::class,
        HabitEntity::class,
        HabitCompletionEntity::class,
        GoalEntity::class,
        GoalMilestoneEntity::class,
        CalendarEventEntity::class,
        FocusSessionEntity::class,
        PlannerTemplateEntity::class,
        SearchIndexEntity::class,
        NoteDraftEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class PlanBDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun tagDao(): TagDao
    abstract fun projectDao(): ProjectDao
    abstract fun noteDao(): NoteDao
    abstract fun habitDao(): HabitDao
    abstract fun goalDao(): GoalDao
    abstract fun eventDao(): EventDao
    abstract fun focusDao(): FocusDao
    abstract fun templateDao(): TemplateDao
    abstract fun searchDao(): SearchDao
    abstract fun backupDao(): BackupDao
    abstract fun noteDraftDao(): NoteDraftDao

    companion object {
        const val VERSION = 2

        /** Internal file name; intentionally independent of the display name. */
        const val NAME = "planb.db"
    }
}
