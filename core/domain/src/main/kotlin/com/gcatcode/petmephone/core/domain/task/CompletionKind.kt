package com.gcatcode.petmephone.core.domain.task

/**
 * Whether a completion satisfied the full [Behavior] or its [Minimum] (#98).
 *
 * ## Doing the minimum is a completion, full stop
 *
 * It keeps the chain, it feeds Hunger and Happiness, and it is not a lesser tick. There is no
 * reduced weight, no partial credit and no separate lower-value scoring path — the moment the
 * minimum is worth fewer points, the user stops reaching for it on exactly the day it was designed
 * for, and the chain breaks precisely when it mattered.
 *
 * That is why this is recorded **beside** the score rather than inside it. Nothing that computes a
 * metric may read this value; it exists so the number stays *interpretable* afterwards. A week made
 * entirely of minimums is a week that survived, and knowing that is more useful than a flat
 * percentage that cannot tell the difference.
 *
 * The same pattern as a retro-logged completion: marked, counted in full.
 */
enum class CompletionKind {
    /** The full behavior the user wrote. */
    FULL,

    /** The two-minute version. Worth exactly as much as [FULL] to every metric. */
    MINIMUM,
}
