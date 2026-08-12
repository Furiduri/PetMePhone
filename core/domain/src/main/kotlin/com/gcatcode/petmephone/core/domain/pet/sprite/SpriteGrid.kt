package com.gcatcode.petmephone.core.domain.pet.sprite

/**
 * A validated sprite sheet grid: [rows] of [columns] square [cellSizePx] cells, read left to right
 * then top to bottom. Construct only through [of] — there is no public constructor, so an
 * `Invalid` grid can never exist as a live [SpriteGrid] instance.
 *
 * The grid is **declared**, never inferred (`design.md` decision 13): a 1500x1500 image is equally
 * consistent with 6x6, 5x5, or 10x10 cells, so the shape has to come from outside the pixels — an
 * import-time user declaration or a bundled character's manifest.
 */
data class SpriteGrid(val cellSizePx: Int, val columns: Int, val rows: Int) {

    companion object {
        /**
         * Validates [widthPx]×[heightPx] against the sheet contract and the caller-supplied
         * [declaration]. Order matters and is part of the contract: oversize is checked before
         * divisibility, so a fixture that is both oversized and non-divisible fails as
         * [SpriteSheetFailure.Oversized].
         *
         * The declared grid must divide the image exactly on both axes AND produce square cells —
         * any disagreement is [SpriteSheetFailure.NotDivisible], never a clamp and never a
         * truncation. This is what makes a square sheet declared as `1 column x 1 row` no longer
         * silently accepted as one frame: `1x1` for a non-square image already fails divisibility
         * in the ordinary case, and a genuinely square sheet must be declared with its real grid to
         * pass at all.
         *
         * [maxDimensionPx] is an injected safety bound (2048 today per `design.md`), never a
         * literal inside this function.
         */
        fun of(
            widthPx: Int,
            heightPx: Int,
            declaration: SpriteGridDeclaration,
            maxDimensionPx: Int,
        ): SpriteGridResult {
            if (widthPx > maxDimensionPx || heightPx > maxDimensionPx) {
                return SpriteGridResult.Invalid(SpriteSheetFailure.Oversized)
            }
            val columns = declaration.columns
            val rows = declaration.rows
            if (columns <= 0 || rows <= 0) {
                return SpriteGridResult.Invalid(SpriteSheetFailure.NotDivisible)
            }
            if (widthPx % columns != 0 || heightPx % rows != 0) {
                // A remainder is NEVER truncated to a whole cell count — that would silently clip
                // the last row/column rather than reject the sheet.
                return SpriteGridResult.Invalid(SpriteSheetFailure.NotDivisible)
            }
            val cellWidthPx = widthPx / columns
            val cellHeightPx = heightPx / rows
            if (cellWidthPx != cellHeightPx) {
                // A non-square cell disagrees with the declaration just as much as a remainder does.
                return SpriteGridResult.Invalid(SpriteSheetFailure.NotDivisible)
            }
            return SpriteGridResult.Valid(SpriteGrid(cellSizePx = cellWidthPx, columns = columns, rows = rows))
        }
    }
}

/** Result of [SpriteGrid.of]: either a valid grid, or the specific reason it was rejected. */
sealed interface SpriteGridResult {
    data class Valid(val grid: SpriteGrid) : SpriteGridResult
    data class Invalid(val failure: SpriteSheetFailure) : SpriteGridResult
}
