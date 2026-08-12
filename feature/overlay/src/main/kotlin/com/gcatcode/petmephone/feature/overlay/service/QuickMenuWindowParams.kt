package com.gcatcode.petmephone.feature.overlay.service

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import com.gcatcode.petmephone.core.domain.overlay.OverlayPosition

/**
 * Pure `LayoutParams` construction for the quick-menu card's window, mirroring
 * [OverlayWindowParams]'s shape (`TYPE_APPLICATION_OVERLAY`, `PixelFormat.TRANSLUCENT`,
 * `Gravity.TOP or START`) but differing in exactly one flag: `FLAG_WATCH_OUTSIDE_TOUCH`, which
 * belongs to the card so it can detect a touch outside its own bounds and dismiss (decision 8's
 * `ACTION_OUTSIDE` path) — [OverlayWindowParams]' own kdoc already documents that this flag is
 * deliberately not the pet window's.
 *
 * Both `FLAG_NOT_FOCUSABLE` and `FLAG_NOT_TOUCH_MODAL` are set, same as the pet window: the card
 * takes no window focus (design decision 6 — the IME-viability spike gates that cost, not this
 * shell) and never consumes touches outside its own bounds. `FLAG_ALT_FOCUSABLE_IM` is
 * deliberately absent: that flag is only meaningful for a window that is *not*
 * `FLAG_NOT_FOCUSABLE`, so pairing the two here would be noise suggesting a focus story that does
 * not exist in this change.
 *
 * These two params objects stay independent classes rather than a shared factory with a flag
 * parameter — their windows have independent lifecycles, and the card's flags are the ones
 * expected to change first if a later spike-driven change reopens focus (design.md's kdoc note on
 * this exact point).
 */
internal object QuickMenuWindowParams {

    /**
     * [maxHeightPx] bounds the placement math only. The window itself is `WRAP_CONTENT` in height,
     * so the card is exactly as tall as its content: a fixed height was guessed twice and was wrong
     * twice, the second guess caught by `QuickMenuCardFitsTest` rather than by a user. Content that
     * would exceed [maxHeightPx] scrolls instead of being clipped, so nothing is ever unreachable.
     */
    fun create(position: OverlayPosition, widthPx: Int, maxHeightPx: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.x
            y = position.y
        }
}
