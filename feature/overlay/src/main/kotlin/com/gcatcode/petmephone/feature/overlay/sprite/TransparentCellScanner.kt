package com.gcatcode.petmephone.feature.overlay.sprite

import android.graphics.Bitmap
import com.gcatcode.petmephone.core.domain.pet.sprite.PetSpriteRow
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteGrid

/**
 * Scans each row from its last cell backward for fully-transparent cells (every pixel
 * `alpha == 0`); a trailing run of such cells is excluded from that row's frame count. The first
 * non-fully-transparent cell from the end fixes the count — see `pet-sprite-sheet` spec, "Trailing
 * fully-transparent cells clamp a row's frame count".
 */
object TransparentCellScanner {

    /** Frame counts for all six [PetSpriteRow]s, in fixed row order. */
    fun scan(bitmap: Bitmap, grid: SpriteGrid): List<Int> =
        PetSpriteRow.entries.map { row -> frameCountFor(bitmap, grid, row) }

    private fun frameCountFor(bitmap: Bitmap, grid: SpriteGrid, row: PetSpriteRow): Int {
        val top = row.ordinal * grid.cellSizePx
        for (frame in grid.columns - 1 downTo 0) {
            if (!isCellFullyTransparent(bitmap, grid, left = frame * grid.cellSizePx, top = top)) {
                return frame + 1
            }
        }
        return 0
    }

    private fun isCellFullyTransparent(bitmap: Bitmap, grid: SpriteGrid, left: Int, top: Int): Boolean {
        for (y in top until top + grid.cellSizePx) {
            for (x in left until left + grid.cellSizePx) {
                if ((bitmap.getPixel(x, y) ushr 24) != 0) return false
            }
        }
        return true
    }
}
