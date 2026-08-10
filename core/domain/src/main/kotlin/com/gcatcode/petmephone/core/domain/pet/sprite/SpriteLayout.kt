package com.gcatcode.petmephone.core.domain.pet.sprite

/**
 * A validated [grid] plus this sheet's usable frame count after the trailing-transparent-cell
 * clamp (see `TransparentCellScanner` in `:feature:overlay`, which is the only producer of
 * [frameCount] outside tests). One sprite sheet is one animation's single row of frames — there is
 * no row index here, unlike the retired six-row-per-sheet contract. Pure integer arithmetic, no
 * allocations — safe to call from a per-frame draw path.
 */
data class SpriteLayout(val grid: SpriteGrid, val frameCount: Int) {

    init {
        require(frameCount in 0..grid.columns) {
            "frameCount must be between 0 and grid.columns (${grid.columns}), got $frameCount"
        }
    }

    /** Left edge, in pixels, of the [frame]-th cell. */
    fun cellLeftPx(frame: Int): Int = frame * grid.cellSizePx
}
