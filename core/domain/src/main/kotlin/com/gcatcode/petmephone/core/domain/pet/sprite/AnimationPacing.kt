package com.gcatcode.petmephone.core.domain.pet.sprite

/**
 * How fast one animation plays.
 *
 * A character declares **how long each frame is held**, and the length of the animation follows from
 * how many frames it has. More frames therefore means a longer animation, not a faster one.
 *
 * That is a decision about what a frame means in this project's art, and it was got wrong once: an
 * earlier version had the character declare the duration of a whole cycle, on the assumption that
 * more frames meant the same motion drawn more finely. It does not here — a sheet with more frames
 * holds more movement, so dividing a fixed cycle across them rushed it. Per frame is the unit that
 * matches the art.
 *
 * Arithmetic and policy, so it lives here rather than inside the composable that consumes it.
 */
object AnimationPacing {

    /**
     * The per-frame delay for one animation.
     *
     * [declaredFrameDurationMillis] is what the character's manifest declared, or `null` when it
     * declared nothing — in which case [defaultFrameDurationMillis] is used unchanged. An absent
     * declaration is not a zero and not a guess; it means the character expressed no opinion, and
     * the configured frame rate stands.
     *
     * [minFrameDurationMillis] floors the result so a very small declared value cannot drive the
     * frame clock faster than the display can show.
     */
    fun frameDurationMillis(
        declaredFrameDurationMillis: Long?,
        defaultFrameDurationMillis: Long,
        minFrameDurationMillis: Long,
    ): Long {
        if (declaredFrameDurationMillis == null) return defaultFrameDurationMillis
        return declaredFrameDurationMillis.coerceAtLeast(minFrameDurationMillis)
    }
}
