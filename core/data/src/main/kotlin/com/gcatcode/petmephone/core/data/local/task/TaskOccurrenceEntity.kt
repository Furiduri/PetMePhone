package com.gcatcode.petmephone.core.data.local.task

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gcatcode.petmephone.core.domain.task.CompletionKind
import java.time.Instant
import java.time.LocalDate

/**
 * Room entity backing [com.gcatcode.petmephone.core.domain.task.TaskOccurrence]
 * (`task-persistence` spec). Cascades on its parent [TaskEntity]'s delete, and rejects a second
 * insert for the same `(taskId, dueDate)` pair via the unique index.
 */
@Entity(
    tableName = "TaskOccurrence",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["taskId", "dueDate"], unique = true),
    ],
)
data class TaskOccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val dueDate: LocalDate,
    val originDate: LocalDate?,
    val points: Int,
    val isCompleted: Boolean,
    /**
     * Null until the occurrence is completed. Nullable rather than defaulted: a non-null kind on
     * something nobody has done would be a record of a completion that never happened.
     *
     * No scoring query reads this column — `CompletionKindNotScoredTest` enforces that.
     */
    val completionKind: CompletionKind? = null,
    val isCarriedOver: Boolean,
    val isMandatoryMakeup: Boolean,
    val createdAt: Instant,
)
