package com.gcatcode.petmephone.feature.overlay.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `[RENDER-5]` `[RENDER-6]`: a decode failure renders the broken-shape placeholder, never a blank
 * area. Renders the real `BrokenPlaceholder` composable directly — the exact branch `PetOverlay`
 * draws when `PetOverlayStateHolder.sheetResult` is `Failed`.
 */
class PetOverlayFailurePlaceholderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun decodeFailureRendersBrokenShapeNeverBlank() {
        composeTestRule.setContent { BrokenPlaceholder() }

        val bitmap = composeTestRule.onRoot().captureToImage().asAndroidBitmap()
        var foundRedPixel = false
        for (x in 0 until bitmap.width step 4) {
            for (y in 0 until bitmap.height step 4) {
                val pixel = bitmap.getPixel(x, y)
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                if (red > 200 && green < 60 && blue < 60) {
                    foundRedPixel = true
                }
            }
        }
        // Never blank: the broken-shape placeholder must actually paint distinct (red) pixels.
        assertTrue("expected the broken-shape placeholder to paint red pixels, area was blank", foundRedPixel)
    }

    /**
     * `[IMPORT-15]` — the identity affordance is persistent "regardless of the active character",
     * and a character whose sheet failed to decode is still the active character. This is the case
     * that was missing: the badge used to be drawn only on the successful-decode path, which made
     * the guarantee conditional on the very thing that had just failed.
     *
     * Asserted on real pixels rather than structurally. The placeholder does not animate, so unlike
     * the `ReadyPet` path there is no perpetual coroutine for `captureToImage` to wait on.
     */
    @Test
    fun theIdentityAffordanceRendersEvenWhenTheCharacterIsBroken() {
        composeTestRule.setContent { BrokenPlaceholder() }

        val bitmap = composeTestRule.onRoot().captureToImage().asAndroidBitmap()

        // Asserted on the badge's BLACK RIM, not its white centre. The first draft of this test
        // checked for a white pixel at the centre and passed with the fix reverted: the surface
        // renders on an opaque white background, so "white at (16,16)" was true either way. The rim
        // is the only thing on this placeholder that is neither the background nor the red shape.
        val badgeCentre = 16
        val badgeRadius = 10
        var foundRimPixel = false
        for (x in (badgeCentre - badgeRadius - 2)..(badgeCentre + badgeRadius + 2)) {
            for (y in (badgeCentre - badgeRadius - 2)..(badgeCentre + badgeRadius + 2)) {
                if (x < 0 || y < 0 || x >= bitmap.width || y >= bitmap.height) continue
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val a = (pixel ushr 24) and 0xFF
                if (a > 200 && r < 60 && g < 60 && b < 60) {
                    foundRimPixel = true
                }
            }
        }

        assertTrue(
            "expected the identity badge's dark rim near (16,16) on a broken character; " +
                "without it the affordance is conditional on a successful decode",
            foundRimPixel,
        )
    }
}
