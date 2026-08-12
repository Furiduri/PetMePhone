package com.gcatcode.petmephone.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gcatcode.petmephone.core.data.local.task.TaskDao
import com.gcatcode.petmephone.core.data.local.task.TaskEntity
import com.gcatcode.petmephone.core.data.local.task.TaskOccurrenceDao
import com.gcatcode.petmephone.core.data.local.task.TaskOccurrenceEntity

/**
 * Version 2: `PlaceholderEntity`/`PlaceholderDao` retired, `Task`/`TaskOccurrence` added
 * (`task-persistence` spec, design decision 10). `fallbackToDestructiveMigration(dropAllTables =
 * true)` stays on the builder in [com.gcatcode.petmephone.core.data.di.DataModule] pre-release;
 * its removal is tracked in issue #74 (task 2.16) before first public release.
 */
@Database(
    entities = [TaskEntity::class, TaskOccurrenceEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(RoomTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    abstract fun taskOccurrenceDao(): TaskOccurrenceDao
}
