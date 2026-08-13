package com.gcatcode.petmephone.feature.overlay.quickmenu

/**
 * The quick-menu card's width, the CEILING on its height, and the gap it opens away from the pet.
 *
 * This supersedes design decision 11's fixed height. That decision existed to avoid measuring the
 * composition ourselves — which needs the view already attached, so the card would appear at a
 * wrong position and visibly jump, the bug `[POS-5]` forbids for the pet. A fixed height avoided
 * the jump and introduced a worse failure: it was guessed twice and wrong twice, the first guess
 * putting the launch button outside the window entirely, where the card looked like it had none.
 *
 * The window is now `WRAP_CONTENT` in height, so the window manager sizes it and we never measure.
 * [maxCardHeightDp] only bounds how far it may grow before the content scrolls, so a small screen
 * or a large font scale can never make part of the card unreachable.
 *
 * `@Provides`-only in `OverlayModule`, per the standing injected-config rule — these three numbers
 * are not a product reference yet (`design.md`'s open questions), so rebalancing stays a value
 * change here, never a code hunt.
 */
data class QuickMenuConfig(
    val cardWidthDp: Int,
    val maxCardHeightDp: Int,
    val gapDp: Int,
)
