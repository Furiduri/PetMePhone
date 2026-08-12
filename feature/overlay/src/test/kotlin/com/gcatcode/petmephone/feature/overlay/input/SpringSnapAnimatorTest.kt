package com.gcatcode.petmephone.feature.overlay.input

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

/**
 * Regression test for the crash the first device run of #15 exposed.
 *
 * The overlay drives the snap from the service scope, whose context is
 * `Dispatchers.Main.immediate` and carries no `MonotonicFrameClock`. `Animatable.animateTo`
 * suspends on `withFrameNanos`, which requires one, so the real animator threw
 * `IllegalStateException` and killed the process the moment the user lifted their finger.
 *
 * The existing controller tests could not catch this: they inject a fake [SnapAnimator], so they
 * assert the seam rather than the animation. This test exercises [SpringSnapAnimator] itself, from
 * a context shaped like production's, and fails if the frame clock is ever removed again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpringSnapAnimatorTest {

    @Test
    fun `animates to the target from a context with no frame clock`() {
        val animator = SpringSnapAnimator()
        val updates = mutableListOf<Float>()

        runBlocking {
            // Deliberately Main.immediate and nothing else: the production service scope.
            val job = launch(Dispatchers.Main.immediate) {
                animator.animate(fromX = 0f, toX = 100f) { value -> updates += value }
            }
            val looper = shadowOf(Looper.getMainLooper())
            var guard = 0
            while (!job.isCompleted && guard++ < 2_000) {
                looper.idle()
            }
            job.join()
        }

        assertTrue("the animation produced no frames", updates.isNotEmpty())
        assertEquals("the animation did not settle on the target", 100f, updates.last(), 0.01f)
        assertTrue(
            "the animation jumped straight to the target instead of springing",
            updates.size > 1,
        )
    }
}
