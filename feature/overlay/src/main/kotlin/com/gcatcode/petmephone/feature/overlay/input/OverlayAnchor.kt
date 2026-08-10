package com.gcatcode.petmephone.feature.overlay.input

/**
 * The pet's screen-space bounds at the moment a genuine tap (slop never exceeded) is detected.
 * This is the value handed to [OverlayTapListener], shaped so slice 3's quick menu can anchor its
 * popup against the pet's current position without this contract changing shape later — no quick
 * menu is built in this slice, this is only the seam it will consume.
 */
data class OverlayAnchor(
    val xPx: Int,
    val yPx: Int,
    val sizePx: Int,
)
