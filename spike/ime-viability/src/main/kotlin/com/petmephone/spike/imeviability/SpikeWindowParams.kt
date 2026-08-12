package com.petmephone.spike.imeviability

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager

/**
 * The spike's measurement window is deliberately **focusable** in both modes — unlike the
 * production quick-menu card (design.md decision 6) — because measuring the cost of window focus
 * is exactly what this module exists to do. Shares no code with `OverlayWindowParams` or
 * `QuickMenuWindowParams`.
 */
object SpikeWindowParams {
    fun create(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // No FLAG_NOT_FOCUSABLE, no FLAG_ALT_FOCUSABLE_IM override needed — a plain focusable
        // window already receives IME focus for its text field in full-IME mode.
        //
        // FLAG_NOT_TOUCH_MODAL is NOT optional here. Without it the window is touch-modal: it
        // consumes every touch on the display, not just those inside its own bounds, so the device
        // becomes unusable and the operator cannot switch to the video app the measurement
        // requires. The production pet window has carried this flag since slice 2; the spike lost
        // it by not reusing that code.
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = 200
    }
}
