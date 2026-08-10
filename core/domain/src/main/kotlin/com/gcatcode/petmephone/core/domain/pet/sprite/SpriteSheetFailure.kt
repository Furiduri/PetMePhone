package com.gcatcode.petmephone.core.domain.pet.sprite

/**
 * Every way a sprite sheet can fail to become a usable [SpriteLayout]. Distinct cases so callers
 * can tell "too big" from "wrong shape" from "not an image" from "no idle frames" — collapsing
 * these into a single boolean or a nullable result is exactly what "absence never renders as
 * zero" forbids.
 */
sealed interface SpriteSheetFailure {
    /** Wider or taller than the injected `maxDimensionPx` bound, rejected at header read. */
    data object Oversized : SpriteSheetFailure

    /** `height % 6 != 0`, or `width % (height / 6) != 0` — a remainder, never truncated. */
    data object NotDivisible : SpriteSheetFailure

    /** `BitmapFactory.decode*` (or equivalent) returned null: the bytes are not a valid image. */
    data object Undecodable : SpriteSheetFailure

    /** Row 0 (IDLE) has zero usable frames after the trailing-transparent-cell clamp. */
    data object EmptyIdleRow : SpriteSheetFailure
}
