package com.petmephone.spike.imeviability

/**
 * One repositioning of the pet window, caused by one measured displacement of the card window's own
 * content that was observed on two consecutive samples.
 *
 * [measuredDisplacementPx] is the displacement that was actually read off the card. It is never a
 * default, never a constant, and never an estimate: if no displacement could be read, no move is
 * recorded here because no move was made.
 */
data class PetFollowMove(
    val elapsedMillisSinceWindowAdded: Long,
    val measuredDisplacementPx: Int,
    val movedToYPx: Int,
)

/**
 * What the run is entitled to say about deriving a keyboard height from the card window.
 *
 * The four "no height" outcomes are deliberately distinct values rather than one falsy result. "The
 * content top was never readable", "the content never moved", "the content moved but no displacement
 * was ever seen twice in a row", and "the pet window was never added at all" are four different facts
 * about the device, and collapsing them would reproduce exactly the failure that invalidated the
 * previous instrument's coverage reading.
 */
sealed interface PetFollowOutcome {

    /** The mode under test adds no pet window, so there was nothing to follow the keyboard. */
    data object ModeAddsNoPetWindow : PetFollowOutcome

    /** A pet window exists, but the card's content top was never readable even once. */
    data object ContentTopNeverReadable : PetFollowOutcome

    /**
     * The card's content top was read repeatedly and never moved up from its baseline. The keyboard
     * height was therefore never measurable, and the pet was correctly left where it was — this is a
     * measurement, not a failure, and explicitly not a displacement of zero pixels.
     */
    data object NoDisplacementEverObserved : PetFollowOutcome

    /**
     * Displacements were observed, but none was ever reported by two consecutive samples, so none
     * was ever acted on. Every one of them is still listed in the observations table.
     */
    data object DisplacementsObservedButNoneAgreed : PetFollowOutcome

    /**
     * At least one displacement was confirmed by two consecutive samples; [maxDisplacementPx] is the
     * largest of those confirmed values.
     */
    data class DisplacementAgreed(val maxDisplacementPx: Int) : PetFollowOutcome

    fun describe(): String = when (this) {
        ModeAddsNoPetWindow -> "not applicable (this mode adds no pet window)"
        ContentTopNeverReadable ->
            "not measured (the card's own content top could not be read at any sample)"
        NoDisplacementEverObserved ->
            "no displacement ever observed (the card's content top was readable throughout and " +
                "never moved up from its baseline, so no keyboard height was derivable and the pet " +
                "was not moved)"
        DisplacementsObservedButNoneAgreed ->
            "displacements were observed but none was reported by two consecutive samples, so none " +
                "was acted on (every observation is listed below, including the ones that never agreed)"
        is DisplacementAgreed ->
            "keyboard height derived from a content displacement of up to $maxDisplacementPx px, " +
                "confirmed by two consecutive samples"
    }
}

/**
 * Everything one run observed about using the card window as a measuring instrument for the pet
 * window.
 *
 * [restoredToOriginalY] is nullable on purpose: `null` means the pet never moved, so restoration
 * never applied. A `false` would claim the instrument tried to restore the pet and failed.
 */
data class PetFollowRecord(
    /**
     * The pet window's starting y, or null when the window bounds could not be read and a fallback
     * position had to be used — so a fallback run is never mistaken for a measured one.
     */
    val originalYPx: Int?,
    val outcome: PetFollowOutcome,
    val moves: List<PetFollowMove>,
    val restoredToOriginalY: Boolean?,
    /** Every displacement observed, including those that never agreed and so never caused a move. */
    val observations: List<ContentDisplacementObservation>,
    /** True when the observation list was truncated by its record cap; behaviour was not truncated. */
    val observationsTruncated: Boolean,
    /** Every baseline reset that happened during the run, and what caused each one. */
    val baselineResets: List<DisplacementBaselineReset>,
) {
    companion object {
        /** The record for every mode that adds no pet window. */
        val NOT_APPLICABLE = PetFollowRecord(
            originalYPx = null,
            outcome = PetFollowOutcome.ModeAddsNoPetWindow,
            moves = emptyList(),
            restoredToOriginalY = null,
            observations = emptyList(),
            observationsTruncated = false,
            baselineResets = emptyList(),
        )
    }
}
