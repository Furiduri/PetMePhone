package com.gcatcode.petmephone.core.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SCREEN_WIDTH_PX = 1080
private const val SCREEN_HEIGHT_PX = 2280
private const val CARD_WIDTH_PX = 320
private const val CARD_HEIGHT_PX = 400
private const val GAP_PX = 16
private const val PET_SIZE_PX = 220

private val NO_INSETS = ScreenInsets(left = 0, top = 0, right = 0, bottom = 0)

class QuickMenuPlacementTest {

    @Test
    fun `pet at top-left corner opens down-right`() {
        val anchor = QuickMenuAnchor(xPx = 0, yPx = 0, sizePx = PET_SIZE_PX)

        val result = QuickMenuPlacement.place(
            anchor, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX, CARD_WIDTH_PX, CARD_HEIGHT_PX, NO_INSETS, GAP_PX,
        )

        // Fails if the card were placed left of or above the pet: x must clear the pet's right
        // edge + gap, y must clear the pet's bottom edge + gap.
        assertEquals(PET_SIZE_PX + GAP_PX, result.x)
        assertEquals(PET_SIZE_PX + GAP_PX, result.y)
    }

    @Test
    fun `pet at top-right corner opens down-left`() {
        val anchor = QuickMenuAnchor(
            xPx = SCREEN_WIDTH_PX - PET_SIZE_PX,
            yPx = 0,
            sizePx = PET_SIZE_PX,
        )

        val result = QuickMenuPlacement.place(
            anchor, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX, CARD_WIDTH_PX, CARD_HEIGHT_PX, NO_INSETS, GAP_PX,
        )

        // Fails if x were placed to the right of the pet (>= anchor.xPx) instead of left of it.
        assertEquals(anchor.xPx - GAP_PX - CARD_WIDTH_PX, result.x)
        assertEquals(PET_SIZE_PX + GAP_PX, result.y)
        assertTrue(result.x + CARD_WIDTH_PX <= anchor.xPx)
    }

    @Test
    fun `pet at bottom-left corner opens up-right`() {
        val anchor = QuickMenuAnchor(
            xPx = 0,
            yPx = SCREEN_HEIGHT_PX - PET_SIZE_PX,
            sizePx = PET_SIZE_PX,
        )

        val result = QuickMenuPlacement.place(
            anchor, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX, CARD_WIDTH_PX, CARD_HEIGHT_PX, NO_INSETS, GAP_PX,
        )

        // Fails if y were placed below the pet instead of above it.
        assertEquals(PET_SIZE_PX + GAP_PX, result.x)
        assertEquals(anchor.yPx - GAP_PX - CARD_HEIGHT_PX, result.y)
        assertTrue(result.y + CARD_HEIGHT_PX <= anchor.yPx)
    }

    @Test
    fun `pet at bottom-right corner opens up-left`() {
        val anchor = QuickMenuAnchor(
            xPx = SCREEN_WIDTH_PX - PET_SIZE_PX,
            yPx = SCREEN_HEIGHT_PX - PET_SIZE_PX,
            sizePx = PET_SIZE_PX,
        )

        val result = QuickMenuPlacement.place(
            anchor, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX, CARD_WIDTH_PX, CARD_HEIGHT_PX, NO_INSETS, GAP_PX,
        )

        assertEquals(anchor.xPx - GAP_PX - CARD_WIDTH_PX, result.x)
        assertEquals(anchor.yPx - GAP_PX - CARD_HEIGHT_PX, result.y)
        assertTrue(result.x + CARD_WIDTH_PX <= anchor.xPx)
        assertTrue(result.y + CARD_HEIGHT_PX <= anchor.yPx)
    }

    @Test
    fun `pet at left-edge vertical midpoint opens right and centers vertically`() {
        // Vertical midpoint: topSpace == bottomSpace exactly, so the tie branch must fire.
        val anchorY = (SCREEN_HEIGHT_PX - PET_SIZE_PX) / 2
        val anchor = QuickMenuAnchor(xPx = 0, yPx = anchorY, sizePx = PET_SIZE_PX)

        val result = QuickMenuPlacement.place(
            anchor, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX, CARD_WIDTH_PX, CARD_HEIGHT_PX, NO_INSETS, GAP_PX,
        )

        // Fails if x were left of the pet, or if y were offset to anchor.bottom+gap /
        // anchor.top-gap-cardHeight (the non-centered edge-offset formula) instead of centered.
        assertEquals(PET_SIZE_PX + GAP_PX, result.x)
        val expectedCenteredY = anchor.yPx + PET_SIZE_PX / 2 - CARD_HEIGHT_PX / 2
        assertEquals(expectedCenteredY, result.y)
    }

