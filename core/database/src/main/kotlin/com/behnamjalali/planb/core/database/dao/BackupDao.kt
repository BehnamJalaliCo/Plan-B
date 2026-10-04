package com.behnamjalali.planb.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.behnamjalali.planb.core.database.entity.ActivityLogEntity
import com.behnamjalali.planb.core.database.entity.AttachmentEntity
import com.behnamjalali.planb.core.database.entity.BadgeEntity
import com.behnamjalali.planb.core.database.entity.CalendarEventEntity
import com.behnamjalali.planb.core.database.entity.CalendarLinkEntity
import com.behnamjalali.planb.core.database.entity.ChallengeEntity
import com.behnamjalali.planb.core.database.entity.JournalEntryEntity
import com.behnamjalali.planb.core.database.entity.MoodEntryEntity
import com.behnamjalali.planb.core.database.entity.NoteLinkEntity
import com.behnamjalali.planb.core.database.entity.NoteVersionEntity
import com.behnamjalali.planb.core.database.entity.SavedFilterEntity
import com.behnamjalali.planb.core.database.entity.TaskDependencyEntity
import com.behnamjalali.planb.core.database.entity.TaskReminderEntity
import com.behnamjalali.planb.core.database.entity.FocusSessionEntity
import com.behnamjalali.planb.core.database.entity.GoalEntity
import com.behnamjalali.planb.core.database.entity.GoalMilestoneEntity
import com.behnamjalali.planb.core.database.entity.HabitCompletionEntity
import com.behnamjalali.planb.core.database.entity.HabitEntity
import com.behnamjalali.planb.core.database.entity.NoteEntity
import com.behnamjalali.planb.core.database.entity.NoteTagCrossRef
import com.behnamjalali.planb.core.database.entity.NotebookEntity
import com.behnamjalali.planb.core.database.entity.NotebookSectionEntity
import com.behnamjalali.planb.core.database.entity.PlannerTemplateEntity
import com.behnamjalali.planb.core.database.entity.ProjectEntity
import com.behnamjalali.planb.core.database.entity.ProjectMilestoneEntity
import com.behnamjalali.planb.core.database.entity.ProjectTagCrossRef
import com.behnamjalali.planb.core.database.entity.TagEntity
import com.behnamjalali.planb.core.database.entity.TaskEntity
import com.behnamjalali.planb.core.database.entity.TaskTagCrossRef

