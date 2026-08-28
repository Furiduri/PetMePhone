package com.gcatcode.petmephone.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gcatcode.petmephone.core.data.local.habit.HabitAnchorEntity
import com.gcatcode.petmephone.core.data.local.habit.HabitDao
import com.gcatcode.petmephone.core.data.local.habit.HabitDayEntity
import com.gcatcode.petmephone.core.data.local.habit.HabitEntity
import com.gcatcode.petmephone.core.data.local.task.TaskDao
import com.gcatcode.petmephone.core.data.local.task.TaskEntity
import com.gcatcode.petmephone.core.data.local.task.TaskOccurrenceDao
import com.gcatcode.petmephone.core.data.local.task.TaskOccurrenceEntity

/**
 * Version 4: `Habit`, `HabitDay` and `HabitAnchor` added (#128). The frequency and the cues each
 * get a table rather than a column, so neither is opaque to the database and the anchor's
 * reference to another habit is a real foreign key.
 *
 * Version 3: `TaskOccurrence.completionKind` added, recording whether a completion satisfied the
 * full behavior or its minimum (#98). Nullable, and read by nothing that computes a metric.
 *
 * Version 2: `PlaceholderEntity`/`PlaceholderDao` retired, `Task`/`TaskOccurrence` added
 * (`task-persistence` spec, design decision 10). `fallbackToDestructiveMigration(dropAllTables =
 * true)` stays on the builder in [com.gcatcode.petmephone.core.data.di.DataModule] pre-release;
 * its removal is tracked in issue #74 (task 2.16) before first public release.
 */
@Database(
    entities = [
        TaskEntity::class,
        TaskOccurrenceEntity::class,
        HabitEntity::class,
        HabitDayEntity::class,
        HabitAnchorEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(RoomTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    abstract fun taskOccurrenceDao(): TaskOccurrenceDao

    abstract fun habitDao(): HabitDao
}
