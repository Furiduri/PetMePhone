package com.gcatcode.petmephone.core.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs

/** `[POS-1]` — fraction/pixel round-trip. */
class OverlayPositionFractionTest {

    private val screens = listOf(
        Triple(1080, 2400, 220), // tall phone
        Triple(2400, 1080, 220), // rotated
        Triple(720, 1600, 220), // smaller device
        Triple(1440, 3120, 220), // large device
    )

    @Test
    fun `round trip is lossless within tolerance across screen sizes`() {
        val fractions = listOf(
            OverlayPositionFraction(0f, 0f),
            OverlayPositionFraction(1f, 1f),
            OverlayPositionFraction(0.5f, 0.5f),
            OverlayPositionFraction(0.25f, 0.75f),
        )

        for ((width, height, renderSize) in screens) {
            for (fraction in fractions) {
                val pixels = fraction.toPixels(width, height, renderSize)
                val roundTripped = OverlayPositionFraction.ofPixels(pixels, width, height, renderSize)

                assertEquals(fraction.x, roundTripped.x, TOLERANCE)
                assertEquals(fraction.y, roundTripped.y, TOLERANCE)
            }
        }
    }

    @Test
    fun `toPixels stays within the travel range`() {
        val (width, height, renderSize) = screens.first()
        val pixels = OverlayPositionFraction(1f, 1f).toPixels(width, height, renderSize)

        assertEquals(width - renderSize, pixels.x)
        assertEquals(height - renderSize, pixels.y)
    }

    private fun assertEquals(expected: Float, actual: Float, tolerance: Float) {
        assert(abs(expected - actual) <= tolerance) {
            "expected $expected within $tolerance of $actual"
        }
    }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}

/** `[POS-2]` — absence never renders as zero. */
class OverlayPositionFractionValidOrNullTest {

    @Test
    fun `both present and in range returns a fraction`() {
        val result = OverlayPositionFraction.validOrNull(0.5f, 0.5f)

        assertNotNull(result)
        assertEquals(0.5f, result!!.x, 0.0001f)
        assertEquals(0.5f, result.y, 0.0001f)
    }

    @Test
    fun `NaN x returns null`() {
        assertNull(OverlayPositionFraction.validOrNull(Float.NaN, 0.5f))
    }

    @Test
    fun `NaN y returns null`() {
        assertNull(OverlayPositionFraction.validOrNull(0.5f, Float.NaN))
    }

    @Test
    fun `x outside 0f to 1f returns null`() {
        assertNull(OverlayPositionFraction.validOrNull(1.5f, 0.5f))
        assertNull(OverlayPositionFraction.validOrNull(-0.1f, 0.5f))
    }

    @Test
    fun `y outside 0f to 1f returns null`() {
        assertNull(OverlayPositionFraction.validOrNull(0.5f, 1.5f))
        assertNull(OverlayPositionFraction.validOrNull(0.5f, -0.1f))
    }

    @Test
    fun `missing x returns null`() {
        assertNull(OverlayPositionFraction.validOrNull(null, 0.5f))
    }

    @Test
    fun `missing y returns null`() {
        assertNull(OverlayPositionFraction.validOrNull(0.5f, null))
    }

    @Test
    fun `both missing returns null`() {
        assertNull(OverlayPositionFraction.validOrNull(null, null))
    }

    @Test
    fun `infinite values return null`() {
        assertNull(OverlayPositionFraction.validOrNull(Float.POSITIVE_INFINITY, 0.5f))
        assertNull(OverlayPositionFraction.validOrNull(0.5f, Float.NEGATIVE_INFINITY))
    }
}
