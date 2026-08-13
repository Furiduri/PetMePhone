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

    /**
     * Upper bound on the rendered window, not the size itself. The actual side length is derived
     * from the screen — a fixed pixel count is a different physical size on every device: 220px is
     * 84dp at 420dpi and only 67dp at 520dpi, which is why the pet looked correct on one device and
     * too small on another.
     */
    val MAX_SIZE_PX = OverlayRenderSize.MAX_RENDER_SIZE_PX

    fun create(position: OverlayPosition, sizePx: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.x
            y = position.y
        }

    /** Re-clamps params already attached to a window to the current screen bounds after rotation. */
    fun clampToBounds(
        params: WindowManager.LayoutParams,
        screenWidthPx: Int,
        screenHeightPx: Int,
        sizePx: Int = params.width,
    ) {
        params.x = params.x.coerceIn(0, (screenWidthPx - sizePx).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screenHeightPx - sizePx).coerceAtLeast(0))
    }
}
