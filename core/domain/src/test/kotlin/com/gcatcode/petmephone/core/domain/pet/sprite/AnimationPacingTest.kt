package com.gcatcode.petmephone.core.domain.pet.sprite

import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationPacingTest {

    private val default = 150L
    private val floor = 16L

    @Test
    fun `no declaration leaves the configured frame rate alone`() {
        val held = AnimationPacing.frameDurationMillis(
            declaredFrameDurationMillis = null,
            defaultFrameDurationMillis = default,
            minFrameDurationMillis = floor,
        )

        assertEquals("an absent declaration is not a guess at one", default, held)
    }

    @Test
    fun `a declared frame duration is used as declared`() {
        assertEquals(200L, AnimationPacing.frameDurationMillis(200, default, floor))
    }

    @Test
    fun `more frames make a longer animation, not a faster one`() {
        // The property that matters, and the one an earlier per-cycle version got backwards: at a
        // fixed frame duration a sheet with more frames simply plays for longer, because in this
        // project's art extra frames are extra movement rather than the same movement drawn finer.
        val held = AnimationPacing.frameDurationMillis(150, default, floor)

        assertEquals("six frames", 900L, held * 6)
        assertEquals("nine frames of the same animation run half again as long", 1350L, held * 9)
    }

    @Test
    fun `an unreasonably small declaration is floored instead of spinning the clock`() {
        val held = AnimationPacing.frameDurationMillis(
            declaredFrameDurationMillis = 1,
            defaultFrameDurationMillis = default,
            minFrameDurationMillis = floor,
        )

        assertEquals("1ms a frame is faster than the display can show", floor, held)
    }
}
