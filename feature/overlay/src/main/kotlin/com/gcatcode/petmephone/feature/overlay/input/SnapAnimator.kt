package com.gcatcode.petmephone.feature.overlay.input

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import javax.inject.Inject

/**
 * Seam around the horizontal spring animation `[DRAG-5]` requires, so [PetTouchController]'s snap
 * direction and vertical-preservation behaviour can be exercised under Robolectric without a real
 * `MonotonicFrameClock` driving `Animatable.animateTo` frame-by-frame.
 */
interface SnapAnimator {
    suspend fun animate(fromX: Float, toX: Float, onUpdate: (Float) -> Unit)
}

/** Real animator: a spring, never a jump. */
class SpringSnapAnimator @Inject constructor() : SnapAnimator {
    override suspend fun animate(fromX: Float, toX: Float, onUpdate: (Float) -> Unit) {
        val animatable = Animatable(fromX)
        animatable.animateTo(toX, animationSpec = spring()) { onUpdate(value) }
    }
}
