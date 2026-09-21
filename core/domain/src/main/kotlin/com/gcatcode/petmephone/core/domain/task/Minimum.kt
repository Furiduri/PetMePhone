package com.gcatcode.petmephone.core.domain.task

/**
 * The two-minute version of a [Behavior]: what counts on a bad day (#98). It is what keeps the
 * chain alive when capacity collapses — "Clean the kitchen" against "Put three dishes in the sink".
 *
 * Shared by tasks and habits, and **required on both**. An optional minimum is an empty minimum:
 * optional fields go unfilled, and the presentation that depends on this one then collapses back to
 * showing the heavy version of every task, which is the postponement problem it exists to remove
 * (#98, #99). Requiredness is expressed by this type having no absent case at all — a caller cannot
 * construct one from nothing, so no downstream reader has to handle a null.
 *
 * Completing the minimum is a full completion. It keeps the chain and it feeds Hunger and Happiness
 * identically to the full behavior; nothing here or downstream may weight it lower, because the
 * moment it is worth less the user stops reaching for it on exactly the day it was designed for.
 * The completion kind is recorded elsewhere, beside the score and never inside it.
 *
 * Only [of] can produce an instance. See [Behavior] for why [MAX_LENGTH] is a domain constant
 * rather than a balance value.
 */
@JvmInline
value class Minimum private constructor(val value: String) {

    companion object {
        /** Matches [Behavior.MAX_LENGTH]: a minimum is a behavior, just a smaller one. */
        const val MAX_LENGTH = 200

        /** Trims [raw], then rejects blank or over-[MAX_LENGTH] results. */
        fun of(raw: String): MinimumResult {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty() -> MinimumResult.Rejected.Blank
                trimmed.length > MAX_LENGTH ->
                    MinimumResult.Rejected.TooLong(length = trimmed.length, maxLength = MAX_LENGTH)
                else -> MinimumResult.Valid(Minimum(trimmed))
            }
        }
    }
}

/** Outcome of [Minimum.of], carrying measured values rather than a bare failure. */
sealed interface MinimumResult {
    data class Valid(val minimum: Minimum) : MinimumResult

    sealed interface Rejected : MinimumResult {
        data object Blank : Rejected
        data class TooLong(val length: Int, val maxLength: Int) : Rejected
    }
}
