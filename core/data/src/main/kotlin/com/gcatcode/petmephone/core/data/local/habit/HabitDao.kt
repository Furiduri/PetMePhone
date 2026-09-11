package com.gcatcode.petmephone.core.data.local.habit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HabitDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(habit: HabitEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDays(days: List<HabitDayEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAnchors(anchors: List<HabitAnchorEntity>)

    @Query("SELECT * FROM Habit WHERE id = :id")
    suspend fun habit(id: Long): HabitEntity?

    @Query("SELECT * FROM HabitDay WHERE habitId = :habitId")
    suspend fun days(habitId: Long): List<HabitDayEntity>

    /** Ordered by [HabitAnchorEntity.position], so the user's own ordering survives the round trip. */
    @Query("SELECT * FROM HabitAnchor WHERE habitId = :habitId ORDER BY position ASC")
    suspend fun anchors(habitId: Long): List<HabitAnchorEntity>

    @Query("DELETE FROM Habit WHERE id = :id")
    suspend fun delete(id: Long)
}
