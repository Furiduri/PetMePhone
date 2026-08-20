package com.gcatcode.petmephone.feature.overlay.service

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuPlacementResult
import com.gcatcode.petmephone.core.domain.overlay.VerticalAnchor
import com.gcatcode.petmephone.core.domain.overlay.OverlayPosition
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickMenuWindowParamsTest {

    @Test
    fun `create is focusable, sets SOFT_INPUT_ADJUST_RESIZE, and keeps FLAG_NOT_TOUCH_MODAL and FLAG_WATCH_OUTSIDE_TOUCH`() {
        val params = QuickMenuWindowParams.create(
            QuickMenuPlacementResult(xPx = 10, yPx = 20, verticalAnchor = VerticalAnchor.TOP),
            widthPx = 600,
        )

        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, params.type)
        assertEquals(PixelFormat.TRANSLUCENT, params.format)
        assertEquals(600, params.width)
        // The window wraps its content rather than taking a fixed height: a guessed height was
        // wrong twice, and the first guess put the launch button outside the window entirely.
        // maxHeightPx bounds the placement math only, never the window itself.
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, params.height)
        assertEquals(10, params.x)
        assertEquals(20, params.y)
        // Vertical gravity follows the placement instead of being pinned to TOP. That is what lets
        // a wrap-content window still hug the pet when the card opens upward: a BOTTOM-anchored
        // result grows away from the pet without anyone knowing the card's final height.
        assertEquals(Gravity.START or Gravity.TOP, params.gravity)
        // Measured fact 1 (design.md): softInputMode is a property of the IME target window only,
        // so focus and SOFT_INPUT_ADJUST_RESIZE ship together or not at all. FLAG_NOT_FOCUSABLE is
        // therefore dropped — the card must be focusable to become the IME target.
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        assertEquals(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            params.flags and (
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                ),
        )
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, params.softInputMode)
    }

    @Test
    fun `OverlayWindowParams output is unchanged by the card becoming focusable`() {
        val params = OverlayWindowParams.create(OverlayPosition(x = 0, y = 0), sizePx = 220)

        // The pet window's params are never mutated by this change — an #18 acceptance criterion.
        // Asserted field-by-field (the closest a LayoutParams object gets to "byte-identical" —
        // it has no equals() override) rather than only the one flag the previous test covered.
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, params.type)
        assertEquals(PixelFormat.TRANSLUCENT, params.format)
        assertEquals(220, params.width)
        assertEquals(220, params.height)
        assertEquals(0, params.x)
        assertEquals(0, params.y)
        assertEquals(Gravity.TOP or Gravity.START, params.gravity)
        assertEquals(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            params.flags,
        )
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
    }

    @Test
    fun `an upward placement anchors the window to the screen's bottom edge`() {
        val params = QuickMenuWindowParams.create(
            QuickMenuPlacementResult(xPx = 10, yPx = 500, verticalAnchor = VerticalAnchor.BOTTOM),
            widthPx = 600,
        )

        // Fails if vertical gravity were pinned to TOP. With a wrap-content window, a TOP-anchored
        // y for an upward-opening card can only be computed by subtracting a height nobody knows
        // yet — which is precisely how the card ended up floating well above the pet on a device.
        assertEquals(Gravity.START or Gravity.BOTTOM, params.gravity)
        assertEquals(500, params.y)
    }

    @Test
    fun `a balanced placement centres the window vertically`() {
        val params = QuickMenuWindowParams.create(
            QuickMenuPlacementResult(xPx = 10, yPx = -40, verticalAnchor = VerticalAnchor.CENTER),
            widthPx = 600,
        )

        // Fails if a tie were resolved by an edge gravity, which would push a mid-edge card to the
        // top or bottom of the screen instead of level with the pet.
        assertEquals(Gravity.START or Gravity.CENTER_VERTICAL, params.gravity)
        assertEquals(-40, params.y)
    }
}
