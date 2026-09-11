package com.gcatcode.petmephone.core.domain.habit

/**
 * The kind of person a habit votes for, in the user's own words — "I am someone who reads" (#98).
 * One line, chosen at creation and shown at completion.
 *
 * **Habits only, and by type rather than by convention.** A one-off errand does not vote for an
 * identity, and asking as though it did makes the question meaningless by repetition. There is no
 * identity field on a task, so no surface can accidentally ask for one.
 *
 * ## Optional, and absence is null rather than an empty line
 *
 * Unlike [Minimum], identity is not required — a user who does not want to answer should not be
 * blocked from creating a habit. But an unanswered identity is *absent*, never a blank
 * [Identity]: an empty instance would render as an empty line at completion, which reads as the app
 * having lost the answer rather than the user never having given one. [of] refuses blank input so
 * that the only way to express "not answered" is a null [Identity].
 */
@JvmInline
value class Identity private constructor(val value: String) {

    companion object {
        /**
         * Longer than a [Behavior], because this is a sentence rather than a label — but still one
         * line. A paragraph here is a journal entry, and nothing shows it at completion.
         */
        const val MAX_LENGTH = 280

        /** Trims [raw], then rejects blank or over-[MAX_LENGTH] results. */
        fun of(raw: String): IdentityResult {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty() -> IdentityResult.Rejected.Blank
                trimmed.length > MAX_LENGTH ->
                    IdentityResult.Rejected.TooLong(length = trimmed.length, maxLength = MAX_LENGTH)
                else -> IdentityResult.Valid(Identity(trimmed))
            }
        }

        /**
         * The authoring seam: blank or absent input becomes `null` rather than a rejection, since
         * skipping the question is allowed. Anything the user actually typed is still validated.
         */
        fun ofOptional(raw: String?): IdentityResult =
            if (raw.isNullOrBlank()) IdentityResult.Absent else of(raw)
    }
}

/** Outcome of [Identity.of] — measured values, as the other rejections carry. */
sealed interface IdentityResult {
    data class Valid(val identity: Identity) : IdentityResult

    /** The user skipped the question. Distinct from every [Rejected] case: nothing went wrong. */
    data object Absent : IdentityResult

    sealed interface Rejected : IdentityResult {
        data object Blank : Rejected
        data class TooLong(val length: Int, val maxLength: Int) : Rejected
    }
}
