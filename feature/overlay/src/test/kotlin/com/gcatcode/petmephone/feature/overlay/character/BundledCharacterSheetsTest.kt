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
 * `default2` in particular is the two-row case that motivated the declared-grid work: 12 frames a
 * single row could not hold. If either asset is removed, delete the matching case rather than
 * weakening it.
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
    fun `the default2 character decodes as two rows of six frames`() {
        val result = decode("default2")

        assertTrue("default2 failed to decode: $result", result is SpriteSheetResult.Loaded)
        val layout = (result as SpriteSheetResult.Loaded).layout
        assertEquals("a two-row sheet must expose every frame, not just the first row", 12, layout.frameCount)
        assertEquals(2, layout.grid.rows)
        assertEquals("cells must stay square across both rows", 341, layout.grid.cellSizePx)
    }

    @Test
    fun `frame twelve of default2 is read from the second row, not clamped to the first`() {
        val layout = (decode("default2") as SpriteSheetResult.Loaded).layout

        // Row-major order: the last frame sits in the bottom-right cell. A layout that silently
        // treated the sheet as one row would report a top of 0 here and draw the wrong pixels.
        assertEquals(5 * 341, layout.cellLeftPx(11))
        assertEquals(341, layout.cellTopPx(11))
    }
}
