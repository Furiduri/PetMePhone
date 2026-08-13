package com.petmephone.spike.imeviability

import android.view.WindowManager

/**
 * The strategies the spike runs against the same window.
 *
 * The first two modes are the ones the 2026-08-12 run already used and are kept unchanged so those
 * findings stay interpretable. The three that follow are the keyboard-visibility candidates issue
 * #18 needs compared against each other on real hardware.
 *
 * [FULL_IME] doubles as the explicit "no strategy" control: it raises a real keyboard while setting
 * no `softInputMode` and never repositioning the window, which is exactly what a control run needs
 * to be. A separate `NO_STRATEGY` entry would be a duplicate of it under another name, and would
 * split one baseline across two labels in the findings file.
 */
@Suppress("DEPRECATION")
enum class SpikeMode(
    val label: String,
    /** True when the run puts a real text field on screen and asks for the soft keyboard. */
    val raisesKeyboard: Boolean,
    /**
     * The value written to `WindowManager.LayoutParams.softInputMode`, or null when the mode
     * deliberately sets none. Null is "no value applied", never "zero applied".
     */
    val softInputMode: Int?,
    /**
     * True when the mode repositions the window to the top of the usable bounds while the field
     * holds focus, and restores the original position when focus is lost.
     */
    val repositionsOnFocus: Boolean,
    /**
     * True when the mode adds a SECOND, non-focusable window emulating the production pet, and moves
     * it by whatever keyboard height the card window managed to measure.
     */
    val addsPetWindow: Boolean = false,
) {
    /** Focusable window, no text field, no keyboard ever raised. */
    FOCUS_ONLY(
        label = "Focus-only",
        raisesKeyboard = false,
        softInputMode = null,
        repositionsOnFocus = false,
    ),

    /**
     * Focusable window with a real text field, no visibility strategy at all. This is the control
     * the three strategies below are compared against.
     */
    FULL_IME(
        label = "Full IME (no strategy control)",
        raisesKeyboard = true,
        softInputMode = null,
        repositionsOnFocus = false,
    ),

    /** Same window, with `SOFT_INPUT_ADJUST_PAN` on the overlay window's own `LayoutParams`. */
    SOFT_INPUT_PAN(
        label = "Pan",
        raisesKeyboard = true,
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN,
        repositionsOnFocus = false,
    ),

    /**
     * Same window, with `SOFT_INPUT_ADJUST_RESIZE`. Deprecated since API 30 in favour of insets —
     * which this window class provably never receives — so it is measured here rather than assumed
     * dead, and pan is compared against something instead of being assumed better.
     */
    SOFT_INPUT_RESIZE(
        label = "Resize",
        raisesKeyboard = true,
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        repositionsOnFocus = false,
    ),

    /**
     * No `softInputMode` reliance at all: when the field gains focus the window is moved to the top
     * of the usable bounds via `updateViewLayout`, and moved back when focus is lost. This is the
     * one candidate that needs no keyboard-height signal, so it must stay testable on its own.
     */
    ANCHOR_TOP_ON_FOCUS(
        label = "Anchor top on focus",
        raisesKeyboard = true,
        softInputMode = null,
        repositionsOnFocus = true,
    ),

    /**
     * Round 2's mode: the two-window shape the production overlay actually has.
     *
     * A focusable card window under `SOFT_INPUT_ADJUST_RESIZE` — round 1 established that resize is
     * the strategy that keeps the field visible, and that `softInputMode` is a property of the IME
     * target window alone — plus a small non-focusable pet window that can never be the IME target
     * and will therefore never be moved by the keyboard on its own.
     *
     * The card is the measuring instrument: it reads its own reduced visible display frame, and the
     * service moves the pet by exactly that measured reduction. If the card never measures a
     * reduction, the pet does not move at all — there is no default height, because a guessed one
     * would answer this round's question with an assumption.
     */
    TWO_WINDOW_PET_FOLLOW(
        label = "Two windows: card resizes, pet follows",
        raisesKeyboard = true,
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        repositionsOnFocus = false,
        addsPetWindow = true,
    ),
    ;

    /** Human-readable rendering of [softInputMode] for the findings file. */
    fun softInputModeLabel(): String = when (softInputMode) {
        null -> "none applied"
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN -> "SOFT_INPUT_ADJUST_PAN (0x20)"
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE -> "SOFT_INPUT_ADJUST_RESIZE (0x10)"
        else -> "0x${softInputMode.toString(16)}"
    }
}
