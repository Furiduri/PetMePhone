package com.petmephone.spike.imeviability

/**
 * One reading of the card's content displacement, recorded whether or not it caused a move.
 *
 * An observation that never reached agreement is still written into the findings. Dropping it would
 * hide exactly the kind of transient that made round 2's single 1746px outlier look like a measured
 * keyboard height, and the point of the debounce is to stop ACTING on such a reading, not to stop
 * seeing it.
 */
data class ContentDisplacementObservation(
    val elapsedMillisSinceWindowAdded: Long,
    /** `baselineTop - currentTop`, in pixels, exactly as measured. Never clamped, never capped. */
    val displacementPx: Int,
    /** True when the immediately preceding observation reported the same displacement. */
    val agreedWithPreviousObservation: Boolean,
    /** True when this observation was the one the pet was actually moved on. */
    val causedPetMove: Boolean,
)

/**
 * One reset of the displacement baseline, and what caused it.
 *
 * Round 2 had no reset at all, so after a rotation the portrait baseline was compared against
 * landscape geometry and the resulting "reduction" was an orientation delta rather than a keyboard.
 */
data class DisplacementBaselineReset(
    val elapsedMillisSinceWindowAdded: Long,
    val cause: String,
)

/**
 * Derives the keyboard displacement from the card's own content position, and decides — with a
 * two-sample debounce — when the pet may be moved.
 *
 * The measurement is `baselineTop - currentTop` where both are `getLocationOnScreen`-based tops of
 * the card's content. The baseline is the largest (lowest on screen) content top seen since the last
 * reset: content returning to its resting place re-establishes it.
 *
 * The debounce is a debounce on ACTING, not a plausibility filter. There is no maximum height, no
 * rejection of a value for being large, and no clamping — an unusual displacement observed twice in
 * a row is acted on, because it is then a measurement rather than a transient.
 */
class ContentDisplacementTracker {

    private var baselineTopPx: Int? = null
    private var previousDisplacementPx: Int? = null
    private var lastOrientation: SampleOrientation? = null
    private var pendingResetCause: String? = null

    private val observations = mutableListOf<ContentDisplacementObservation>()
    private val baselineResets = mutableListOf<DisplacementBaselineReset>()

    private var contentTopEverReadable = false
    private var maxAgreedDisplacementPx: Int? = null
    private var observationsTruncated = false
    private var lastObservationWasRecorded = false

    /** True once any content top was readable at all, distinguishing "no reading" from "no move". */
    fun contentTopEverReadable(): Boolean = contentTopEverReadable

    /** The largest displacement that ever reached agreement, or null if none ever did. */
    fun maxAgreedDisplacementPx(): Int? = maxAgreedDisplacementPx

    fun observations(): List<ContentDisplacementObservation> = observations.toList()

    fun baselineResets(): List<DisplacementBaselineReset> = baselineResets.toList()

    fun observationsTruncated(): Boolean = observationsTruncated

    /**
     * Asks for the baseline to be dropped before the next observation. Called from the service's
     * `onConfigurationChanged`; the reset is applied at the next sample so it lands with a real
     * elapsed time and a real reading rather than at an arbitrary moment.
     */
    fun requestBaselineReset(cause: String) {
        pendingResetCause = cause
    }

    /**
     * Feeds one sample in and reports the displacement the pet may be moved by, or null when no move
     * is warranted — because the content top was unreadable, because the baseline was just
     * established, or because this displacement has not yet been seen twice in a row.
     */
    fun observe(sample: KeyboardGeometrySample): Int? {
        val currentTop = sample.contentTopOnScreenPx ?: return null
        contentTopEverReadable = true

        applyPendingReset(sample)
        applyOrientationChangeReset(sample)
        lastOrientation = sample.orientation ?: lastOrientation

        val baseline = baselineTopPx
        if (baseline == null || currentTop > baseline) {
            baselineTopPx = currentTop
            // A freshly established baseline is a displacement of zero BY DEFINITION, not a
            // measurement of a keyboard, so it enters the debounce like any other reading.
            return recordAndDecide(sample, displacement = 0)
        }
        return recordAndDecide(sample, displacement = baseline - currentTop)
    }

    /**
     * Marks the most recent observation as the one the pet was actually moved on.
     *
     * Does nothing once recording is truncated: the observation that caused the move was not stored,
     * and marking an older one instead would attribute the move to the wrong reading.
     */
    fun markLastObservationCausedMove() {
        if (!lastObservationWasRecorded) return
        val last = observations.removeLastOrNull() ?: return
        observations += last.copy(causedPetMove = true)
    }

    private fun applyPendingReset(sample: KeyboardGeometrySample) {
        val cause = pendingResetCause ?: return
        pendingResetCause = null
        resetBaseline(sample, cause)
    }

    private fun applyOrientationChangeReset(sample: KeyboardGeometrySample) {
        val current = sample.orientation ?: return
        val previous = lastOrientation ?: return
        if (current == previous) return
        resetBaseline(sample, "window bounds orientation changed from ${previous.label} to ${current.label}")
    }

    private fun resetBaseline(sample: KeyboardGeometrySample, cause: String) {
        baselineTopPx = null
        previousDisplacementPx = null
        baselineResets += DisplacementBaselineReset(
            elapsedMillisSinceWindowAdded = sample.elapsedMillisSinceWindowAdded,
            cause = cause,
        )
    }

    private fun recordAndDecide(sample: KeyboardGeometrySample, displacement: Int): Int? {
        val agreed = previousDisplacementPx == displacement
        previousDisplacementPx = displacement
        record(
            ContentDisplacementObservation(
                elapsedMillisSinceWindowAdded = sample.elapsedMillisSinceWindowAdded,
                displacementPx = displacement,
                agreedWithPreviousObservation = agreed,
                causedPetMove = false,
            ),
        )
        if (!agreed) return null
        if (displacement > 0) {
            maxAgreedDisplacementPx = maxOf(maxAgreedDisplacementPx ?: 0, displacement)
        }
        return displacement
    }

    private fun record(observation: ContentDisplacementObservation) {
        if (observations.size >= MAX_RECORDED_OBSERVATIONS) {
            // Bounds the RECORD only. `observe` still returns its decision, so a full list never
            // stops the pet from following.
            observationsTruncated = true
            lastObservationWasRecorded = false
            return
        }
        observations += observation
        lastObservationWasRecorded = true
    }

    private companion object {
        /**
         * Upper bound on recorded observations. A layout listener fires as often as the framework
         * decides to, and an unbounded list buries the interesting transition. Reaching it is
         * reported in the findings so a truncated list is never read as a settled one.
         */
        const val MAX_RECORDED_OBSERVATIONS = 60
    }
}
