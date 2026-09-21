package com.gcatcode.petmephone.core.data.local.draft

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftDao {
    /** REPLACE onto the fixed key: saving a draft overwrites the pending one, never adds a second. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftEntity)

    @Query("SELECT * FROM AuthoringDraft WHERE id = :id")
    fun observe(id: Int = DraftEntity.SINGLETON_ID): Flow<DraftEntity?>

    @Query("DELETE FROM AuthoringDraft")
    suspend fun clear()
}
