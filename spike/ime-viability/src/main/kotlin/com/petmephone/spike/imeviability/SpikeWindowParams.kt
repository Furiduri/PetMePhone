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
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = 200
    }
}
