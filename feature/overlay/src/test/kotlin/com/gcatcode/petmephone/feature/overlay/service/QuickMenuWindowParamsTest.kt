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
class QuickMenuWindowParamsTest {

    @Test
    fun `create sets FLAG_NOT_FOCUSABLE and FLAG_WATCH_OUTSIDE_TOUCH, never FLAG_ALT_FOCUSABLE_IM`() {
        val params = QuickMenuWindowParams.create(
            OverlayPosition(x = 10, y = 20),
            widthPx = 600,
            heightPx = 400,
        )

        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, params.type)
        assertEquals(PixelFormat.TRANSLUCENT, params.format)
        assertEquals(600, params.width)
        assertEquals(400, params.height)
        assertEquals(10, params.x)
        assertEquals(20, params.y)
        assertEquals(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            params.flags and (
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                ),
        )
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }

    @Test
    fun `OverlayWindowParams still omits FLAG_WATCH_OUTSIDE_TOUCH`() {
        val params = OverlayWindowParams.create(OverlayPosition(x = 0, y = 0))

        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
    }
}
