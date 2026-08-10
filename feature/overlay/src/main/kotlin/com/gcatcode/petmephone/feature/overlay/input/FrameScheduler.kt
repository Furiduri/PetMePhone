package com.gcatcode.petmephone.feature.overlay.input

/**
 * Seam around `Choreographer`, so [PetTouchController]'s per-frame throttle can be exercised
 * under Robolectric with a fake that runs callbacks synchronously and counts scheduled frames,
 * without depending on the real display refresh loop.
 */
interface FrameScheduler {
    /** Schedules [callback] to run before the next rendered frame. */
    fun postFrameCallback(callback: () -> Unit)

    /** Cancels a previously scheduled [callback], if it has not yet run. */
    fun removeFrameCallback(callback: () -> Unit)
}
