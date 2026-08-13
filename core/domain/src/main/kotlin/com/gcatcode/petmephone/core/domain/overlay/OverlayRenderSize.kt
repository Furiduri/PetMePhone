package com.gcatcode.petmephone.core.domain.overlay

/**
 * The ceiling on the overlay's rendered size, in pixels.
 *
 * This used to be 220 and was also the pet's actual size, which made it useless as a ceiling: once
 * the size became a fraction of the screen, `coerceAtMost(220)` would have clamped every device
 * straight back to the old constant and the change would have done nothing. A fixed pixel size is
 * what caused the problem in the first place — 220px is 84dp at 420dpi and 67dp at 520dpi, so the
 * pet looked right on one phone and too small on another.
 *
 * The value is now a genuine upper bound: large enough that no phone reaches it, low enough that a
 * tablet cannot produce an absurd sprite.
 */
object OverlayRenderSize {
    const val MAX_RENDER_SIZE_PX = 480
}
