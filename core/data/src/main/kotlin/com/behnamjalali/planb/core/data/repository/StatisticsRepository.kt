package com.behnamjalali.planb.core.data.repository

import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.toModel
import com.behnamjalali.planb.core.database.dao.HabitDao
import com.behnamjalali.planb.core.database.dao.StatisticsDao
import com.behnamjalali.planb.core.model.CompletedTaskRecord
import com.behnamjalali.planb.core.model.DateSpan
import com.behnamjalali.planb.core.model.DueTaskRecord
import com.behnamjalali.planb.core.model.FocusRecord
import com.behnamjalali.planb.core.model.HabitRecord
import com.behnamjalali.planb.core.model.PeriodStatistics
import com.behnamjalali.planb.core.model.StatisticsCalculator
import com.behnamjalali.planb.core.model.StatsInput
import java.time.DayOfWeek
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Plan-B Pro statistics and the yearly report (#31): read-only aggregation of local data. */
interface StatisticsRepository {
    /** Statistics for [span], with chart values per [buckets] (calendar-correct, see StatsPeriods). */
    suspend fun statistics(span: DateSpan, buckets: List<DateSpan>, weekStart: DayOfWeek): PeriodStatistics
}

@Singleton
internal class OfflineStatisticsRepository @Inject constructor(
    private val dao: StatisticsDao,
    private val habitDao: HabitDao,
    private val projects: ProjectRepository,
    private val time: TimeProvider,
) : StatisticsRepository {
    override suspend fun statistics(span: DateSpan, buckets: List<DateSpan>, weekStart: DayOfWeek): PeriodStatistics {
        val zone = time.zone()
        val from = span.start.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = span.end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        fun local(instant: java.time.Instant): LocalDateTime = LocalDateTime.ofInstant(instant, zone)

        val tagsByTask = dao.completedTaskTags(from, to).groupBy({ it.taskId }, { it.tagId })
        val completed = dao.completedTasks(from, to).map {
            CompletedTaskRecord(it.id, local(it.completedAt), it.dueDate, it.projectId, tagsByTask[it.id].orEmpty())
        }
        val due = dao.dueTasks(span.start.toEpochDay(), span.end.toEpochDay()).map { row ->
            DueTaskRecord(row.dueDate, if (row.completed) row.completedAt?.let { local(it).toLocalDate() } ?: row.dueDate else null)
        }
        val focus = dao.focusSessions(from, to).map { FocusRecord(local(it.startedAt), (it.actualDurationMillis / 60_000L).toInt()) }
        val completions = habitDao.completions(span.start.toEpochDay(), span.end.toEpochDay()).groupBy { it.habitId }
        val habits = habitDao.activeHabits().map { entity ->
            val habit = entity.toModel()
            HabitRecord(habit, completions[habit.id].orEmpty().associate { it.date to it.amount })
        }
        val input = StatsInput(
            span = span,
            buckets = buckets,
            completedTasks = completed,
            dueTasks = due,
            focus = focus,
            habits = habits,
            notesCreated = dao.notesCreated(from, to).map { local(it).toLocalDate() },
            projects = projects.observeProjects(archived = false).first(),
            tags = dao.tags().map { it.toModel() },
        )
        return StatisticsCalculator.compute(input, time.today(), weekStart)
    }
}
