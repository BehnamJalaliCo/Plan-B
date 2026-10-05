package com.behnamjalali.planb.core.data

import com.behnamjalali.planb.core.data.repository.DataStoreSettingsRepository
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.data.repository.FocusRepository
import com.behnamjalali.planb.core.data.repository.FtsSearchRepository
import com.behnamjalali.planb.core.data.repository.GoalRepository
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.NoteRepository
import com.behnamjalali.planb.core.data.repository.OfflineEventRepository
import com.behnamjalali.planb.core.data.repository.OfflineFocusRepository
import com.behnamjalali.planb.core.data.repository.OfflineGoalRepository
import com.behnamjalali.planb.core.data.repository.OfflineHabitRepository
import com.behnamjalali.planb.core.data.repository.OfflineNoteRepository
import com.behnamjalali.planb.core.data.repository.OfflineProjectRepository
import com.behnamjalali.planb.core.data.repository.OfflineReviewRepository
import com.behnamjalali.planb.core.data.repository.OfflineTaskRepository
import com.behnamjalali.planb.core.data.repository.OfflineTemplateRepository
import com.behnamjalali.planb.core.data.repository.ProjectRepository
import com.behnamjalali.planb.core.data.repository.ReviewRepository
import com.behnamjalali.planb.core.data.repository.SearchRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.OfflineStatisticsRepository
import com.behnamjalali.planb.core.data.repository.StatisticsRepository
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.data.repository.TemplateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds abstract fun tasks(impl: OfflineTaskRepository): TaskRepository
    @Binds abstract fun projects(impl: OfflineProjectRepository): ProjectRepository
    @Binds abstract fun notes(impl: OfflineNoteRepository): NoteRepository
    @Binds abstract fun habits(impl: OfflineHabitRepository): HabitRepository
    @Binds abstract fun goals(impl: OfflineGoalRepository): GoalRepository
    @Binds abstract fun events(impl: OfflineEventRepository): EventRepository
    @Binds abstract fun focus(impl: OfflineFocusRepository): FocusRepository
    @Binds abstract fun templates(impl: OfflineTemplateRepository): TemplateRepository
    @Binds abstract fun search(impl: FtsSearchRepository): SearchRepository
    @Binds abstract fun review(impl: OfflineReviewRepository): ReviewRepository
    @Binds abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository
    @Binds abstract fun trash(impl: com.behnamjalali.planb.core.data.repository.OfflineTrashRepository): com.behnamjalali.planb.core.data.repository.TrashRepository
    @Binds abstract fun activity(impl: com.behnamjalali.planb.core.data.repository.OfflineActivityRepository): com.behnamjalali.planb.core.data.repository.ActivityRepository
    @Binds abstract fun statistics(impl: OfflineStatisticsRepository): StatisticsRepository
    @Binds abstract fun planning(impl: com.behnamjalali.planb.core.data.repository.OfflineTaskPlanningRepository): com.behnamjalali.planb.core.data.repository.TaskPlanningRepository
    @Binds abstract fun smartLists(impl: com.behnamjalali.planb.core.data.repository.OfflineSmartListRepository): com.behnamjalali.planb.core.data.repository.SmartListRepository
}
