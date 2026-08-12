package com.gcatcode.petmephone.core.domain.pet.state

import app.cash.turbine.test
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DWELL_MILLIS = 100L

class PetStateResolverFlowTest {

    private val resolver = PetStateResolver(
        setOf(DraggingStateProvider(), IdleStateProvider()),
        PetStateConfig(DWELL_MILLIS),
    )

    @Test
    fun `dwell coalesces a flapping snapshot into emissions no closer than the injected duration`() = runTest {
        // Flaps between dragging/not-dragging faster than the dwell window, then settles.
        val snapshots = flow {
            emit(PetSnapshot(isDragging = false))
            delay(10)
            emit(PetSnapshot(isDragging = true))
            delay(10)
            emit(PetSnapshot(isDragging = false))
            delay(10)
            emit(PetSnapshot(isDragging = true))
            delay(200)
            emit(PetSnapshot(isDragging = false))
        }

        val emissionTimestamps = mutableListOf<Long>()
        resolver.states(snapshots).test {
            repeat(3) {
                awaitItem()
                emissionTimestamps += this@runTest.currentTime
            }
        }

        for (i in 1 until emissionTimestamps.size) {
            val gap = emissionTimestamps[i] - emissionTimestamps[i - 1]
            assertTrue(
                "emission $i landed only ${gap}ms after emission ${i - 1}, dwell is ${DWELL_MILLIS}ms",
                gap >= DWELL_MILLIS,
            )
        }
    }

    @Test
    fun `screen-on-off has no effect on emission`() = runTest {
        // A fake "screen off" input is simply not part of PetSnapshot/PetStateResolver at all —
        // resolution keeps emitting regardless, per [STATE-7].
        val snapshots = flow {
            emit(PetSnapshot(isDragging = false))
            delay(200)
            emit(PetSnapshot(isDragging = true))
        }

        resolver.states(snapshots).test {
            assertTrue(awaitItem() == PetState.IDLE)
            assertTrue(awaitItem() == PetState.DRAGGING)
        }
    }
}
