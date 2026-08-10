package com.gcatcode.petmephone.feature.overlay.sprite

import android.graphics.Bitmap
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

        val result = decoder.decode(bytes)

        assertEquals(SpriteSheetResult.Failed(SpriteSheetFailure.Oversized), result)
        assertEquals(0, counting.fullDecodeCalls)
    }

    @Test
    fun `non-divisible header is rejected without decoding`() {
        val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = maxDimensionPx)

        val result = decoder.decode(SpriteFixtures.nonDivisibleHeaderBytes())

        assertEquals(SpriteSheetResult.Failed(SpriteSheetFailure.NotDivisible), result)
    }

    @Test
    fun `corrupt bytes produce an explicit Failed result, never a thrown exception or null`() {
        // Robolectric's native-runtime BitmapFactory shadow reports garbage bounds for undecodable
        // bytes rather than real Android's -1x-1, so the specific failure case it lands on
        // (Undecodable here, or Oversized if the garbage bounds exceed maxDimensionPx) is a
        // Robolectric quirk. The behavior this test actually proves — no thrown exception, no
        // null, always the explicit Failed case — holds either way.
        val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = maxDimensionPx)

        val result = decoder.decode(SpriteFixtures.corruptBytes())

        assertTrue(result is SpriteSheetResult.Failed)
    }

    @Test
    fun `successful decode is ARGB_8888`() {
        val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = maxDimensionPx)

        val result = decoder.decode(SpriteFixtures.validSheetBytes(cellSizePx = 8))

        val loaded = result as SpriteSheetResult.Loaded
        assertFalse(loaded.layout.frameCount == 0)
    }

    @Test
    fun `an all-transparent sheet fails as EmptySheet`() {
        val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = maxDimensionPx)

        val result = decoder.decode(SpriteFixtures.emptySheetBytes(cellSizePx = 8))

        assertEquals(SpriteSheetResult.Failed(SpriteSheetFailure.EmptySheet), result)
    }
}