/** Whole-table access used only by backup, restore and data management. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM tags") suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM projects") suspend fun projects(): List<ProjectEntity>
    @Query("SELECT * FROM project_tags") suspend fun projectTags(): List<ProjectTagCrossRef>
    @Query("SELECT * FROM project_milestones") suspend fun projectMilestones(): List<ProjectMilestoneEntity>

    /** Parents before children so foreign keys are satisfied on insert. */
    @Query("SELECT * FROM tasks ORDER BY parent_task_id IS NOT NULL, id") suspend fun tasks(): List<TaskEntity>
    @Query("SELECT * FROM task_tags") suspend fun taskTags(): List<TaskTagCrossRef>
    @Query("SELECT * FROM notebooks") suspend fun notebooks(): List<NotebookEntity>
    @Query("SELECT * FROM notebook_sections") suspend fun sections(): List<NotebookSectionEntity>
    @Query("SELECT * FROM notes") suspend fun notes(): List<NoteEntity>
    @Query("SELECT * FROM note_tags") suspend fun noteTags(): List<NoteTagCrossRef>
    @Query("SELECT * FROM habits") suspend fun habits(): List<HabitEntity>
    @Query("SELECT * FROM habit_completions") suspend fun habitCompletions(): List<HabitCompletionEntity>
    @Query("SELECT * FROM goals") suspend fun goals(): List<GoalEntity>
    @Query("SELECT * FROM goal_milestones") suspend fun goalMilestones(): List<GoalMilestoneEntity>
    @Query("SELECT * FROM calendar_events") suspend fun events(): List<CalendarEventEntity>
    @Query("SELECT * FROM focus_sessions") suspend fun focusSessions(): List<FocusSessionEntity>
    @Query("SELECT * FROM planner_templates") suspend fun templates(): List<PlannerTemplateEntity>

    // Schema v3 tables.
    @Query("SELECT * FROM task_reminders") suspend fun taskReminders(): List<TaskReminderEntity>
    @Query("SELECT * FROM task_dependencies") suspend fun taskDependencies(): List<TaskDependencyEntity>
    @Query("SELECT * FROM saved_filters") suspend fun savedFilters(): List<SavedFilterEntity>
    @Query("SELECT * FROM note_versions") suspend fun noteVersions(): List<NoteVersionEntity>
    @Query("SELECT * FROM note_links") suspend fun noteLinks(): List<NoteLinkEntity>
    @Query("SELECT * FROM attachments") suspend fun attachments(): List<AttachmentEntity>
    @Query("SELECT * FROM journal_entries") suspend fun journalEntries(): List<JournalEntryEntity>
    @Query("SELECT * FROM mood_entries") suspend fun moodEntries(): List<MoodEntryEntity>
    @Query("SELECT * FROM challenges") suspend fun challenges(): List<ChallengeEntity>
    @Query("SELECT * FROM badges") suspend fun badges(): List<BadgeEntity>
    @Query("SELECT * FROM activity_log") suspend fun activityLog(): List<ActivityLogEntity>
    @Query("SELECT * FROM calendar_links") suspend fun calendarLinks(): List<CalendarLinkEntity>

    @Insert suspend fun insertTags(items: List<TagEntity>)
    @Insert suspend fun insertProjects(items: List<ProjectEntity>)
    @Insert suspend fun insertProjectTags(items: List<ProjectTagCrossRef>)
    @Insert suspend fun insertProjectMilestones(items: List<ProjectMilestoneEntity>)
    @Insert suspend fun insertTasks(items: List<TaskEntity>)
    @Insert suspend fun insertTaskTags(items: List<TaskTagCrossRef>)
    @Insert suspend fun insertNotebooks(items: List<NotebookEntity>)
    @Insert suspend fun insertSections(items: List<NotebookSectionEntity>)
    @Insert suspend fun insertNotes(items: List<NoteEntity>)
    @Insert suspend fun insertNoteTags(items: List<NoteTagCrossRef>)
    @Insert suspend fun insertHabits(items: List<HabitEntity>)
    @Insert suspend fun insertHabitCompletions(items: List<HabitCompletionEntity>)
    @Insert suspend fun insertGoals(items: List<GoalEntity>)
    @Insert suspend fun insertGoalMilestones(items: List<GoalMilestoneEntity>)
    @Insert suspend fun insertEvents(items: List<CalendarEventEntity>)
    @Insert suspend fun insertFocusSessions(items: List<FocusSessionEntity>)
    @Insert suspend fun insertTemplates(items: List<PlannerTemplateEntity>)
    @Insert suspend fun insertTaskReminders(items: List<TaskReminderEntity>)
    @Insert suspend fun insertTaskDependencies(items: List<TaskDependencyEntity>)
    @Insert suspend fun insertSavedFilters(items: List<SavedFilterEntity>)
    @Insert suspend fun insertNoteVersions(items: List<NoteVersionEntity>)
    @Insert suspend fun insertNoteLinks(items: List<NoteLinkEntity>)
    @Insert suspend fun insertAttachments(items: List<AttachmentEntity>)
    @Insert suspend fun insertJournalEntries(items: List<JournalEntryEntity>)
    @Insert suspend fun insertMoodEntries(items: List<MoodEntryEntity>)
    @Insert suspend fun insertChallenges(items: List<ChallengeEntity>)
    @Insert suspend fun insertBadges(items: List<BadgeEntity>)
    @Insert suspend fun insertActivityLog(items: List<ActivityLogEntity>)
    @Insert suspend fun insertCalendarLinks(items: List<CalendarLinkEntity>)

    // Children first so foreign keys never block the wipe.
    @Query("DELETE FROM task_tags") suspend fun clearTaskTags()
    @Query("DELETE FROM project_tags") suspend fun clearProjectTags()
    @Query("DELETE FROM note_tags") suspend fun clearNoteTags()
    @Query("DELETE FROM focus_sessions") suspend fun clearFocusSessions()
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM project_milestones") suspend fun clearProjectMilestones()
    @Query("DELETE FROM goal_milestones") suspend fun clearGoalMilestones()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM projects") suspend fun clearProjects()
    @Query("DELETE FROM notes") suspend fun clearNotes()
    @Query("DELETE FROM notebook_sections") suspend fun clearSections()
    @Query("DELETE FROM notebooks") suspend fun clearNotebooks()
    @Query("DELETE FROM habit_completions") suspend fun clearHabitCompletions()
    @Query("DELETE FROM habits") suspend fun clearHabits()
    @Query("DELETE FROM calendar_events") suspend fun clearEvents()
    @Query("DELETE FROM planner_templates") suspend fun clearTemplates()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("DELETE FROM search_index") suspend fun clearSearchIndex()
    @Query("DELETE FROM task_reminders") suspend fun clearTaskReminders()
    @Query("DELETE FROM task_dependencies") suspend fun clearTaskDependencies()
    @Query("DELETE FROM saved_filters") suspend fun clearSavedFilters()
    @Query("DELETE FROM note_versions") suspend fun clearNoteVersions()
    @Query("DELETE FROM note_links") suspend fun clearNoteLinks()
    @Query("DELETE FROM note_drafts") suspend fun clearNoteDrafts()
    @Query("DELETE FROM attachments") suspend fun clearAttachments()
    @Query("DELETE FROM journal_entries") suspend fun clearJournalEntries()
    @Query("DELETE FROM mood_entries") suspend fun clearMoodEntries()
    @Query("DELETE FROM challenges") suspend fun clearChallenges()
    @Query("DELETE FROM badges") suspend fun clearBadges()
    @Query("DELETE FROM activity_log") suspend fun clearActivityLog()
    @Query("DELETE FROM calendar_links") suspend fun clearCalendarLinks()

    suspend fun clearAll() {
        clearTaskReminders(); clearTaskDependencies(); clearNoteLinks(); clearNoteVersions(); clearNoteDrafts()
        clearJournalEntries(); clearMoodEntries(); clearAttachments(); clearChallenges(); clearBadges()
        clearActivityLog(); clearCalendarLinks(); clearSavedFilters()
        clearTaskTags(); clearProjectTags(); clearNoteTags(); clearFocusSessions()
        clearTasks(); clearProjectMilestones(); clearGoalMilestones(); clearGoals(); clearProjects()
        clearNotes(); clearSections(); clearNotebooks(); clearHabitCompletions(); clearHabits()
        clearEvents(); clearTemplates(); clearTags(); clearSearchIndex()
    }
}
