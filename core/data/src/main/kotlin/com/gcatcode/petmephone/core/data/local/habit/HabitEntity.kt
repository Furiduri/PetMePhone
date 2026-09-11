package com.gcatcode.petmephone.core.data.local.habit

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Room entity backing [com.gcatcode.petmephone.core.domain.habit.Habit] (#128).
 *
 * The habit's own row carries only what is single-valued. Its frequency lives in [HabitDayEntity]
 * and its cues in [HabitAnchorEntity], because both are sets and neither belongs in a column.
 *
 * [createdDate] is the day the *user* was living when they created it, already resolved in
 * `:core:domain` by `AppDay` — this layer never recomputes a date.
 */
@Entity(tableName = "Habit")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val behavior: String,
    val minimum: String,
    /** Null when the user skipped the question. Never an empty string. */
    val identity: String?,
    val createdAt: Instant,
    val createdDate: LocalDate,
    val isActive: Boolean,
)

/**
 * One weekday a habit needs a cue on — the stored form of
 * [com.gcatcode.petmephone.core.domain.habit.HabitFrequency].
 *
 * A table rather than a joined string for the same reason the anchors get one: "which habits need a
 * cue on Tuesday?" is a question the database should be able to answer. A comma-separated column
 * makes every such question a `LIKE` against text, and text that no constraint can keep valid.
 *
 * The unique index makes a duplicated day impossible, which is what lets the read side treat these
 * rows as the set they represent.
 */
@Entity(
    tableName = "HabitDay",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["habitId", "dayOfWeek"], unique = true)],
)
data class HabitDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val dayOfWeek: DayOfWeek,
)
