package com.petmephone.spike.imeviability

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager

/**
 * The spike's measurement window is deliberately **focusable** in every mode — unlike the
 * production quick-menu card (design.md decision 6) — because measuring the cost of window focus is
 * exactly what this module exists to do. Shares no code with `OverlayWindowParams` or
 * `QuickMenuWindowParams`.
 */
object SpikeWindowParams {

    /**
     * Where the window starts vertically, as a fraction of the window's own measured height.
     *
     * The card MUST start low. Pan, resize and anchor-to-top are all strategies for rescuing a
     * field the keyboard would otherwise cover; a field that starts near the top is never covered
     * in the first place, so a run from there proves nothing about any of them.
     *
     * It is a fraction of the measured bounds rather than a pixel literal so the same run is low on
     * a short device and a tall one alike. The resulting absolute y is recorded in the findings.
     */
    const val LOW_START_FRACTION = 0.72f

    /**
     * The y the anchor-to-top mode moves to while the field holds focus. Zero would put the window
     * under the status bar on an edge-to-edge device, so a small fixed inset is used and, like the
     * start position, recorded rather than assumed.
     */
    const val ANCHOR_TOP_Y_PX = 120

    /** Computes the low starting y from the measured window bounds height. */
    fun lowStartY(windowHeightPx: Int): Int = (windowHeightPx * LOW_START_FRACTION).toInt()

    /**
     * The pet emulation window's side length, in pixels. Roughly the production pet's measured
     * on-device size; this module shares no code with `OverlayRenderSize`, so the number is restated
     * here rather than imported, and it only has to be close enough for the pet to be unmistakable
     * on a screenshot.
     */
    const val PET_SIZE_PX = 305

    /** The pet window's horizontal inset from the left edge. Any visible margin will do. */
    const val PET_START_X_PX = 60

    /**
     * Where the pet window starts vertically, as a fraction of the measured window height.
     *
     * It must start DEEP inside the region a keyboard occupies. The entire hypothesis is that a
     * non-focusable window is never the IME target and is therefore left stranded behind the
     * keyboard; a pet that starts above the keyboard is never stranded, so a run from there would
     * look clean while proving nothing. Round 1 measured a 933px reduction on a 2712px-tall
     * portrait display, so 0.86 puts the whole pet inside that band.
     */
    const val PET_START_FRACTION = 0.86f

    /** Computes the pet window's low starting y from the measured window bounds height. */
    fun petStartY(windowHeightPx: Int): Int = (windowHeightPx * PET_START_FRACTION).toInt()

    /**
     * The pet emulation window: non-focusable and with NO `softInputMode` at all, exactly like the
     * production pet window. Both facts are load-bearing — a window carrying `FLAG_NOT_FOCUSABLE`
     * can never become the IME target, and `softInputMode` is a property of the IME target window
     * only, so any value set here would be inert. Leaving it unset keeps that honest.
     */
    fun createPetWindow(startY: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            PET_SIZE_PX,
            PET_SIZE_PX,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = PET_START_X_PX
            y = startY
        }

    fun create(mode: SpikeMode, startY: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // No FLAG_NOT_FOCUSABLE, no FLAG_ALT_FOCUSABLE_IM override needed — a plain focusable
            // window already receives IME focus for its text field.
            //
            // FLAG_NOT_TOUCH_MODAL is NOT optional here. Without it the window is touch-modal: it
            // consumes every touch on the display, not just those inside its own bounds, so the
            // device becomes unusable and the operator cannot switch to the app the measurement
            // requires. The production pet window has carried this flag since slice 2; the spike
            // lost it by not reusing that code.
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = startY
            // Applied only when the mode asks for one. Modes that set none must genuinely set none:
            // writing a "neutral" value here would make the control run a fourth strategy under the
            // name of a baseline.
            mode.softInputMode?.let { softInputMode = it }
        }
}
