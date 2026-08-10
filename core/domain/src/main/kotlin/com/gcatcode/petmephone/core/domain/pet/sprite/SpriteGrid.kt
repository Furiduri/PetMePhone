package com.gcatcode.petmephone.core.domain.pet.sprite

/**
 * A validated sprite sheet grid: one animation, one row of square [cellSizePx] cells, [columns] of
 * them. Construct only through [of] — there is no public constructor, so an `Invalid` grid can
 * never exist as a live [SpriteGrid] instance.
 */
data class SpriteGrid(val cellSizePx: Int, val columns: Int) {

    companion object {
        /**
         * Validates [widthPx]×[heightPx] against the sheet contract and, on success, derives the
         * grid by division alone: cell side = image height, frame count = image width / image
         * height. Order matters and is part of the contract: oversize is checked before
         * divisibility, so a fixture that is both oversized and non-divisible fails as
         * [SpriteSheetFailure.Oversized].
         *
         * [maxDimensionPx] is an injected safety bound (2048 today per `design.md`), never a
         * literal inside this function.
         */
        fun of(widthPx: Int, heightPx: Int, maxDimensionPx: Int): SpriteGridResult {
            if (widthPx > maxDimensionPx || heightPx > maxDimensionPx) {
                return SpriteGridResult.Invalid(SpriteSheetFailure.Oversized)
            }
            if (heightPx <= 0 || widthPx % heightPx != 0) {
                // A remainder is NEVER truncated to a whole column count — that would silently
                // clip the last frame rather than reject the sheet.
                return SpriteGridResult.Invalid(SpriteSheetFailure.NotDivisible)
            }
            val columns = widthPx / heightPx
            return SpriteGridResult.Valid(SpriteGrid(cellSizePx = heightPx, columns = columns))
        }
    }
}

/** Result of [SpriteGrid.of]: either a valid grid, or the specific reason it was rejected. */
sealed interface SpriteGridResult {
    data class Valid(val grid: SpriteGrid) : SpriteGridResult
    data class Invalid(val failure: SpriteSheetFailure) : SpriteGridResult
}
