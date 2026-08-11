package com.gcatcode.petmephone.feature.overlay.sprite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteGrid
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TransparentCellScannerTest {

    private val cellSizePx = 8
    private val grid = SpriteGrid(cellSizePx = cellSizePx, columns = 6, rows = 1)

    private fun decode(bytes: ByteArray): Bitmap =
        requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))

    @Test
    fun `an 8-cell row with the last 4 cells transparent reports frame count 4`() {
        val bitmap = decode(SpriteFixtures.validSheetBytes(cellSizePx = cellSizePx, columns = 8, opaqueFrames = 4))
        val grid8Col = SpriteGrid(cellSizePx = cellSizePx, columns = 8, rows = 1)

        val count = TransparentCellScanner.scan(bitmap, grid8Col)

        assertEquals(4, count)
    }

    @Test
    fun `a row with no transparent cells equals the column count`() {
        val bitmap = decode(SpriteFixtures.validSheetBytes(cellSizePx = cellSizePx, columns = 6, opaqueFrames = 6))

        val count = TransparentCellScanner.scan(bitmap, grid)

        assertEquals(6, count)
    }

    @Test
    fun `an all-transparent sheet reports frame count 0`() {
        val bitmap = decode(SpriteFixtures.emptySheetBytes(cellSizePx = cellSizePx, columns = 6))

        val count = TransparentCellScanner.scan(bitmap, grid)

        assertEquals(0, count)
    }

    @Test
    fun `a multi-row sheet with the last row fully transparent clamps to the prior row's end`() {
        val multiRowGrid = SpriteGrid(cellSizePx = cellSizePx, columns = 6, rows = 6)
        // 30 opaque frames (rows 0-4 full) then row 5 fully transparent.
        val bitmap = decode(
            SpriteFixtures.multiRowSheetBytes(cellSizePx = cellSizePx, columns = 6, rows = 6, opaqueFrames = 30),
        )

        val count = TransparentCellScanner.scan(bitmap, multiRowGrid)

        assertEquals(30, count)
    }

    @Test
    fun `a fully opaque 6x6 sheet reports all 36 frames`() {
        val multiRowGrid = SpriteGrid(cellSizePx = cellSizePx, columns = 6, rows = 6)
        val bitmap = decode(
            SpriteFixtures.multiRowSheetBytes(cellSizePx = cellSizePx, columns = 6, rows = 6, opaqueFrames = 36),
        )

        val count = TransparentCellScanner.scan(bitmap, multiRowGrid)

        assertEquals(36, count)
    }
}
