package com.gcatcode.petmephone.feature.overlay.sprite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.gcatcode.petmephone.core.domain.pet.sprite.PetSpriteRow
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
    private val grid = SpriteGrid(cellSizePx = cellSizePx, columns = 6)

    private fun decode(bytes: ByteArray): Bitmap =
        requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))

    @Test
    fun `an 8-cell row with the last 4 cells transparent reports frame count 4`() {
        val bitmap = decode(SpriteFixtures.validSheetBytes(cellSizePx = cellSizePx, columns = 8, idleOpaqueFrames = 4))
        val grid8Col = SpriteGrid(cellSizePx = cellSizePx, columns = 8)

        val counts = TransparentCellScanner.scan(bitmap, grid8Col)

        assertEquals(4, counts[PetSpriteRow.IDLE.ordinal])
    }

    @Test
    fun `a row with no transparent cells equals the column count`() {
        val bitmap = decode(SpriteFixtures.validSheetBytes(cellSizePx = cellSizePx, columns = 6, idleOpaqueFrames = 6))

        val counts = TransparentCellScanner.scan(bitmap, grid)

        assertEquals(6, counts[PetSpriteRow.IDLE.ordinal])
    }

    @Test
    fun `an all-transparent row 0 reports frame count 0`() {
        val bitmap = decode(SpriteFixtures.emptyIdleRowBytes(cellSizePx = cellSizePx, columns = 6))

        val counts = TransparentCellScanner.scan(bitmap, grid)

        assertEquals(0, counts[PetSpriteRow.IDLE.ordinal])
    }
}
