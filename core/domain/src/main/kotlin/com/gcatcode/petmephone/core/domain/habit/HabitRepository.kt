package com.gcatcode.petmephone.core.domain.habit

import com.gcatcode.petmephone.core.domain.task.Behavior
import com.gcatcode.petmephone.core.domain.task.Minimum
import java.time.Instant
import java.time.LocalDate

/**
 * The write port for habits, owned by the domain and implemented in `:core:data` — the same shape
 * as [com.gcatcode.petmephone.core.domain.task.TaskRepository].
 *
 * The port exists here so [CreateHabit] is a complete, testable use case without `:core:domain`
 * gaining any storage dependency, which #98 forbids outright. The implementation, the entities and
 * the anchor table are the persistence issue's work.
 */
interface HabitRepository {

    /**
     * Persists a new habit and returns its assigned identity. Takes the validated parts rather than
     * a [Habit], because the id is the store's to assign — the same reason
     * [com.gcatcode.petmephone.core.domain.task.TaskRepository.createOneOff] does.
     */
    suspend fun create(
        behavior: Behavior,
        minimum: Minimum,
        frequency: HabitFrequency,
        anchors: Anchors,
        identity: Identity?,
        createdAt: Instant,
        createdDate: LocalDate,
    ): HabitId
}
