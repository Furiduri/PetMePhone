package com.gcatcode.petmephone.core.data.local.task

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gcatcode.petmephone.core.domain.task.CompletionKind
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskOccurrenceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(occurrence: TaskOccurrenceEntity): Long

    @Query("SELECT * FROM TaskOccurrence WHERE dueDate = :date")
    fun occurrencesDueOn(date: LocalDate): Flow<List<TaskOccurrenceEntity>>

    /**
     * Records a completion and how it was reached. [points] is deliberately absent from the SET
     * clause: a minimum completion is worth exactly what a full one is, and the moment this query
     * touches the score that stops being true (#98).
     */
    @Query("UPDATE TaskOccurrence SET isCompleted = 1, completionKind = :kind WHERE id = :id")
    suspend fun markCompleted(id: Long, kind: CompletionKind)
}
