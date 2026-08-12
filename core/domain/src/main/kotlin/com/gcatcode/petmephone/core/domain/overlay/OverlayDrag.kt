package com.gcatcode.petmephone.core.domain.overlay

/** Horizontal screen edge the pet can rest against after a drag. Snap is horizontal-only. */
enum class ScreenEdge { LEFT, RIGHT }

/**
 * Resolves the nearest horizontal edge for a pet resting at [xPx], given the current
 * [screenWidthPx] and the pet's [renderSizePx]. Compares the pet's centre against the screen's
 * centre: an exact tie snaps [ScreenEdge.RIGHT] (design decision 6 — the unplaced resting corner
 * is bottom-right, so right is the pet's home side).
 */
fun nearestEdge(xPx: Int, screenWidthPx: Int, renderSizePx: Int): ScreenEdge {
    val petCentreX = xPx + renderSizePx / 2f
    val screenCentreX = screenWidthPx / 2f
    return if (petCentreX < screenCentreX) ScreenEdge.LEFT else ScreenEdge.RIGHT
}

/**
 * Whether the movement described by [dxPx]/[dyPx] exceeds [slopPx] — the tap-versus-drag
 * discrimination threshold. Movement exactly at the slop distance is NOT a drag (the design
 * requires the distance to strictly exceed, never equal).
 */
fun exceedsSlop(dxPx: Float, dyPx: Float, slopPx: Int): Boolean {
    val distance = kotlin.math.sqrt(dxPx * dxPx + dyPx * dyPx)
    return distance > slopPx.toFloat()
}
