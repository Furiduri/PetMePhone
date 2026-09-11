package com.gcatcode.petmephone.core.domain.draft

import com.gcatcode.petmephone.core.domain.habit.Anchor
import java.time.Duration
import java.time.Instant

/**
 * A half-finished task or habit the user was authoring (#100).
 *
 * ## Why this is persisted rather than held in memory
 *
 * The requested behaviour was that an accidental outside tap must not lose progress. But the list
 * of things that destroy an in-memory draft is longer than that: the screen turning off, the
 * overlay service restarting, process death under memory pressure, an incoming call.
 *
 * A draft that survives only an outside tap and dies to a service restart is **worse than no
 * draft at all**, because by then the user has learned to trust it.
 *
 * ## Raw text, deliberately
 *
 * The fields are the strings the user typed, not [com.gcatcode.petmephone.core.domain.task.Behavior]
 * and [com.gcatcode.petmephone.core.domain.task.Minimum]. A draft is explicitly the state *before*
 * it is valid — storing validated types would make a half-typed field unstorable, which is the one
 * thing a draft exists to hold. Validation happens on submit, not on save.
 */
data class AuthoringDraft(
    val kind: DraftKind,
    val rawBehavior: String,
    val rawMinimum: String,
    /**
     * The cue, once the user has picked one. Null while they have not — a habit draft is allowed to
     * be incomplete; a habit is not.
     */
    val anchor: Anchor?,
    /** Which step the user was on, so resuming returns them there rather than to the beginning. */
    val step: Int,
    val createdAt: Instant,
    /** Bumped on every save. [isOfferable] measures staleness from this, not from [createdAt]. */
    val updatedAt: Instant,
) {
    /**
     * Whether this draft should still be offered back to the user.
     *
     * A two-week-old half-typed thought is no longer something they are trying to finish, and
     * surfacing it forever turns the pet from a glanceable status into a task they cannot escape.
     *
     * Measured from [updatedAt] rather than [createdAt]: a draft the user touched an hour ago is
     * live regardless of when they started it.
     *
     * Not offerable is **not** deleted. The draft stays exactly where it is — nothing here removes
     * it, and a silent deletion would be the app throwing away the user's words on a timer.
     */
    fun isOfferable(now: Instant, maxAge: Duration): Boolean =
        Duration.between(updatedAt, now) < maxAge

    /** True once there is anything worth resuming. A draft of pure whitespace is not progress. */
    fun hasContent(): Boolean =
        rawBehavior.isNotBlank() || rawMinimum.isNotBlank() || anchor != null
}

/** Whether the user was authoring a one-off task or a habit. They need different fields. */
enum class DraftKind {
    TASK,
    HABIT,
}
