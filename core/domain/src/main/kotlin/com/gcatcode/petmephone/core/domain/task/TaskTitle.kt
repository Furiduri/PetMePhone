package com.gcatcode.petmephone.core.domain.task

/**
 * A task's title, trimmed and validated (`task-creation` spec). Only [of] can produce an instance:
 * a blank (post-trim) or over-length raw string never reaches [Task]. `MAX_LENGTH` is a domain
 * validation constant, not a [com.gcatcode.petmephone.core.domain.balance.BalanceConfig] field —
 * #29 explicitly excludes UI/validation caps from the balance object.
 */
@JvmInline
value class TaskTitle private constructor(val value: String) {

    companion object {
        const val MAX_LENGTH = 200

        /** Trims [raw], then rejects blank or over-[MAX_LENGTH] results. */
        fun of(raw: String): TaskTitleResult {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty() -> TaskTitleResult.Rejected.Blank
                trimmed.length > MAX_LENGTH ->
                    TaskTitleResult.Rejected.TooLong(length = trimmed.length, maxLength = MAX_LENGTH)
                else -> TaskTitleResult.Valid(TaskTitle(trimmed))
            }
        }
    }
}

/** Outcome of [TaskTitle.of] — measured values, as [com.gcatcode.petmephone.core.domain.character.CharacterImportRejection] does. */
sealed interface TaskTitleResult {
    data class Valid(val title: TaskTitle) : TaskTitleResult

    sealed interface Rejected : TaskTitleResult {
        data object Blank : Rejected
        data class TooLong(val length: Int, val maxLength: Int) : Rejected
    }
}
