package com.gcatcode.petmephone.core.domain.pet.sprite

/**
 * How fast one animation plays.
 *
 * A character declares how long a full **cycle** should take, because that is the unit the result
 * is judged in: with a fixed per-frame interval a 12-frame sheet takes twice as long as a 6-frame
 * one, so a single number reads as right on one character and sluggish on another. Timed per cycle,
 * adding frames makes an animation smoother rather than slower.
 *
 * Arithmetic and policy, so it lives here rather than inside the composable that consumes it.
 */
object AnimationPacing {

    /**
     * The per-frame delay for one animation.
     *
     * [cycleDurationMillis] is what the character's manifest declared, or `null` when it declared
     * nothing — in which case [defaultFrameIntervalMillis] is used unchanged. An absent declaration
     * is not a zero-length cycle and not a guess at one; it means the character expressed no
     * opinion, and the configured frame rate stands.
     *
     * [minFrameIntervalMillis] floors the result so a very short declared cycle spread over many
     * frames cannot produce a zero delay and spin the frame clock.
     */
    fun frameIntervalMillis(
        cycleDurationMillis: Long?,
        frameCount: Int,
        defaultFrameIntervalMillis: Long,
        minFrameIntervalMillis: Long,
    ): Long {
        if (cycleDurationMillis == null) return defaultFrameIntervalMillis
        // A sheet always has at least one frame; guarding anyway keeps a malformed layout from
        // turning a pacing question into a divide-by-zero crash.
        val frames = frameCount.coerceAtLeast(1)
        return (cycleDurationMillis / frames).coerceAtLeast(minFrameIntervalMillis)
    }
}
