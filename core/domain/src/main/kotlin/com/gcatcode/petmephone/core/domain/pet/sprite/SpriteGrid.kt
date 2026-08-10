package com.gcatcode.petmephone.core.domain.pet.sprite

/** Number of fixed rows in every sprite sheet — see [PetSpriteRow]. */
private const val ROW_COUNT = 6

/**
 * A validated sprite sheet grid: square cells of [cellSizePx] and [columns] of them per row.
 * Construct only through [of] — there is no public constructor, so an `Invalid` grid can never
 * exist as a live [SpriteGrid] instance.
 */
data class SpriteGrid(val cellSizePx: Int, val columns: Int) {

    companion object {
        /**
         * Validates [widthPx]×[heightPx] against the sheet contract and, on success, derives the
         * grid by division alone. Order matters and is part of the contract: oversize is checked
         * before divisibility, so a fixture that is both oversized and non-divisible fails as
         * [SpriteSheetFailure.Oversized].
         *
         * [maxDimensionPx] is an injected safety bound (2048 today per `design.md`), never a
         * literal inside this function.
         */
        fun of(widthPx: Int, heightPx: Int, maxDimensionPx: Int): SpriteGridResult {
            if (widthPx > maxDimensionPx || heightPx > maxDimensionPx) {
                return SpriteGridResult.Invalid(SpriteSheetFailure.Oversized)
            }
            if (heightPx % ROW_COUNT != 0) {
                return SpriteGridResult.Invalid(SpriteSheetFailure.NotDivisible)
            }
            val cellSizePx = heightPx / ROW_COUNT
            if (widthPx % cellSizePx != 0) {
                return SpriteGridResult.Invalid(SpriteSheetFailure.NotDivisible)
            }
            val columns = widthPx / cellSizePx
            return SpriteGridResult.Valid(SpriteGrid(cellSizePx = cellSizePx, columns = columns))
        }
    }
}

/** Result of [SpriteGrid.of]: either a valid grid, or the specific reason it was rejected. */
sealed interface SpriteGridResult {
    data class Valid(val grid: SpriteGrid) : SpriteGridResult
    data class Invalid(val failure: SpriteSheetFailure) : SpriteGridResult
}
