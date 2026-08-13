package com.petmephone.spike.imeviability

/**
 * The two modes the spike runs against the same window (design.md decision 14a). Recording them
 * separately is the whole point: without the split, a "video paused" result cannot distinguish the
 * keyboard's cost from the mere cost of taking window focus.
 */
enum class SpikeMode(val label: String) {
    /** Focusable window, no text field, no keyboard ever raised. */
    FOCUS_ONLY("Focus-only"),

    /** Focusable window with a real text field; the soft keyboard is expected to raise. */
    FULL_IME("Full IME"),
}
