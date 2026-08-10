package com.gcatcode.petmephone.core.domain.overlay

/**
 * Top/start-relative offset of the overlay window, in pixels. This is the *source of truth* the
 * service mirrors into `WindowManager.LayoutParams` on every collection; no service field is ever
 * authoritative for it. Mutation (drag) is a separate issue's concern.
 */
data class OverlayPosition(val x: Int, val y: Int) {
    companion object {
        /**
         * Where the pet rests before the user has ever placed it: the bottom-right corner, inset
         * by [RESTING_MARGIN_PX] so it does not sit flush against the screen edges.
         *
         * There is deliberately no `DEFAULT` constant. A fixed coordinate pair cannot express a
         * corner, because a corner depends on the screen it is on — so the resting place is
         * computed against real bounds rather than guessed at compile time. See
         * [OverlayPositionRepository.position] for why absence is a `null` rather than a stand-in
         * coordinate.
         */
        fun restingCorner(screenWidthPx: Int, screenHeightPx: Int, overlaySizePx: Int) =
            OverlayPosition(
                x = (screenWidthPx - overlaySizePx - RESTING_MARGIN_PX).coerceAtLeast(0),
                y = (screenHeightPx - overlaySizePx - RESTING_MARGIN_PX).coerceAtLeast(0),
            )

        /** Inset from the screen edges, in pixels, for the resting corner. */
        const val RESTING_MARGIN_PX = 16
    }
}
