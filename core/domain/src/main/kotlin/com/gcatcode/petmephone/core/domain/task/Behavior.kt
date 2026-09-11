package com.gcatcode.petmephone.core.domain.task

/**
 * What the user actually does: a verb plus its object, concrete enough to execute (#98). "Read" is
 * a vague cue; "Read one page of X" is executable, and vague cues are the most-named cause of habit
 * failure.
 *
 * Shared by tasks and habits — it is one of the two fields both carry, which is what lets a single
 * creation form and a single row renderer serve both.
 *
 * Concreteness is NOT validated here. No rule distinguishes "Read" from "Read one page" without
 * parsing natural language, and a validator that guesses would reject honest input while passing
 * grammatical vagueness. The authoring flow teaches concreteness with an example (#100); this type
 * only guarantees that something non-blank of a sane length reached the domain.
 *
 * Only [of] can produce an instance. [MAX_LENGTH] is a domain validation constant, not a
 * [com.gcatcode.petmephone.core.domain.balance.BalanceConfig] field — #29 excludes UI/validation
 * caps from the balance object, the same reason [TaskTitle.MAX_LENGTH] lives on its own type.
 */
@JvmInline
value class Behavior private constructor(val value: String) {

    companion object {
        /** Matches [TaskTitle.MAX_LENGTH]: both are one line naming a thing to do. */
        const val MAX_LENGTH = 200

        /** Trims [raw], then rejects blank or over-[MAX_LENGTH] results. */
        fun of(raw: String): BehaviorResult {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty() -> BehaviorResult.Rejected.Blank
                trimmed.length > MAX_LENGTH ->
                    BehaviorResult.Rejected.TooLong(length = trimmed.length, maxLength = MAX_LENGTH)
                else -> BehaviorResult.Valid(Behavior(trimmed))
            }
        }
    }
}

/** Outcome of [Behavior.of] — measured values, as [TaskTitleResult] does. */
sealed interface BehaviorResult {
    data class Valid(val behavior: Behavior) : BehaviorResult

    sealed interface Rejected : BehaviorResult {
        data object Blank : Rejected
        data class TooLong(val length: Int, val maxLength: Int) : Rejected
    }
}
