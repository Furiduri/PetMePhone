package com.gcatcode.petmephone.core.domain.overlay

/**
 * The pet's on-screen anchor for quick-menu placement, in pixels. Deliberately its own type
 * rather than reusing `OverlayAnchor` (`:feature:overlay`, which cannot be imported here) — the
 * controller maps into this at the call site (design decision 10).
 */
data class QuickMenuAnchor(val xPx: Int, val yPx: Int, val sizePx: Int)

/** System-bar and display-cutout insets, in pixels, resolved via the API 30+ `WindowMetrics` path. */
data class ScreenInsets(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Which screen edge the card's [QuickMenuPlacement.place] result is measured from, vertically. */
enum class VerticalAnchor {
    /** `y` is the distance from the top of the screen to the card's TOP edge. */
    TOP,

    /** `y` is the distance from the bottom of the screen to the card's BOTTOM edge. */
    BOTTOM,

    /** `y` is the offset of the card's centre from the screen's vertical centre. */
    CENTER,
}

/** Where the card goes, and which edge its `y` is measured from. */
data class QuickMenuPlacementResult(
    val xPx: Int,
    val yPx: Int,
    val verticalAnchor: VerticalAnchor,
)

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
     * **The vertical result never depends on the card's height**, because the card's window wraps
     * its content and its real height is unknown until it is laid out. Opening upward returns a
     * [VerticalAnchor.BOTTOM] result so the window grows away from the pet from a known bottom
     * edge; a height-based top offset would place the card as if it were as tall as its ceiling and
     * leave a visible gap between the card and the pet — which is exactly the bug this replaces.
     *
     * [maxCardHeightPx] therefore only bounds the clamp, never the position itself.
     */
    fun place(
        anchor: QuickMenuAnchor,
        screenWidthPx: Int,
        screenHeightPx: Int,
        cardWidthPx: Int,
        maxCardHeightPx: Int,
        insets: ScreenInsets,
        gapPx: Int,
    ): QuickMenuPlacementResult {
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
        val maxX = maxOf(usableLeft, usableRight - cardWidthPx)
        val x = rawX.coerceIn(usableLeft, maxX)

        return when {
            // Opens downward: the card's TOP edge sits just below the pet. Height is irrelevant.
            bottomSpace > topSpace -> QuickMenuPlacementResult(
                xPx = x,
                yPx = (anchorBottom + gapPx).coerceIn(usableTop, maxOf(usableTop, usableBottom)),
                verticalAnchor = VerticalAnchor.TOP,
            )

            // Opens upward: the card's BOTTOM edge sits just above the pet, measured from the
            // screen's bottom so the window grows upward without anyone knowing its height.
            topSpace > bottomSpace -> QuickMenuPlacementResult(
                xPx = x,
                yPx = (screenHeightPx - (anchor.yPx - gapPx))
                    .coerceIn(insets.bottom, maxOf(insets.bottom, screenHeightPx - usableTop)),
                verticalAnchor = VerticalAnchor.BOTTOM,
            )

            // Balanced: centre the card on the pet's centre, again without needing its height.
            else -> QuickMenuPlacementResult(
                xPx = x,
                yPx = (anchor.yPx + anchor.sizePx / 2) - screenHeightPx / 2,
                verticalAnchor = VerticalAnchor.CENTER,
            )
        }
    }
}
