package com.behnamjalali.planb.core.data.repository

import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.toModel
import com.behnamjalali.planb.core.database.dao.FocusDao
import com.behnamjalali.planb.core.database.dao.GoalDao
import com.behnamjalali.planb.core.database.dao.HabitDao
import com.behnamjalali.planb.core.database.dao.NoteDao
import com.behnamjalali.planb.core.database.dao.TaskDao
import com.behnamjalali.planb.core.model.HabitStats
import com.behnamjalali.planb.core.model.HabitWeekSummary
import com.behnamjalali.planb.core.model.ProjectStatus
import com.behnamjalali.planb.core.model.WeeklyReview
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

interface ReviewRepository {
    suspend fun weeklyReview(weekStart: LocalDate): WeeklyReview
}

@Singleton
internal class OfflineReviewRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val habitDao: HabitDao,
    private val focusDao: FocusDao,
    private val noteDao: NoteDao,
    private val goalDao: GoalDao,
    private val projects: ProjectRepository,
    private val time: TimeProvider,
) : ReviewRepository {
    override suspend fun weeklyReview(weekStart: LocalDate): WeeklyReview {
        val zone: ZoneId = time.zone()
        val weekEnd = weekStart.plusDays(6)
        val from = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = weekEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val today = time.today()

        val completed = taskDao.completedBetween(from, to)
        val perDay = (0L until 7L).map { offset ->
            val day = weekStart.plusDays(offset)
            completed.count { it.completedAt?.atZone(zone)?.toLocalDate() == day }
        }
        val missedUntil = minOf(today, weekEnd.plusDays(1))
        val missed = taskDao.openDueBetween(weekStart.toEpochDay(), missedUntil.toEpochDay()).map { it.toModel() }

        val reviewEnd = minOf(weekEnd, today)
        val completions = habitDao.completions(weekStart.toEpochDay(), weekEnd.toEpochDay()).groupBy { it.habitId }
        val habits = habitDao.activeHabits().map { it.toModel() }
            .filter { it.startDate <= weekEnd }
            .map { habit ->
                val amounts = completions[habit.id].orEmpty().associate { it.date to it.amount }
                val rate = if (reviewEnd < weekStart) 0f else HabitStats.completionRate(habit, amounts, weekStart, reviewEnd)
                HabitWeekSummary(habit, rate, amounts.count { (_, amount) -> amount >= habit.target })
            }

        val focusMinutes = (focusDao.focusedMillis(from, to) / 60_000L).toInt()
        val activeProjects = projects.observeProjects(archived = false).first().filter { it.project.status == ProjectStatus.ACTIVE }
        val notesCreated = noteDao.countCreatedBetween(from, to)
        val goals = goalDao.activeGoals().map { it.toModel() }
        val nextStart = weekEnd.plusDays(1)
        val priorities = taskDao.priorities(nextStart.toEpochDay(), nextStart.plusDays(6).toEpochDay(), 7).map { it.toModel() }

        return WeeklyReview(
            weekStart = weekStart,
            weekEnd = weekEnd,
            completedPerDay = perDay,
            missedTasks = missed,
            habits = habits,
            focusMinutes = focusMinutes,
            projects = activeProjects,
            notesCreated = notesCreated,
            goals = goals,
            nextWeekPriorities = priorities,
        )
    }
}
