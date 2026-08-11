package com.gcatcode.petmephone.core.domain.overlay

/**
 * The persisted position type — a fraction of the **travel range** (`bounds - renderSize`), not of
 * raw screen width or height (design decision 4). `1.0` therefore always means "flush against the
 * far edge" regardless of screen size, which is what lets a fraction stored on one device restore
 * to a sensible on-screen position on another.
 *
 * [OverlayPosition] stays the pixel-typed runtime value `WindowManager.LayoutParams` needs; this is
 * the only type DataStore ever sees. Two types make the pixel/fraction boundary a compile error
 * rather than a convention someone has to remember.
 */
data class OverlayPositionFraction(val x: Float, val y: Float) {

    /**
     * Resolves this fraction into pixels against the given screen bounds and render size. The
     * travel range on each axis is `bounds - renderSizePx`, clamped to at least `0` so a render
     * size larger than the screen never produces a negative range.
     */
    fun toPixels(widthPx: Int, heightPx: Int, renderSizePx: Int): OverlayPosition {
        val travelX = (widthPx - renderSizePx).coerceAtLeast(0)
        val travelY = (heightPx - renderSizePx).coerceAtLeast(0)
        return OverlayPosition(
            x = (x * travelX).toInt(),
            y = (y * travelY).toInt(),
        )
    }

    companion object {
        /**
         * Inverse of [toPixels]. When the travel range on an axis is `0` (render size fills or
         * exceeds the screen on that axis), the fraction is defined as `0f` — there is no travel to
         * express a position along.
         */
        fun ofPixels(
            position: OverlayPosition,
            widthPx: Int,
            heightPx: Int,
            renderSizePx: Int,
        ): OverlayPositionFraction {
            val travelX = (widthPx - renderSizePx).coerceAtLeast(0)
            val travelY = (heightPx - renderSizePx).coerceAtLeast(0)
            return OverlayPositionFraction(
                x = if (travelX == 0) 0f else position.x.toFloat() / travelX,
                y = if (travelY == 0) 0f else position.y.toFloat() / travelY,
            )
        }

        /**
         * Validates a candidate pair read from storage. Returns `null` — never a fabricated `0f` —
         * unless *both* values are present, finite, and within `0f..1f`. This is the only
         * constructor call site the repository is allowed to use for a value coming off disk, so a
         * half-written pair, a `NaN`, or an out-of-range float can never masquerade as a real
         * position.
         */
        fun validOrNull(x: Float?, y: Float?): OverlayPositionFraction? {
            if (x == null || y == null) return null
            if (!x.isFinite() || !y.isFinite()) return null
            if (x < 0f || x > 1f || y < 0f || y > 1f) return null
            return OverlayPositionFraction(x, y)
        }
    }
}
