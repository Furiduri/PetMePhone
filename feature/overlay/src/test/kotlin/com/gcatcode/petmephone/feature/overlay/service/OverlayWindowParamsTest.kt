package com.gcatcode.petmephone.feature.overlay.service

import android.graphics.PixelFormat
import android.view.WindowManager
import com.gcatcode.petmephone.core.domain.overlay.OverlayPosition
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OverlayWindowParamsTest {

    @Test
    fun `create sets the mandatory overlay window flags`() {
        val params = OverlayWindowParams.create(OverlayPosition(x = 10, y = 20))

        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, params.type)
        assertEquals(PixelFormat.TRANSLUCENT, params.format)
        assertEquals(10, params.x)
        assertEquals(20, params.y)
        assertEquals(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            params.flags and (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL),
        )
    }

    @Test
    fun `create does not set FLAG_LAYOUT_NO_LIMITS or FLAG_WATCH_OUTSIDE_TOUCH`() {
        val params = OverlayWindowParams.create(OverlayPosition(x = 0, y = 0))

        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
    }

    @Test
    fun `clampToBounds keeps the window inside the screen`() {
        val params = OverlayWindowParams.create(OverlayPosition(x = 9_999, y = 9_999))

        OverlayWindowParams.clampToBounds(params, screenWidthPx = 1080, screenHeightPx = 2400)

        assertEquals(1080 - OverlayWindowParams.PLACEHOLDER_SIZE_PX, params.x)
        assertEquals(2400 - OverlayWindowParams.PLACEHOLDER_SIZE_PX, params.y)
    }

    @Test
    fun `clampToBounds never produces a negative offset`() {
        val params = OverlayWindowParams.create(OverlayPosition(x = -50, y = -50))

        OverlayWindowParams.clampToBounds(params, screenWidthPx = 1080, screenHeightPx = 2400)

        assertEquals(0, params.x)
        assertEquals(0, params.y)
    }
}
