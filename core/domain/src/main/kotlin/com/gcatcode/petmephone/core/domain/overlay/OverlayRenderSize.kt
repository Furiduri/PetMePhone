package com.gcatcode.petmephone.core.domain.overlay

/**
 * The named cap on the overlay's rendered size, in pixels. Replaces the former
 * `OverlayWindowParams.PLACEHOLDER_SIZE_PX` constant — same number, now a named contract with a
 * test (design decision 11), instead of a constant whose own name admitted it was a placeholder.
 */
object OverlayRenderSize {
    const val MAX_RENDER_SIZE_PX = 220
}
