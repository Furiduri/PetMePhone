package com.gcatcode.petmephone.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PlaceholderEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun placeholderDao(): PlaceholderDao
}