    @Test
    fun `pet at right-edge vertical midpoint opens left and centers vertically`() {
        val anchorY = (SCREEN_HEIGHT_PX - PET_SIZE_PX) / 2
        val anchor = QuickMenuAnchor(xPx = SCREEN_WIDTH_PX - PET_SIZE_PX, yPx = anchorY, sizePx = PET_SIZE_PX)

        val result = QuickMenuPlacement.place(
            anchor, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX, CARD_WIDTH_PX, CARD_HEIGHT_PX, NO_INSETS, GAP_PX,
        )

        assertEquals(anchor.xPx - GAP_PX - CARD_WIDTH_PX, result.x)
        val expectedCenteredY = anchor.yPx + PET_SIZE_PX / 2 - CARD_HEIGHT_PX / 2
        assertEquals(expectedCenteredY, result.y)
    }

    @Test
    fun `card wider than available space is clamped within horizontal bounds`() {
        // Pet at top-right corner: chosen direction is left, but the card (900px) is wider than
        // the ~740px available to the left. Fails if result.x is negative (off-screen) or if
        // result.x + cardWidth exceeds the screen's right bound.
        val hugeCardWidthPx = 900
        val anchor = QuickMenuAnchor(xPx = SCREEN_WIDTH_PX - PET_SIZE_PX, yPx = 0, sizePx = PET_SIZE_PX)

        val result = QuickMenuPlacement.place(
            anchor, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX, hugeCardWidthPx, CARD_HEIGHT_PX, NO_INSETS, GAP_PX,
        )

        assertTrue("x=${result.x} must not be negative", result.x >= 0)
        assertTrue(
            "card right edge ${result.x + hugeCardWidthPx} must not exceed screen width $SCREEN_WIDTH_PX",
            result.x + hugeCardWidthPx <= SCREEN_WIDTH_PX,
        )
    }

    @Test
    fun `card taller than available space is clamped within vertical bounds`() {
        val hugeCardHeightPx = 2000
        val anchor = QuickMenuAnchor(xPx = 0, yPx = SCREEN_HEIGHT_PX - PET_SIZE_PX, sizePx = PET_SIZE_PX)

        val result = QuickMenuPlacement.place(
            anchor, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX, CARD_WIDTH_PX, hugeCardHeightPx, NO_INSETS, GAP_PX,
        )

        assertTrue("y=${result.y} must not be negative", result.y >= 0)
        assertTrue(
            "card bottom edge ${result.y + hugeCardHeightPx} must not exceed screen height $SCREEN_HEIGHT_PX",
            result.y + hugeCardHeightPx <= SCREEN_HEIGHT_PX,
        )
    }

    @Test
    fun `top system-bar inset keeps the card clear of the status bar`() {
        // Fails if the top inset were ignored: an anchor near the very top with no inset would
        // place the card at y=0, which would be under the status bar once the inset is honored.
        val topInsetPx = 80
        val insets = ScreenInsets(left = 0, top = topInsetPx, right = 0, bottom = 0)
        val anchor = QuickMenuAnchor(xPx = 0, yPx = topInsetPx, sizePx = PET_SIZE_PX)

        val result = QuickMenuPlacement.place(
            anchor, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX, CARD_WIDTH_PX, CARD_HEIGHT_PX, insets, GAP_PX,
        )

        assertTrue("y=${result.y} must clear the top inset $topInsetPx", result.y >= topInsetPx)
    }

    @Test
    fun `right display-cutout inset keeps the card clear of the cutout region`() {
        // Fails if the right inset were ignored: the card's right edge would land past
        // SCREEN_WIDTH_PX - cutoutInsetPx, overlapping the cutout.
        val cutoutInsetPx = 60
        val insets = ScreenInsets(left = 0, top = 0, right = cutoutInsetPx, bottom = 0)
        val anchor = QuickMenuAnchor(xPx = SCREEN_WIDTH_PX - PET_SIZE_PX, yPx = 0, sizePx = PET_SIZE_PX)

        val result = QuickMenuPlacement.place(
            anchor, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX, CARD_WIDTH_PX, CARD_HEIGHT_PX, insets, GAP_PX,
        )

        assertTrue(
            "card right edge ${result.x + CARD_WIDTH_PX} must clear the cutout at ${SCREEN_WIDTH_PX - cutoutInsetPx}",
            result.x + CARD_WIDTH_PX <= SCREEN_WIDTH_PX - cutoutInsetPx,
        )
    }

    @Test
    fun `repeated calls with identical inputs return the same result`() {
        val anchor = QuickMenuAnchor(xPx = 400, yPx = 900, sizePx = PET_SIZE_PX)
        val insets = ScreenInsets(left = 10, top = 20, right = 10, bottom = 40)

        val first = QuickMenuPlacement.place(
            anchor, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX, CARD_WIDTH_PX, CARD_HEIGHT_PX, insets, GAP_PX,
        )
        val second = QuickMenuPlacement.place(
            anchor, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX, CARD_WIDTH_PX, CARD_HEIGHT_PX, insets, GAP_PX,
        )

        // Fails if `place` carried any hidden mutable state or non-determinism (e.g. system time).
        assertEquals(first, second)
    }
}
