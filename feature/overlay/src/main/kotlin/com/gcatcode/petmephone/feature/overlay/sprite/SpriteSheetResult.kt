package com.gcatcode.petmephone.feature.overlay.sprite

import androidx.compose.ui.graphics.ImageBitmap
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteLayout
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteSheetFailure

/**
 * Outcome of decoding a sprite sheet. Deliberately not a nullable `Bitmap?` and not a
 * `Result<Bitmap>` (see `design.md`'s rejected-alternatives table): a sealed type forces every
 * consumer to name the failure branch explicitly, so "absence never renders as zero" is enforced
 * by the compiler rather than by review.
 */
sealed interface SpriteSheetResult {
    data class Loaded(val bitmap: ImageBitmap, val layout: SpriteLayout) : SpriteSheetResult
    data class Failed(val failure: SpriteSheetFailure) : SpriteSheetResult
}
