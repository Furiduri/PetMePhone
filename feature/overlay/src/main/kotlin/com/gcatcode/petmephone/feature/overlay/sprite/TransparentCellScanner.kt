package com.gcatcode.petmephone.feature.overlay.sprite

import android.graphics.Bitmap
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteGrid

/**
 * Scans a sheet's single row from its last cell backward for fully-transparent cells (every pixel
 * `alpha == 0`); a trailing run of such cells is excluded from the frame count. The first
 * non-fully-transparent cell from the end fixes the count — see `pet-sprite-sheet` spec, "Trailing
 * fully-transparent cells clamp the frame count".
 */
object TransparentCellScanner {

    /** Usable frame count for [grid], after the trailing-transparent-cell clamp. */
    fun scan(bitmap: Bitmap, grid: SpriteGrid): Int {
        for (frame in grid.columns - 1 downTo 0) {
            if (!isCellFullyTransparent(bitmap, grid, left = frame * grid.cellSizePx)) {
                return frame + 1
            }
        }
        return 0
    }

    private fun isCellFullyTransparent(bitmap: Bitmap, grid: SpriteGrid, left: Int): Boolean {
        for (y in 0 until grid.cellSizePx) {
            for (x in left until left + grid.cellSizePx) {
                if ((bitmap.getPixel(x, y) ushr 24) != 0) return false
            }
        }
        return true
    }
}
