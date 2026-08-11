package com.gcatcode.petmephone.core.domain.pet.sprite

import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationPacingTest {

    private val default = 150L
    private val floor = 16L

    @Test
    fun `no declaration leaves the configured frame rate alone`() {
        val interval = AnimationPacing.frameIntervalMillis(
            cycleDurationMillis = null,
            frameCount = 12,
            defaultFrameIntervalMillis = default,
            minFrameIntervalMillis = floor,
        )

        assertEquals("an absent declaration is not a guess at one", default, interval)
    }

    @Test
    fun `two sheets with different frame counts play at the same declared speed`() {
        // The whole reason the declaration is per cycle. Under a fixed per-frame interval these two
        // take 900ms and 1800ms: the same configuration reading as right on one and sluggish on
        // the other.
        val six = AnimationPacing.frameIntervalMillis(900, 6, default, floor)
        val twelve = AnimationPacing.frameIntervalMillis(900, 12, default, floor)

        assertEquals(150L, six)
        assertEquals(75L, twelve)
        assertEquals("both cycles must last what the manifest asked for", 900L, six * 6)
        assertEquals(900L, twelve * 12)
    }

    @Test
    fun `adding frames to a declared cycle makes it smoother, never slower`() {
        val coarse = AnimationPacing.frameIntervalMillis(800, 4, default, floor)
        val smooth = AnimationPacing.frameIntervalMillis(800, 16, default, floor)

        assertEquals(800L, coarse * 4)
        assertEquals(800L, smooth * 16)
    }

    @Test
    fun `an unreasonably short cycle is floored instead of spinning the clock`() {
        val interval = AnimationPacing.frameIntervalMillis(
            cycleDurationMillis = 10,
            frameCount = 12,
            defaultFrameIntervalMillis = default,
            minFrameIntervalMillis = floor,
        )

        assertEquals("10ms over 12 frames would otherwise be a zero delay", floor, interval)
    }

    @Test
    fun `a zero frame count is paced, not divided by zero`() {
        val interval = AnimationPacing.frameIntervalMillis(
            cycleDurationMillis = 600,
            frameCount = 0,
            defaultFrameIntervalMillis = default,
            minFrameIntervalMillis = floor,
        )

        assertEquals(600L, interval)
    }
}
