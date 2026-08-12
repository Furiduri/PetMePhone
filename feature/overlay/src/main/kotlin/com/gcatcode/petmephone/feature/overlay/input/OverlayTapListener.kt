package com.gcatcode.petmephone.feature.overlay.input

/**
 * Invoked by [PetTouchController] on a genuine tap — a touch sequence that never exceeded the
 * slop threshold. No drag, no snap animation, and no window position change accompany this call.
 */
fun interface OverlayTapListener {
    fun onTap(anchor: OverlayAnchor)
}
