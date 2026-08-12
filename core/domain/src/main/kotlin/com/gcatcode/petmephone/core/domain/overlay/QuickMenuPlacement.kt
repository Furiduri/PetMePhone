package com.gcatcode.petmephone.core.domain.overlay

/**
 * The pet's on-screen anchor for quick-menu placement, in pixels. Deliberately its own type
 * rather than reusing `OverlayAnchor` (`:feature:overlay`, which cannot be imported here) — the
 * controller maps into this at the call site (design decision 10).
 */
data class QuickMenuAnchor(val xPx: Int, val yPx: Int, val sizePx: Int)

/** System-bar and display-cutout insets, in pixels, resolved via the API 30+ `WindowMetrics` path. */
data class ScreenInsets(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Pure positioning math for the quick-menu card. No `android.*` / `androidx.*` import exists here
 * or may ever be added — the controller is the only caller allowed to touch a real `WindowManager`
 * or `WindowMetrics` (`quick-menu-positioning` requirement).
 */
object QuickMenuPlacement {

    /**
     * Computes the card's top/start-relative offset.
     *
     * Each axis is decided independently by comparing the space available on either side of the
     * anchor, inside the usable bounds (screen bounds with [insets] subtracted):
     * - **More space on one side**: the card opens toward that side, offset from the anchor's
     *   near edge by [gapPx]. This is what produces the diagonal placement at a screen corner —
     *   both axes are imbalanced there, so both axes open toward their larger side.
     * - **Exactly equal space on both sides** (the anchor sits centered on that axis, e.g. a
     *   mid-edge position): the card is centered on the anchor's center for that axis instead of
     *   being pushed to an edge with a gap, which is what a tie-break offset would otherwise do.
     *
     * The result is then clamped so the card never exceeds the usable bounds, even when
     * [cardWidthPx] or [cardHeightPx] is larger than the space available in the chosen direction.
     */
    fun place(
        anchor: QuickMenuAnchor,
        screenWidthPx: Int,
        screenHeightPx: Int,
        cardWidthPx: Int,
        cardHeightPx: Int,
        insets: ScreenInsets,
        gapPx: Int,
    ): OverlayPosition {
        val usableLeft = insets.left
        val usableTop = insets.top
        val usableRight = screenWidthPx - insets.right
        val usableBottom = screenHeightPx - insets.bottom

        val anchorRight = anchor.xPx + anchor.sizePx
        val anchorBottom = anchor.yPx + anchor.sizePx

        val leftSpace = anchor.xPx - usableLeft
        val rightSpace = usableRight - anchorRight
        val topSpace = anchor.yPx - usableTop
        val bottomSpace = usableBottom - anchorBottom

        val rawX = when {
            rightSpace > leftSpace -> anchorRight + gapPx
            leftSpace > rightSpace -> anchor.xPx - gapPx - cardWidthPx
            else -> anchor.xPx + anchor.sizePx / 2 - cardWidthPx / 2
        }
        val rawY = when {
            bottomSpace > topSpace -> anchorBottom + gapPx
            topSpace > bottomSpace -> anchor.yPx - gapPx - cardHeightPx
            else -> anchor.yPx + anchor.sizePx / 2 - cardHeightPx / 2
        }

        val maxX = maxOf(usableLeft, usableRight - cardWidthPx)
        val maxY = maxOf(usableTop, usableBottom - cardHeightPx)

        return OverlayPosition(
            x = rawX.coerceIn(usableLeft, maxX),
            y = rawY.coerceIn(usableTop, maxY),
        )
    }
}
