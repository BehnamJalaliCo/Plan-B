package com.behnamjalali.planb.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.behnamjalali.planb.core.database.entity.GoalEntity
import com.behnamjalali.planb.core.database.entity.GoalMilestoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE archived = :archived ORDER BY deadline IS NULL, deadline, id")
    fun observeGoals(archived: Boolean): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    fun observeGoal(id: Long): Flow<GoalEntity?>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoal(id: Long): GoalEntity?

    @Query("SELECT * FROM goals WHERE archived = 0")
    suspend fun activeGoals(): List<GoalEntity>

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM goal_milestones WHERE goal_id = :goalId ORDER BY sort_order, id")
    fun observeMilestones(goalId: Long): Flow<List<GoalMilestoneEntity>>

    @Insert
    suspend fun insertMilestone(milestone: GoalMilestoneEntity): Long

    @Update
    suspend fun updateMilestone(milestone: GoalMilestoneEntity)

    @Query("DELETE FROM goal_milestones WHERE id = :id")
    suspend fun deleteMilestone(id: Long)

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM goal_milestones WHERE goal_id = :goalId")
    suspend fun maxMilestoneOrder(goalId: Long): Long
}
