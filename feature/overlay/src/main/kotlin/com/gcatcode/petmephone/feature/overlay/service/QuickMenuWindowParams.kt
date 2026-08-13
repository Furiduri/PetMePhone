package com.gcatcode.petmephone.feature.overlay.service

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuPlacementResult
import com.gcatcode.petmephone.core.domain.overlay.VerticalAnchor
import com.gcatcode.petmephone.core.domain.overlay.OverlayPosition

/**
 * Pure `LayoutParams` construction for the quick-menu card's window, mirroring
 * [OverlayWindowParams]'s shape (`TYPE_APPLICATION_OVERLAY`, `PixelFormat.TRANSLUCENT`,
 * `Gravity.TOP or START`) but differing in two ways: `FLAG_WATCH_OUTSIDE_TOUCH`, which belongs to
 * the card so it can detect a touch outside its own bounds and dismiss (decision 8's
 * `ACTION_OUTSIDE` path) — [OverlayWindowParams]' own kdoc already documents that this flag is
 * deliberately not the pet window's — and, as of #18, focusability itself.
 *
 * **This card's window is focusable and sets `softInputMode = SOFT_INPUT_ADJUST_RESIZE`,
 * reversing this file's prior "non-focusable" framing.** That reversal is a measured decision,
 * not a preference (design.md, decisions 1–2 and measured facts 1–2):
 *
 * 1. `dumpsys window windows` on a Xiaomi Redmi Note 14 Pro (HyperOS 3, API 36, sixteen runs
 *    across three rounds) showed `softInputMode` is a property of the IME *target* window only —
 *    `imeLayeringTarget`, `imeInputTarget`, and `imeControlTarget` all resolve to one window. A
 *    non-focusable card is never that window, so any `softInputMode` value set on a non-focusable
 *    card is a measured no-op: the flag drop and the `softInputMode` value must ship in the same
 *    change, or neither has any effect.
 * 2. `ADJUST_PAN` measured no better than the no-strategy control on that device; `ADJUST_RESIZE`
 *    measured the field fully visible while typing and was approved on device. `ADJUST_PAN` and a
 *    manual keyboard-height reposition were both rejected on that evidence (design decision 1);
 *    `WindowInsets.ime` is never delivered to this window class (measured fact 3), so no
 *    inset-driven layout is possible either.
 *
 * `FLAG_NOT_TOUCH_MODAL` is still set, same as the pet window: the card never consumes touches
 * outside its own bounds. Focusability is fixed at construction only — nothing here or in
 * `QuickMenuWindowController` ever calls `updateViewLayout` to toggle it after `addView` (design
 * decision 2): a flag that can be toggled has an interval where the wrong value is live, and that
 * interval is exactly #18's "left focusable after dismissal" hazard. One value, set once, has no
 * such interval.
 *
 * These two params objects stay independent classes rather than a shared factory with a flag
 * parameter — their windows have independent lifecycles, and [OverlayWindowParams] is never
 * touched by this change (an explicit #18 acceptance criterion, held by
 * `QuickMenuWindowParamsTest`).
 */
internal object QuickMenuWindowParams {

    /**
     * The window is `WRAP_CONTENT` in height, so the card is exactly as tall as its content — a
     * fixed height was guessed twice and wrong twice, the second guess caught by
     * `QuickMenuCardFitsTest` rather than by a user. Content beyond the ceiling scrolls instead of
     * being clipped, so nothing is ever unreachable.
     *
     * The vertical gravity comes from the placement rather than being fixed to `TOP`, which is what
     * lets a wrapping window still hug the pet. Anchored to `TOP` the y is the card's top edge;
     * anchored to `BOTTOM` it is its bottom edge, so a card opening upward grows away from the pet
     * without anyone needing to know how tall it turned out to be.
     */
    fun create(placement: QuickMenuPlacementResult, widthPx: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or when (placement.verticalAnchor) {
                VerticalAnchor.TOP -> Gravity.TOP
                VerticalAnchor.BOTTOM -> Gravity.BOTTOM
                VerticalAnchor.CENTER -> Gravity.CENTER_VERTICAL
            }
            x = placement.xPx
            y = placement.yPx
            // Measured facts 1–2 (design.md): the flag drop above and this value ship together,
            // or neither has any effect — softInputMode is a property of the IME target window
            // only, and a non-focusable window is never that window.
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            // No window animation. Every stock style is applied relative to the window's gravity,
            // so a BOTTOM-anchored card slid downward on dismissal while a TOP-anchored one simply
            // vanished — the same card behaving differently depending on where the pet happened to
            // be. The maintainer confirmed the top behaviour is the wanted one, so the asymmetry is
            // removed by removing its cause rather than by picking another style with the same
            // gravity dependence.
            windowAnimations = 0
        }
}
