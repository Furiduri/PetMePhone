package com.gcatcode.petmephone.feature.overlay.position

import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionFraction
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `[POS-3]` `[POS-4]` — one write per completed gesture, cancelled by a superseding drag. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PositionWriterTest {

    private class SlowFakeRepository(private val delayMillis: Long) : OverlayPositionRepository {
        val savedFractions = mutableListOf<OverlayPositionFraction>()
        var cancelledCount = 0

        override val position: Flow<OverlayPositionFraction?> = flowOf(null)
        override val normalizations: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow()

        override suspend fun save(position: OverlayPositionFraction) {
            try {
                delay(delayMillis)
                savedFractions += position
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                cancelledCount++
                throw cancellation
            }
        }
    }

    @Test
    fun `a new drag cancels a pending write from the previous gesture`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = SlowFakeRepository(delayMillis = 1_000L)
        val scope = CoroutineScope(dispatcher)
        val writer = PositionWriter(repository, scope)

        // First gesture settles and starts writing.
        writer.writeAtRest(OverlayPositionFraction(0.1f, 0.1f))
        dispatcher.scheduler.advanceTimeBy(100L) // the write is in flight, not yet completed

        // A new drag starts before the first write lands.
        writer.cancelPending()

        // The second (superseding) gesture settles and writes its own resting position.
        writer.writeAtRest(OverlayPositionFraction(0.9f, 0.9f))
        advanceUntilIdle()

        assertEquals(1, repository.cancelledCount)
        assertEquals(1, repository.savedFractions.size)
        assertEquals(OverlayPositionFraction(0.9f, 0.9f), repository.savedFractions.single())
    }

    @Test
    fun `writeAtRest with no prior pending write completes normally`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = SlowFakeRepository(delayMillis = 0L)
        val scope = CoroutineScope(dispatcher)
        val writer = PositionWriter(repository, scope)

        writer.writeAtRest(OverlayPositionFraction(0.5f, 0.5f))
        advanceUntilIdle()

        assertEquals(0, repository.cancelledCount)
        assertEquals(OverlayPositionFraction(0.5f, 0.5f), repository.savedFractions.single())
    }

    @Test
    fun `cancelPending with no in-flight write is a no-op`() = runTest {
        val repository = SlowFakeRepository(delayMillis = 0L)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val writer = PositionWriter(repository, scope)

        writer.cancelPending() // must not throw

        assertTrue(repository.savedFractions.isEmpty())
    }
}
