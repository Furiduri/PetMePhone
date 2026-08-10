package com.gcatcode.petmephone.core.domain.pet.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

private const val DWELL_MILLIS = 100L

class PetStateResolverTest {

    @Test
    fun `providers in scrambled injection order still resolve identically`() {
        val providers = setOf(DraggingStateProvider(), IdleStateProvider())
        val resolverA = PetStateResolver(providers, PetStateConfig(DWELL_MILLIS))
        val resolverB = PetStateResolver(
            setOf<PetStateProvider>(IdleStateProvider(), DraggingStateProvider()),
            PetStateConfig(DWELL_MILLIS),
        )
        val snapshot = PetSnapshot(isDragging = true)

        assertEquals(resolverA.resolve(snapshot), resolverB.resolve(snapshot))
    }

    @Test
    fun `no provider matches falls back to IDLE`() {
        val resolver = PetStateResolver(
            setOf(DraggingStateProvider(), IdleStateProvider()),
            PetStateConfig(DWELL_MILLIS),
        )

        val resolved = resolver.resolve(PetSnapshot(isDragging = false))

        assertEquals(PetState.IDLE, resolved)
    }

    @Test
    fun `dragging true resolves DRAGGING regardless of provider registration order`() {
        val forwardOrder = PetStateResolver(
            setOf(DraggingStateProvider(), IdleStateProvider()),
            PetStateConfig(DWELL_MILLIS),
        )
        val reverseOrder = PetStateResolver(
            setOf<PetStateProvider>(IdleStateProvider(), DraggingStateProvider()),
            PetStateConfig(DWELL_MILLIS),
        )
        val snapshot = PetSnapshot(isDragging = true)

        assertEquals(PetState.DRAGGING, forwardOrder.resolve(snapshot))
        assertEquals(PetState.DRAGGING, reverseOrder.resolve(snapshot))
    }

    @Test
    fun `two providers with identical priority throws at construction`() {
        val duplicatePriorityProvider = object : PetStateProvider {
            override val priority: Int = DraggingStateProvider.PRIORITY
            override fun evaluate(snapshot: PetSnapshot): PetState? = null
        }

        assertThrows(IllegalArgumentException::class.java) {
            PetStateResolver(
                setOf(DraggingStateProvider(), duplicatePriorityProvider),
                PetStateConfig(DWELL_MILLIS),
            )
        }
    }

    @Test
    fun `full registered set has strictly distinct priorities`() {
        val providers = setOf(DraggingStateProvider(), IdleStateProvider())

        val priorities = providers.map { it.priority }

        assertEquals(priorities.distinct().size, priorities.size)
    }
}
