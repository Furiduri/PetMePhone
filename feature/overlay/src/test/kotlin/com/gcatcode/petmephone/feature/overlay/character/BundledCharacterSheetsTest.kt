package com.gcatcode.petmephone.feature.overlay.character

import com.gcatcode.petmephone.feature.overlay.sprite.BitmapDecoding
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetDecoder
import com.gcatcode.petmephone.feature.overlay.sprite.SpriteSheetResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Decodes the real bundled character assets through the real manifest reader and decoder.
 *
 * Every other sheet test uses generated fixtures, which are only ever as wrong as the code that
 * generates them. These are the actual PNGs an artist exported, read from `src/main/assets`, so a
 * format change that works on synthetic pixels and fails on real ones is caught here.
 *
 * `default2` in particular is the multi-row case that motivated the declared-grid work: nine frames
 * on a 3 x 3 grid, which as a single 3069 px strip exceeded the 2048 px decode bound outright. If
 * either asset is removed, delete the matching case rather than weakening it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BundledCharacterSheetsTest {

    private val context = RuntimeEnvironment.getApplication()
    private val manifestReader = BuiltInCharacterManifestReader(context)
    private val decoder = SpriteSheetDecoder(BitmapDecoding.Default(), maxDimensionPx = 2048)

    private fun decode(character: String): SpriteSheetResult {
        val manifest = manifestReader.read(character)
        assertTrue("no manifest for $character", manifest is CharacterManifestResult.Found)
        val declaration = (manifest as CharacterManifestResult.Found).declaration
        return context.assets.open("pet/$character/idle.png").use { input ->
            decoder.decode(input.readBytes(), declaration)
        }
    }

    @Test
    fun `the default character decodes as a single row of six frames`() {
        val result = decode("default")

        assertTrue("default failed to decode: $result", result is SpriteSheetResult.Loaded)
        val layout = (result as SpriteSheetResult.Loaded).layout
        assertEquals(6, layout.frameCount)
        assertEquals(1, layout.grid.rows)
        assertEquals(341, layout.grid.cellSizePx)
    }

    @Test
    fun `the default2 character decodes as three rows of three frames`() {
        val result = decode("default2")

        assertTrue("default2 failed to decode: $result", result is SpriteSheetResult.Loaded)
        val layout = (result as SpriteSheetResult.Loaded).layout
        assertEquals("a multi-row sheet must expose every frame, not just the first row", 9, layout.frameCount)
        assertEquals(3, layout.grid.rows)
        assertEquals("cells must stay square across every row", 341, layout.grid.cellSizePx)
    }

    @Test
    fun `the last frame of default2 is read from the bottom row, not clamped to the first`() {
        val layout = (decode("default2") as SpriteSheetResult.Loaded).layout

        // Row-major order: frame 9 of 9 sits in the bottom-right cell of a 3x3 sheet. A layout that
        // silently treated it as one row would report a top of 0 here and draw the wrong pixels.
        assertEquals(2 * 341, layout.cellLeftPx(8))
        assertEquals(2 * 341, layout.cellTopPx(8))
    }
}
