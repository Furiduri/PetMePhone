package com.gcatcode.petmephone.core.domain.pet.sprite

/**
 * A validated [grid] plus this sheet's usable frame count after the trailing-transparent-cell
 * clamp (see `TransparentCellScanner` in `:feature:overlay`, which is the only producer of
 * [frameCount] outside tests). One sprite sheet is one animation, [grid] may declare more than one
 * row; frames are ordered left to right, then top to bottom. Pure integer arithmetic, no
 * allocations — safe to call from a per-frame draw path.
 */
data class SpriteLayout(val grid: SpriteGrid, val frameCount: Int) {

    private val maxFrameCount = grid.columns * grid.rows

    init {
        require(frameCount in 0..maxFrameCount) {
            "frameCount must be between 0 and grid.columns * grid.rows ($maxFrameCount), got $frameCount"
        }
    }

    /** Left edge, in pixels, of the [frame]-th cell. */
    fun cellLeftPx(frame: Int): Int = (frame % grid.columns) * grid.cellSizePx

    /** Top edge, in pixels, of the [frame]-th cell. */
    fun cellTopPx(frame: Int): Int = (frame / grid.columns) * grid.cellSizePx
}
