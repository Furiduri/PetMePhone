package com.gcatcode.petmephone.core.domain.habit

import java.time.DayOfWeek

/** Outcome of [CreateHabit] — measured values, as the task rejections carry. */
sealed interface CreateHabitResult {
    data class Created(val id: HabitId) : CreateHabitResult

    sealed interface Rejected : CreateHabitResult {
        data object BlankBehavior : Rejected
        data class BehaviorTooLong(val length: Int, val maxLength: Int) : Rejected

        data object BlankMinimum : Rejected
        data class MinimumTooLong(val length: Int, val maxLength: Int) : Rejected

        data class IdentityTooLong(val length: Int, val maxLength: Int) : Rejected

        /** A habit needs a cue; without one it is a task that repeats by nothing. */
        data object NoAnchors : Rejected

        /** A frequency covering no day describes a habit that can never be done. */
        data object NoDays : Rejected

        /**
         * #98's frequency refusal, carrying the anchor at fault and the days it cannot supply, so a
         * surface can name them rather than saying "incompatible".
         *
         * [anchorIndex] points into the list the caller passed, because two anchors of the same
         * kind are indistinguishable otherwise.
         */
        data class IncompatibleAnchor(
            val anchorIndex: Int,
            val anchor: Anchor,
            val missingDays: Set<DayOfWeek>,
        ) : Rejected

        /** The repository write threw, as [com.gcatcode.petmephone.core.domain.task.CreateTaskResult.Rejected.PersistenceFailure] does. */
        data object PersistenceFailure : Rejected
    }
}
