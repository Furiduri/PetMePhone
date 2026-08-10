package com.gcatcode.petmephone.core.domain.pet.sprite

private const val ROW_COUNT = 6

/**
 * A validated [grid] plus each row's usable frame count after the trailing-transparent-cell
 * clamp (see `TransparentCellScanner` in `:feature:overlay`, which is the only producer of
 * [frameCounts] outside tests). Pure integer arithmetic, no allocations — safe to call from a
 * per-frame draw path.
 */
data class SpriteLayout(val grid: SpriteGrid, val frameCounts: List<Int>) {

    init {
        require(frameCounts.size == ROW_COUNT) {
            "frameCounts must have exactly $ROW_COUNT entries, one per PetSpriteRow, got ${frameCounts.size}"
        }
    }

    /** Left edge, in pixels, of [row]'s [frame]-th cell. */
    fun cellLeftPx(row: PetSpriteRow, frame: Int): Int = frame * grid.cellSizePx

    /** Top edge, in pixels, of [row]'s cells. */
    fun cellTopPx(row: PetSpriteRow): Int = row.ordinal * grid.cellSizePx

    /** Convenience accessor for the row this slice renders. */
    val idleFrameCount: Int get() = frameCounts[PetSpriteRow.IDLE.ordinal]
}
