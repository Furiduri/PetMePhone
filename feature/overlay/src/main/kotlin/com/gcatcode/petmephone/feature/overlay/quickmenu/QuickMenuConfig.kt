package com.gcatcode.petmephone.feature.overlay.quickmenu

/**
 * The quick-menu card's fixed size and the gap it opens away from the pet (design decision 11:
 * "Fixed width/height in an injected `QuickMenuConfig` (dp), converted to px by the controller
 * before calling `place`"). Measuring the composition instead would require the view to already
 * be attached, so the card would appear at a wrong position and visibly jump into place — the same
 * bug `[POS-5]` already forbids for the pet.
 *
 * `@Provides`-only in `OverlayModule`, per the standing injected-config rule — these three numbers
 * are not a product reference yet (`design.md`'s open questions), so rebalancing stays a value
 * change here, never a code hunt.
 */
data class QuickMenuConfig(
    val cardWidthDp: Int,
    val cardHeightDp: Int,
    val gapDp: Int,
)
