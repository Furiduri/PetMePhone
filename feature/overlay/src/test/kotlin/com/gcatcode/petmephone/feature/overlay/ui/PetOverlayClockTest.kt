package com.gcatcode.petmephone.feature.overlay.ui

import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteGrid
import com.gcatcode.petmephone.core.domain.pet.sprite.SpriteLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Exercises the exact clock shape used by `PetOverlay`'s `LaunchedEffect` (design.md's code
 * sample) against a fake screen-on `StateFlow`, using `runTest` virtual time — no real delay.
 */
class PetOverlayClockTest {

    private val layout = SpriteLayout(
        grid = SpriteGrid(cellSizePx = 8, columns = 6),
        frameCounts = List(6) { 6 },
    )
    private val frameIntervalMillis = 100L

    @Test
    fun `frame index advances on the injected interval while screen is on`() = runTest {
        val screenOn = MutableStateFlow(true)
        var frameIndex = 0
        val job = launch {
            screenOn.collectLatest { on ->
                if (!on) return@collectLatest
                while (isActive) {
                    kotlinx.coroutines.delay(frameIntervalMillis)
                    frameIndex = (frameIndex + 1) % layout.idleFrameCount
                }
            }
        }

        advanceTimeBy(frameIntervalMillis * 3 + 1)

        assertEquals(3, frameIndex)
        job.cancel()
    }

    @Test
    fun `no advancement occurs while the screen is off`() = runTest {
        val screenOn = MutableStateFlow(false)
        var frameIndex = 0
        val job = launch {
            screenOn.collectLatest { on ->
                if (!on) return@collectLatest
                while (isActive) {
                    kotlinx.coroutines.delay(frameIntervalMillis)
                    frameIndex = (frameIndex + 1) % layout.idleFrameCount
                }
            }
        }

        advanceTimeBy(frameIntervalMillis * 10)

        assertEquals(0, frameIndex)
        job.cancel()
    }

    @Test
    fun `resume continues from the same index, not reset to zero`() = runTest {
        val screenOn = MutableStateFlow(true)
        var frameIndex = 0
        val job = launch {
            screenOn.collectLatest { on ->
                if (!on) return@collectLatest
                while (isActive) {
                    kotlinx.coroutines.delay(frameIntervalMillis)
                    frameIndex = (frameIndex + 1) % layout.idleFrameCount
                }
            }
        }

        advanceTimeBy(frameIntervalMillis * 2 + 1) // frameIndex == 2
        screenOn.value = false
        advanceTimeBy(frameIntervalMillis * 5) // suspended: no change
        assertEquals(2, frameIndex)

        screenOn.value = true
        advanceTimeBy(frameIntervalMillis + 1) // resumes from 2 -> 3

        assertEquals(3, frameIndex)
        job.cancel()
    }
}
