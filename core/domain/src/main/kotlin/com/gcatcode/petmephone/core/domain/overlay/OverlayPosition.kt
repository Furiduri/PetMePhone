package com.gcatcode.petmephone.core.domain.overlay

/**
 * Top/start-relative offset of the overlay window, in pixels. This is the *source of truth* the
 * service mirrors into `WindowManager.LayoutParams` on every collection; no service field is ever
 * authoritative for it. Mutation (drag) is a separate issue's concern.
 */
data class OverlayPosition(val x: Int, val y: Int) {
    companion object {
        /** Used only where no position has ever been persisted; see [OverlayPositionRepository]. */
        val DEFAULT = OverlayPosition(x = 100, y = 300)
    }
}
