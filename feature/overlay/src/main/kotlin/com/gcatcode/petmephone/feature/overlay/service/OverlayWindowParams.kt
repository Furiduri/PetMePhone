package com.gcatcode.petmephone.feature.overlay.service

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import com.gcatcode.petmephone.core.domain.overlay.OverlayPosition

/**
 * Pure `LayoutParams` construction, pulled out of the service so it is unit-testable on its own
 * (issue #13's Robolectric test-case note): `TYPE_APPLICATION_OVERLAY`, both `FLAG_NOT_FOCUSABLE`
 * (no key/IME focus stolen from the app underneath) and `FLAG_NOT_TOUCH_MODAL` (touches outside
 * the pet's bounds pass through), `PixelFormat.TRANSLUCENT`. Deliberately does not set
 * `FLAG_LAYOUT_NO_LIMITS` (off-screen dragging is the drag issue's call) or
 * `FLAG_WATCH_OUTSIDE_TOUCH` (belongs to the quick-menu window, not this one).
 */
internal object OverlayWindowParams {

    /** Side length, in pixels, of the placeholder view. Real sizing arrives with the Compose host (#14). */
    const val PLACEHOLDER_SIZE_PX = 220

    fun create(position: OverlayPosition): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            PLACEHOLDER_SIZE_PX,
            PLACEHOLDER_SIZE_PX,
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
        params.x = params.x.coerceIn(0, (screenWidthPx - PLACEHOLDER_SIZE_PX).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screenHeightPx - PLACEHOLDER_SIZE_PX).coerceAtLeast(0))
    }
}
