package com.gcatcode.petmephone.feature.overlay.sprite

import androidx.compose.ui.graphics.asImageBitmap
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteGrid
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteGridResult
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteLayout
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteSheetFailure
import javax.inject.Inject

/**
 * Header-first sprite sheet decoder. Reads only image bounds first (via [BitmapDecoding], zero
 * pixel allocation) and validates them via [SpriteGrid.of] *before* any full decode ever runs —
 * an `Invalid` header result returns immediately, so the full-decode call path is structurally
 * unreachable for a rejected sheet, not merely skipped by a later check.
 *
 * [maxDimensionPx] is the injected safety bound (2048 today), never a literal.
 */
class SpriteSheetDecoder @Inject constructor(
    private val bitmapDecoding: BitmapDecoding,
    @MaxSpriteDimensionPx private val maxDimensionPx: Int,
) {

    fun decode(bytes: ByteArray): SpriteSheetResult {
        val bounds = bitmapDecoding.decodeBounds(bytes)
        if (bounds.widthPx <= 0 || bounds.heightPx <= 0) {
            // BitmapFactory reports -1x-1 bounds when the bytes are not a decodable image at all.
            return SpriteSheetResult.Failed(SpriteSheetFailure.Undecodable)
        }

        val gridResult = SpriteGrid.of(
            widthPx = bounds.widthPx,
            heightPx = bounds.heightPx,
            maxDimensionPx = maxDimensionPx,
        )
        val grid = when (gridResult) {
            is SpriteGridResult.Invalid -> return SpriteSheetResult.Failed(gridResult.failure)
            is SpriteGridResult.Valid -> gridResult.grid
        }

        val bitmap = bitmapDecoding.decodeFull(bytes)
            ?: return SpriteSheetResult.Failed(SpriteSheetFailure.Undecodable)

        val frameCount = TransparentCellScanner.scan(bitmap, grid)
        val layout = SpriteLayout(grid, frameCount)
        if (layout.frameCount == 0) {
            return SpriteSheetResult.Failed(SpriteSheetFailure.EmptySheet)
        }

        return SpriteSheetResult.Loaded(bitmap.asImageBitmap(), layout)
    }
}
