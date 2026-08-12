package com.gcatcode.petmephone.feature.overlay.input

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlinx.coroutines.withContext
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
        // `Animatable.animateTo` suspends on `withFrameNanos`, which requires a MonotonicFrameClock
        // in the calling context. The overlay drives this from the service scope
        // (`Dispatchers.Main.immediate`), which carries no frame clock: calling it there throws
        // `IllegalStateException` on the first frame and kills the process the moment the user
        // lifts their finger. `AndroidUiDispatcher.Main` is the Main dispatcher that does carry
        // one, so the animation runs against real frames instead of crashing.
        withContext(AndroidUiDispatcher.Main) {
            val animatable = Animatable(fromX)
            animatable.animateTo(toX, animationSpec = spring()) { onUpdate(value) }
        }
    }
}
