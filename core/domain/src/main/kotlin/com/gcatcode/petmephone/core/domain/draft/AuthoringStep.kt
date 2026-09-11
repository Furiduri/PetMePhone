package com.gcatcode.petmephone.core.domain.draft

/**
 * The fields the overlay collects, one per card (#100).
 *
 * ## Only what is required
 *
 * A task needs a behavior and a minimum. A habit needs those plus a cue. Identity and precise
 * scheduling are deliberately absent: building a five-step wizard into a 280dp card is how this
 * becomes unusable, and the full app already exists for the longer conversation.
 *
 * ## Why an ordered list rather than a step number
 *
 * The step the user is on is an index into [stepsFor], so "which field am I editing" and "how many
 * are left" are the same fact. A bare integer would let the two disagree — a stored step 3 on a
 * task draft, which has only two steps, is a state nobody can render.
 * [AuthoringFlow.stepAt] refuses that instead of guessing.
 */
enum class AuthoringStep {
    BEHAVIOR,
    MINIMUM,
    ANCHOR,
}

/**
 * The ordered steps for a draft of this [DraftKind], and the arithmetic of moving through them.
 *
 * Deliberately a pure function of the kind: nothing here reads a draft's contents, so the flow's
 * shape cannot drift as the user types.
 */
object AuthoringFlow {

    /** A task stops at the minimum; a habit needs a cue before it is a habit at all. */
    fun stepsFor(kind: DraftKind): List<AuthoringStep> = when (kind) {
        DraftKind.TASK -> listOf(AuthoringStep.BEHAVIOR, AuthoringStep.MINIMUM)
        DraftKind.HABIT -> listOf(AuthoringStep.BEHAVIOR, AuthoringStep.MINIMUM, AuthoringStep.ANCHOR)
    }

    fun stepCount(kind: DraftKind): Int = stepsFor(kind).size

    /**
     * The step at [index], or `null` when the index does not name one.
     *
     * Null rather than a clamp: an out-of-range index means the stored draft and the flow disagree,
     * and quietly showing the first step would drop the user somewhere they did not leave off.
     * The caller decides what to do — and can tell the user — instead of the arithmetic pretending.
     */
    fun stepAt(kind: DraftKind, index: Int): AuthoringStep? = stepsFor(kind).getOrNull(index)

    /** True when [index] is the last step, which is where submit replaces next. */
    fun isLastStep(kind: DraftKind, index: Int): Boolean = index == stepCount(kind) - 1

    /**
     * The index after [index], or `null` when there is no next step.
     *
     * Null is the signal to submit rather than advance, so no caller has to re-derive the end of
     * the flow from a count and get the off-by-one wrong.
     */
    fun nextIndex(kind: DraftKind, index: Int): Int? =
        (index + 1).takeIf { it < stepCount(kind) }

    /** The index before [index], or `null` at the first step, where back leaves the form entirely. */
    fun previousIndex(index: Int): Int? = (index - 1).takeIf { it >= 0 }
}
