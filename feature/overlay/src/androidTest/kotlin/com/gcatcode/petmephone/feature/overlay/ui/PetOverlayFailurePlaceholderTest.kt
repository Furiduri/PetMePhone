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
}
