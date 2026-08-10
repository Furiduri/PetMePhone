package com.gcatcode.petmephone.feature.overlay.service

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import com.gcatcode.petmephone.core.domain.overlay.OverlayPosition
import com.gcatcode.petmephone.core.domain.overlay.OverlayRenderSize

/**
 * Pure `LayoutParams` construction, pulled out of the service so it is unit-testable on its own
 * (issue #13's Robolectric test-case note): `TYPE_APPLICATION_OVERLAY`, both `FLAG_NOT_FOCUSABLE`
 * (no key/IME focus stolen from the app underneath) and `FLAG_NOT_TOUCH_MODAL` (touches outside
 * the pet's bounds pass through), `PixelFormat.TRANSLUCENT`. Deliberately does not set
 * `FLAG_LAYOUT_NO_LIMITS` (off-screen dragging is the drag issue's call — see design.md decision
 * 12 for the outcome of that procedure) or `FLAG_WATCH_OUTSIDE_TOUCH` (belongs to the quick-menu
 * window, not this one).
 */
internal object OverlayWindowParams {

    /** Side length, in pixels, of the rendered overlay window. Derived from the named cap. */
    val SIZE_PX = OverlayRenderSize.MAX_RENDER_SIZE_PX

    fun create(position: OverlayPosition): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            SIZE_PX,
            SIZE_PX,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.x
            y = position.y
        }

    /** Re-clamps params already attached to a window to the current screen bounds after rotation. */
    fun clampToBounds(params: WindowManager.LayoutParams, screenWidthPx: Int, screenHeightPx: Int) {
        params.x = params.x.coerceIn(0, (screenWidthPx - SIZE_PX).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screenHeightPx - SIZE_PX).coerceAtLeast(0))
    }
}
