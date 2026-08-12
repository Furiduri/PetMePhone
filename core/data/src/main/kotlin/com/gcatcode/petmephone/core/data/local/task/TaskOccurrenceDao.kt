package com.gcatcode.petmephone.core.data.local.task

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskOccurrenceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(occurrence: TaskOccurrenceEntity): Long

    @Query("SELECT * FROM TaskOccurrence WHERE dueDate = :date")
    fun occurrencesDueOn(date: LocalDate): Flow<List<TaskOccurrenceEntity>>
}
