package com.gcatcode.petmephone.core.data.local.task

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.LocalDate

/**
 * No `@Update` and no `@Upsert` on this DAO (design decision 9, `task-persistence` spec): edits
 * are narrow column-naming queries only, none of which names `createdDate`, so no write path can
 * ever rewrite it. `@Insert(onConflict = ABORT)`, never `REPLACE` — `REPLACE` is delete-then-insert
 * and would regenerate the row.
 */
@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity): Long

    @Query("UPDATE Task SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE Task SET isActive = :isActive WHERE id = :id")
    suspend fun updateIsActive(id: Long, isActive: Boolean)

    @Query("SELECT COUNT(*) FROM Task WHERE createdDate = :date")
    suspend fun countCreatedOn(date: LocalDate): Int

    @Query("SELECT * FROM Task WHERE id = :id")
    suspend fun findById(id: Long): TaskEntity?

    @Query("DELETE FROM Task WHERE id = :id")
    suspend fun delete(id: Long)
}
