package com.gcatcode.petmephone.core.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The read gate draws a line that is easy to blur: an out-of-range value is recovered, an absent
 * one is not invented. Both halves are asserted here because collapsing them in either direction
 * is a defect — inventing a position the user never chose, or discarding one they did.
 */
class OverlayPositionFractionReadTest {

    @Test
    fun `a missing key stays absent and is never turned into zero`() {
        assertEquals(OverlayPositionFraction.Stored.Absent, OverlayPositionFraction.read(null, 0.5f))
        assertEquals(OverlayPositionFraction.Stored.Absent, OverlayPositionFraction.read(0.5f, null))
        assertEquals(OverlayPositionFraction.Stored.Absent, OverlayPositionFraction.read(null, null))
    }

    @Test
    fun `a non-finite value stays absent`() {
        assertEquals(OverlayPositionFraction.Stored.Absent, OverlayPositionFraction.read(Float.NaN, 0.5f))
        assertEquals(
            OverlayPositionFraction.Stored.Absent,
            OverlayPositionFraction.read(0.5f, Float.POSITIVE_INFINITY),
        )
    }

    @Test
    fun `an in-range pair is returned untouched and is not reported as normalized`() {
        val result = OverlayPositionFraction.read(0.25f, 0.75f) as OverlayPositionFraction.Stored.Usable

        assertEquals(OverlayPositionFraction(0.25f, 0.75f), result.fraction)
        assertFalse(result.normalized)
    }

    @Test
    fun `the negative y a drag above the top edge once persisted is pulled to the nearest valid value`() {
        // The exact pair read off the device: x snapped correctly to the left edge, y overshot.
        val result = OverlayPositionFraction.read(0.0f, -0.034f) as OverlayPositionFraction.Stored.Usable

        assertEquals(0.0f, result.fraction.x, 0f)
        assertEquals(0.0f, result.fraction.y, 0f)
        assertTrue("an out-of-range read must report itself", result.normalized)
    }

    @Test
    fun `one bad axis no longer discards the good one`() {
        val result = OverlayPositionFraction.read(0.8f, 1.4f) as OverlayPositionFraction.Stored.Usable

        assertEquals("the valid axis must survive", 0.8f, result.fraction.x, 0f)
        assertEquals(1.0f, result.fraction.y, 0f)
        assertTrue(result.normalized)
    }
}
