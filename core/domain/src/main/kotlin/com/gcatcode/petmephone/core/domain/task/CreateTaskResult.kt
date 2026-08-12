package com.gcatcode.petmephone.core.domain.task

/** Outcome of [CreateOneOffTask] — measured values, as [TaskTitleResult] does. */
sealed interface CreateTaskResult {
    /** [hungerCapReached] signals whether this write also reached the day's task goal. */
    data class Created(val id: TaskId, val hungerCapReached: Boolean) : CreateTaskResult

    sealed interface Rejected : CreateTaskResult {
        data object BlankTitle : Rejected
        data class TitleTooLong(val length: Int, val maxLength: Int) : Rejected

        /**
         * The repository write threw. Not in design.md's literal interfaces block — added so
         * [CreateOneOffTask] can satisfy task 3.8 ("a simulated persistence failure returns a typed
         * failure result, not a thrown exception") without ever propagating the exception itself.
         */
        data object PersistenceFailure : Rejected
    }
}
