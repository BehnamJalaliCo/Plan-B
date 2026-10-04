package com.behnamjalali.planb.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.behnamjalali.planb.core.database.entity.PlannerTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM planner_templates ORDER BY created_at DESC")
    fun observeAll(): Flow<List<PlannerTemplateEntity>>

    @Query("SELECT * FROM planner_templates WHERE id = :id")
    suspend fun get(id: Long): PlannerTemplateEntity?

    @Insert
    suspend fun insert(template: PlannerTemplateEntity): Long

    @Update
    suspend fun update(template: PlannerTemplateEntity)

    @Query("DELETE FROM planner_templates WHERE id = :id")
    suspend fun delete(id: Long)
}
