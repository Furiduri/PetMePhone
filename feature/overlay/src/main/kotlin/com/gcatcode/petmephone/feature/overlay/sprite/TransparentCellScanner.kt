package com.gcatcode.petmephone.feature.overlay.sprite

import android.graphics.Bitmap
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteGrid

/**
 * Scans a sheet's frames — left to right, then top to bottom — from the last cell backward for
 * fully-transparent cells (every pixel `alpha == 0`); a trailing run of such cells is excluded from
 * the frame count. The first non-fully-transparent cell from the end fixes the count — see
 * `pet-sprite-sheet` spec, "Trailing fully-transparent cells clamp the frame count".
 */
object TransparentCellScanner {

    /** Usable frame count for [grid], after the trailing-transparent-cell clamp. */
    fun scan(bitmap: Bitmap, grid: SpriteGrid): Int {
        val totalFrames = grid.columns * grid.rows
        for (frame in totalFrames - 1 downTo 0) {
            val col = frame % grid.columns
            val row = frame / grid.columns
            val left = col * grid.cellSizePx
            val top = row * grid.cellSizePx
            if (!isCellFullyTransparent(bitmap, grid, left = left, top = top)) {
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
