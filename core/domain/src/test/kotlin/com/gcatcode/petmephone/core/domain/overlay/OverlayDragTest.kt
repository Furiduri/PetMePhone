package com.gcatcode.petmephone.core.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SCREEN_WIDTH_PX = 1080
private const val RENDER_SIZE_PX = 220

class OverlayDragTest {

    @Test
    fun `nearest edge resolves left when the pet centre sits left of screen centre`() {
        // Pet at x=0: centre at 110, screen centre at 540 -> LEFT.
        assertEquals(ScreenEdge.LEFT, nearestEdge(0, SCREEN_WIDTH_PX, RENDER_SIZE_PX))
    }

    @Test
    fun `nearest edge resolves right when the pet centre sits right of screen centre`() {
        // Pet flush against the right edge: centre well past screen centre -> RIGHT.
        val xAtRightEdge = SCREEN_WIDTH_PX - RENDER_SIZE_PX
        assertEquals(ScreenEdge.RIGHT, nearestEdge(xAtRightEdge, SCREEN_WIDTH_PX, RENDER_SIZE_PX))
    }

    @Test
    fun `exact centre tie-break resolves RIGHT`() {
        // Pet centre exactly at screen centre: x + renderSize/2 == screenWidth/2.
        val xAtExactCentre = (SCREEN_WIDTH_PX / 2) - (RENDER_SIZE_PX / 2)
        assertEquals(ScreenEdge.RIGHT, nearestEdge(xAtExactCentre, SCREEN_WIDTH_PX, RENDER_SIZE_PX))
    }

    @Test
    fun `nearest edge table across a range of horizontal positions`() {
        val cases = listOf(
            0 to ScreenEdge.LEFT,
            100 to ScreenEdge.LEFT,
            429 to ScreenEdge.LEFT, // centre at 539, just left of 540
            430 to ScreenEdge.RIGHT, // centre at 540, the tie -> RIGHT
            600 to ScreenEdge.RIGHT,
            860 to ScreenEdge.RIGHT,
        )

        cases.forEach { (x, expected) ->
            assertEquals("x=$x", expected, nearestEdge(x, SCREEN_WIDTH_PX, RENDER_SIZE_PX))
        }
    }
}

class ExceedsSlopTest {

    private val slopPx = 24

    @Test
    fun `distance below slop is not a drag`() {
        assertFalse(exceedsSlop(dxPx = 5f, dyPx = 5f, slopPx = slopPx))
    }

    @Test
    fun `distance exactly at slop is not a drag - must strictly exceed`() {
        assertFalse(exceedsSlop(dxPx = slopPx.toFloat(), dyPx = 0f, slopPx = slopPx))
    }

    @Test
    fun `distance past slop is a drag`() {
        assertTrue(exceedsSlop(dxPx = slopPx.toFloat() + 1f, dyPx = 0f, slopPx = slopPx))
    }

    @Test
    fun `diagonal movement past slop is a drag`() {
        assertTrue(exceedsSlop(dxPx = 20f, dyPx = 20f, slopPx = slopPx))
    }
}
