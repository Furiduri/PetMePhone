package com.gcatcode.petmephone.feature.overlay.input

import android.view.Choreographer
import javax.inject.Inject

/** Real [FrameScheduler], backed by the actual display frame loop. */
class ChoreographerFrameScheduler @Inject constructor() : FrameScheduler {

    private val pending = mutableMapOf<() -> Unit, Choreographer.FrameCallback>()

    override fun postFrameCallback(callback: () -> Unit) {
        val frameCallback = Choreographer.FrameCallback { callback() }
        pending[callback] = frameCallback
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun removeFrameCallback(callback: () -> Unit) {
        pending.remove(callback)?.let { Choreographer.getInstance().removeFrameCallback(it) }
    }
}
