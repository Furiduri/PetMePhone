package com.gcatcode.petmephone.core.data.local.task

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * Room entity backing [com.gcatcode.petmephone.core.domain.task.Task] (`task-persistence` spec).
 *
 * [createdDate] is structurally immutable after insert (design decision 9): [TaskDao] declares no
 * `@Update`/`@Upsert` on this entity, so no whole-row rewrite path exists that could regenerate it.
 */
@Entity(tableName = "Task")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val rrule: String?,
    val createdAt: Instant,
    val createdDate: LocalDate,
    val isActive: Boolean,
)
