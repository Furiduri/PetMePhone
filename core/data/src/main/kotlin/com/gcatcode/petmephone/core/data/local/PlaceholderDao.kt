package com.gcatcode.petmephone.core.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceholderDao {
    @Query("SELECT * FROM placeholder")
    fun getAll(): Flow<List<PlaceholderEntity>>
}
