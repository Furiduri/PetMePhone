package com.gcatcode.petmephone.core.domain.habit

/**
 * A habit's cues: **at least one, always** (#98). A habit without an anchor is a task that repeats
 * by nothing, which is the distinction this whole model exists to draw.
 *
 * The requirement lives in a type rather than in a check inside a use case, so every future reader
 * — the creation flow, the persistence mapper in particular — inherits it. A mapper reconstructing
 * a habit from a corrupt row cannot produce an anchorless one by accident; it has to handle
 * [AnchorsResult.Rejected.Empty] and say what it wants to do about it.
 *
 * Order is preserved. It is the order the user chose, and nothing here re-sorts by
 * [AnchorKind] strength — that ordering is for *offering* types, not for ranking cues a user has
 * already committed to.
 */
class Anchors private constructor(val values: List<Anchor>) : Iterable<Anchor> {

    override fun iterator(): Iterator<Anchor> = values.iterator()

    val size: Int get() = values.size

    override fun equals(other: Any?): Boolean = other is Anchors && other.values == values

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "Anchors($values)"

    companion object {
        /** Rejects an empty list; copies the input so a caller's later mutation cannot empty it. */
        fun of(anchors: List<Anchor>): AnchorsResult =
            if (anchors.isEmpty()) {
                AnchorsResult.Rejected.Empty
            } else {
                AnchorsResult.Valid(Anchors(anchors.toList()))
            }

        /** Convenience for the common single-cue case. */
        fun of(anchor: Anchor): AnchorsResult = of(listOf(anchor))
    }
}

/** Outcome of [Anchors.of]. */
sealed interface AnchorsResult {
    data class Valid(val anchors: Anchors) : AnchorsResult

    sealed interface Rejected : AnchorsResult {
        /** A habit needs a cue. Without one it cannot fire, and it is not a habit. */
        data object Empty : Rejected
    }
}
