package com.gcatcode.petmephone.core.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SCREEN_WIDTH_PX = 1080
private const val SCREEN_HEIGHT_PX = 2280
private const val CARD_WIDTH_PX = 320
private const val MAX_CARD_HEIGHT_PX = 400
private const val GAP_PX = 16
private const val PET_SIZE_PX = 220

private val NO_INSETS = ScreenInsets(left = 0, top = 0, right = 0, bottom = 0)

private fun place(
    anchor: QuickMenuAnchor,
    insets: ScreenInsets = NO_INSETS,
    maxCardHeightPx: Int = MAX_CARD_HEIGHT_PX,
    cardWidthPx: Int = CARD_WIDTH_PX,
) = QuickMenuPlacement.place(
    anchor = anchor,
    screenWidthPx = SCREEN_WIDTH_PX,
    screenHeightPx = SCREEN_HEIGHT_PX,
    cardWidthPx = cardWidthPx,
    maxCardHeightPx = maxCardHeightPx,
    insets = insets,
    gapPx = GAP_PX,
)

class QuickMenuPlacementTest {

    @Test
    fun `pet at top-left corner opens down and right, measured from the top`() {
        val result = place(QuickMenuAnchor(xPx = 0, yPx = 0, sizePx = PET_SIZE_PX))

        // Fails if the card were placed left of or above the pet.
        assertEquals(PET_SIZE_PX + GAP_PX, result.xPx)
        assertEquals(PET_SIZE_PX + GAP_PX, result.yPx)
        assertEquals(VerticalAnchor.TOP, result.verticalAnchor)
    }

    @Test
    fun `pet at top-right corner opens down and left`() {
        val anchor = QuickMenuAnchor(xPx = SCREEN_WIDTH_PX - PET_SIZE_PX, yPx = 0, sizePx = PET_SIZE_PX)

        val result = place(anchor)

        // Fails if x were placed to the right of the pet instead of left of it.
        assertEquals(anchor.xPx - GAP_PX - CARD_WIDTH_PX, result.xPx)
        assertTrue(result.xPx + CARD_WIDTH_PX <= anchor.xPx)
        assertEquals(VerticalAnchor.TOP, result.verticalAnchor)
    }

    @Test
    fun `pet at bottom-left corner opens up, anchored to the screen's bottom edge`() {
        val anchor = QuickMenuAnchor(xPx = 0, yPx = SCREEN_HEIGHT_PX - PET_SIZE_PX, sizePx = PET_SIZE_PX)

        val result = place(anchor)

        // The card's BOTTOM edge sits one gap above the pet's top edge, expressed as a distance
        // from the screen bottom precisely so the card's own height never enters the calculation.
        assertEquals(VerticalAnchor.BOTTOM, result.verticalAnchor)
        assertEquals(SCREEN_HEIGHT_PX - (anchor.yPx - GAP_PX), result.yPx)
        assertEquals(PET_SIZE_PX + GAP_PX, result.xPx)
    }

    @Test
    fun `pet at bottom-right corner opens up and left`() {
        val anchor = QuickMenuAnchor(
            xPx = SCREEN_WIDTH_PX - PET_SIZE_PX,
            yPx = SCREEN_HEIGHT_PX - PET_SIZE_PX,
            sizePx = PET_SIZE_PX,
        )

        val result = place(anchor)

        assertEquals(VerticalAnchor.BOTTOM, result.verticalAnchor)
        assertTrue(result.xPx + CARD_WIDTH_PX <= anchor.xPx)
    }

    @Test
    fun `the vertical result does not depend on the card's height`() {
        val anchor = QuickMenuAnchor(xPx = 0, yPx = SCREEN_HEIGHT_PX - PET_SIZE_PX, sizePx = PET_SIZE_PX)

        val short = place(anchor, maxCardHeightPx = 200)
        val tall = place(anchor, maxCardHeightPx = 1200)

        // THE regression guard. The card's window wraps its content, so its real height is unknown
        // when this runs. The previous implementation subtracted a height to get a top offset,
        // which positioned the card as if it were as tall as its ceiling and left a visible gap
        // between the card and the pet whenever it opened upward — reported from a device.
        // Fails the moment height re-enters the vertical calculation.
        assertEquals(short.yPx, tall.yPx)
        assertEquals(short.verticalAnchor, tall.verticalAnchor)
    }

    @Test
    fun `pet centred on the left edge centres the card on the pet`() {
        val anchor = QuickMenuAnchor(
            xPx = 0,
            yPx = (SCREEN_HEIGHT_PX - PET_SIZE_PX) / 2,
            sizePx = PET_SIZE_PX,
        )

        val result = place(anchor)

        // Fails if a tie were broken by pushing the card to an edge with a gap instead of centring.
        assertEquals(VerticalAnchor.CENTER, result.verticalAnchor)
        assertEquals((anchor.yPx + PET_SIZE_PX / 2) - SCREEN_HEIGHT_PX / 2, result.yPx)
    }

    @Test
    fun `a card wider than the available space is clamped inside the screen`() {
        val anchor = QuickMenuAnchor(xPx = SCREEN_WIDTH_PX - PET_SIZE_PX, yPx = 0, sizePx = PET_SIZE_PX)

        val result = place(anchor, cardWidthPx = 900)

        // Fails if the card were allowed to start off-screen or overflow the right edge.
        assertTrue("card starts off-screen: ${result.xPx}", result.xPx >= 0)
        assertTrue("card overflows: ${result.xPx + 900}", result.xPx + 900 <= SCREEN_WIDTH_PX)
    }

    @Test
    fun `a display-cutout inset shifts the card away from the cutout region`() {
        val anchor = QuickMenuAnchor(xPx = 0, yPx = 0, sizePx = PET_SIZE_PX)

        val wide = place(anchor, cardWidthPx = 1000)
        val withCutout = place(
            anchor,
            insets = ScreenInsets(left = 0, top = 0, right = 60, bottom = 0),
            cardWidthPx = 1000,
        )

        // Fails if insets were ignored: the usable right edge shrinks by 60px, so a card already
        // clamped against that edge must move left by exactly that much.
        assertEquals(wide.xPx - 60, withCutout.xPx)
    }

    @Test
    fun `repeated calls with identical inputs return the same result`() {
        val anchor = QuickMenuAnchor(xPx = 100, yPx = 900, sizePx = PET_SIZE_PX)

        // Fails if place ever gained hidden mutable state or a non-deterministic input.
        assertEquals(place(anchor), place(anchor))
    }
}
