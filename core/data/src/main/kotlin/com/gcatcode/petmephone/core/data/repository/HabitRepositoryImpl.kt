package com.gcatcode.petmephone.core.data.repository

import androidx.room.withTransaction
import com.gcatcode.petmephone.core.data.local.AppDatabase
import com.gcatcode.petmephone.core.data.local.habit.HabitDao
import com.gcatcode.petmephone.core.data.local.habit.HabitDayEntity
import com.gcatcode.petmephone.core.data.local.habit.HabitEntity
import com.gcatcode.petmephone.core.data.local.habit.HabitReadResult
import com.gcatcode.petmephone.core.data.local.habit.buildHabit
import com.gcatcode.petmephone.core.data.local.habit.toEntity
import com.gcatcode.petmephone.core.domain.habit.Anchors
import com.gcatcode.petmephone.core.domain.habit.Habit
import com.gcatcode.petmephone.core.domain.habit.HabitFrequency
import com.gcatcode.petmephone.core.domain.habit.HabitId
import com.gcatcode.petmephone.core.domain.habit.HabitRepository
import com.gcatcode.petmephone.core.domain.habit.Identity
import com.gcatcode.petmephone.core.domain.task.Behavior
import com.gcatcode.petmephone.core.domain.task.Minimum
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Room-backed [HabitRepository] (#128).
 *
 * [create] writes the habit, its days and its cues inside one transaction, so a habit never exists
 * without the rows that make it one — the same guarantee [TaskRepositoryImpl] gives a task and its
 * first occurrence.
 */
class HabitRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val habitDao: HabitDao,
) : HabitRepository {

    override suspend fun create(
        behavior: Behavior,
        minimum: Minimum,
        frequency: HabitFrequency,
        anchors: Anchors,
        identity: Identity?,
        createdAt: Instant,
        createdDate: LocalDate,
    ): HabitId = database.withTransaction {
        val habitId = habitDao.insert(
            HabitEntity(
                behavior = behavior.value,
                minimum = minimum.value,
                identity = identity?.value,
                createdAt = createdAt,
                createdDate = createdDate,
                isActive = true,
            ),
        )
        habitDao.insertDays(
            frequency.occursOn.map { day -> HabitDayEntity(habitId = habitId, dayOfWeek = day) },
        )
        habitDao.insertAnchors(
            anchors.values.mapIndexed { position, anchor -> anchor.toEntity(habitId, position) },
        )
        HabitId(habitId)
    }

    override suspend fun habitById(id: HabitId): Habit? {
        val entity = habitDao.habit(id.value) ?: return null
        val anchorRows = habitDao.anchors(id.value)

        // An AFTER_HABIT anchor's frequency is DERIVED from the habit it points at, never stored a
        // second time on the anchor row — two copies would be free to drift, which is the same
        // duplicated-default defect the balance config had. Resolved up front because the mapper
        // itself is pure.
        val referencedDays = anchorRows
            .mapNotNull { it.anchorHabitId }
            .distinct()
            .associateWith { referencedId -> habitDao.days(referencedId).map { it.dayOfWeek }.toSet() }

        val result = buildHabit(
            entity = entity,
            dayRows = habitDao.days(id.value),
            anchorRows = anchorRows,
            anchorHabitDays = { referencedId -> referencedDays[referencedId]?.takeIf { it.isNotEmpty() } },
        )
        return when (result) {
            is HabitReadResult.Valid -> result.habit
            is HabitReadResult.Malformed -> null
        }
    }
}
