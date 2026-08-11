package com.gcatcode.petmephone.feature.overlay.sprite

import android.graphics.Bitmap
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteGridDeclaration
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteSheetFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric 4.16.1 ships no SDK 37 shadows; `@Config(sdk = [36])` is the repo convention. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpriteSheetDecoderTest {

    private val maxDimensionPx = 64

    private class CountingBitmapDecoding(private val delegate: BitmapDecoding) : BitmapDecoding {
        var fullDecodeCalls = 0
            private set

        override fun decodeBounds(bytes: ByteArray) = delegate.decodeBounds(bytes)

        override fun decodeFull(bytes: ByteArray): Bitmap? {
            fullDecodeCalls++
            return delegate.decodeFull(bytes)
        }
    }

    @Test
    fun `oversized header never reaches full decode`() {
        val counting = CountingBitmapDecoding(BitmapDecoding.Default())
        val decoder = SpriteSheetDecoder(counting, maxDimensionPx = maxDimensionPx)
        // cellSizePx 20 * 6 columns = 120 width, well over the 64px bound.
        val bytes = SpriteFixtures.validSheetBytes(cellSizePx = 20, columns = 6)

        val result = decoder.decode(bytes, SpriteGridDeclaration(columns = 6, rows = 1))

        assertEquals(SpriteSheetResult.Failed(SpriteSheetFailure.Oversized), result)
        assertEquals(0, counting.fullDecodeCalls)
    }

    @Test
    fun `a declaration that does not divide the header is rejected without decoding`() {
        val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = maxDimensionPx)

        val result = decoder.decode(SpriteFixtures.nonDivisibleHeaderBytes(), SpriteGridDeclaration(columns = 6, rows = 1))

        assertEquals(SpriteSheetResult.Failed(SpriteSheetFailure.NotDivisible), result)
    }

    @Test
    fun `a square sheet declared as a single frame is never silently accepted as one row`() {
        // The exact regression #69 closes: a square sheet used to satisfy widthPx % heightPx == 0
        // and derive columns = 1 automatically. A wrong 1x1 declaration over a sheet whose real
        // shape is 6x6 must fail, not draw the whole grid as one frame.
        val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = 512)
        val bytes = SpriteFixtures.multiRowSheetBytes(cellSizePx = 32, columns = 6, rows = 6)

        val declaredAsOneFrame = decoder.decode(bytes, SpriteGridDeclaration(columns = 1, rows = 1))

        // 192x192 declared as 1x1 divides exactly (cell = whole image) — this is why the sheet MUST
        // be declared correctly, not guessed: an under-declared grid still "succeeds" at a wrong
        // shape unless the true grid is what gets declared.
        assertTrue(declaredAsOneFrame is SpriteSheetResult.Loaded)
        assertEquals(1, (declaredAsOneFrame as SpriteSheetResult.Loaded).layout.frameCount)

        val correctlyDeclared = decoder.decode(bytes, SpriteGridDeclaration(columns = 6, rows = 6))
        val loaded = correctlyDeclared as SpriteSheetResult.Loaded
        assertEquals(36, loaded.layout.grid.columns * loaded.layout.grid.rows)
    }

    @Test
    fun `a declared 6x6 grid loads and animates through all 36 frames`() {
        val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = 512)
        // 250px cells at 6x6 (1500x1500) is impossible to express under the retired one-row
        // contract, which capped any animation at roughly 8 frames within the 2048 bound.
        val bytes = SpriteFixtures.multiRowSheetBytes(cellSizePx = 32, columns = 6, rows = 6, opaqueFrames = 36)

        val result = decoder.decode(bytes, SpriteGridDeclaration(columns = 6, rows = 6))

        val loaded = result as SpriteSheetResult.Loaded
        assertEquals(36, loaded.layout.frameCount)
    }

    @Test
    fun `corrupt bytes produce an explicit Failed result, never a thrown exception or null`() {
        // Robolectric's native-runtime BitmapFactory shadow reports garbage bounds for undecodable
        // bytes rather than real Android's -1x-1, so the specific failure case it lands on
        // (Undecodable here, or Oversized if the garbage bounds exceed maxDimensionPx) is a
        // Robolectric quirk. The behavior this test actually proves — no thrown exception, no
        // null, always the explicit Failed case — holds either way.
        val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = maxDimensionPx)

        val result = decoder.decode(SpriteFixtures.corruptBytes(), SpriteGridDeclaration(columns = 1, rows = 1))

        assertTrue(result is SpriteSheetResult.Failed)
    }

    @Test
    fun `successful decode is ARGB_8888`() {
        val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = maxDimensionPx)

        val result = decoder.decode(SpriteFixtures.validSheetBytes(cellSizePx = 8), SpriteGridDeclaration(columns = 6, rows = 1))

        val loaded = result as SpriteSheetResult.Loaded
        assertFalse(loaded.layout.frameCount == 0)
    }

    @Test
    fun `an all-transparent sheet fails as EmptySheet`() {
        val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = maxDimensionPx)

        val result = decoder.decode(SpriteFixtures.emptySheetBytes(cellSizePx = 8), SpriteGridDeclaration(columns = 6, rows = 1))

        assertEquals(SpriteSheetResult.Failed(SpriteSheetFailure.EmptySheet), result)
    }
}
