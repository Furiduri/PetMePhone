package com.gcatcode.petmephone.feature.overlay.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionFraction
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionRepository
import com.gcatcode.petmephone.feature.overlay.character.BuiltInCharacterManifestReader
import com.gcatcode.petmephone.feature.overlay.sprite.BitmapDecoding
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetDecoder
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetResult
import com.gcatcode.petmephone.feature.overlay.system.ScreenStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `[RENDER-1]` `[RENDER-7]`: the IDLE pet renders non-blank content from the real bundled sheet
 * asset (`assets/pet/default/idle.png`), exercising the exact composable `PetOverlayService`
 * wires in place of the deleted magenta placeholder. Constructs [PetOverlayStateHolder] directly
 * (no Hilt graph) against the real decoder and application asset manager — full
 * `PetOverlayService`/`WindowManager` compositing is verified manually on `emulator-5554` (see
 * apply-progress evidence), since standing up a real overlay window from an instrumented test
 * requires the same runtime permission grant this suite cannot request headlessly.
 */
/**
 * This test renders the pet, not its placement, so it supplies a repository that has never stored
 * a position. `null` is the honest value for that — never a fabricated coordinate.
 */
private object NoPositionRepository : OverlayPositionRepository {
    override val position: Flow<OverlayPositionFraction?> = flowOf(null)
    override val normalizations: Flow<Unit> = emptyFlow()
    override suspend fun save(position: OverlayPositionFraction) = Unit
}

class PetOverlayRendersTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun idlePetRendersNonBlankContentFromTheRealBundledAsset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = 2048)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val holder = PetOverlayStateHolder(
            context = context,
            decoder = decoder,
            manifestReader = BuiltInCharacterManifestReader(context),
            config = PetAnimationConfig(frameIntervalMillis = 150L),
            screenStateMonitor = ScreenStateMonitor(context, scope),
            positionRepository = NoPositionRepository,
        )

        // The bundled asset must decode successfully — if this fails, the asset itself is broken,
        // which is a build problem, not a rendering one.
        assertTrue(
            "expected the bundled pet/default/idle.png to decode successfully",
            holder.sheetResult is SpriteSheetResult.Loaded,
        )

        composeTestRule.setContent { PetOverlay(holder) }

        val bitmap = composeTestRule.onRoot().captureToImage().asAndroidBitmap()
        var foundNonTransparentPixel = false
        for (x in 0 until bitmap.width step 4) {
            for (y in 0 until bitmap.height step 4) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha != 0) {
                    foundNonTransparentPixel = true
                }
            }
        }
        assertTrue("expected PetOverlay to draw visible (non-transparent) content", foundNonTransparentPixel)
    }
}
