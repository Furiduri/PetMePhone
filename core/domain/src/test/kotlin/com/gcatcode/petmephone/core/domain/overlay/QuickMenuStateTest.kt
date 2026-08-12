package com.gcatcode.petmephone.core.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

private val ANCHOR = QuickMenuAnchor(xPx = 100, yPx = 200, sizePx = 220)
private val OTHER_ANCHOR = QuickMenuAnchor(xPx = 500, yPx = 600, sizePx = 220)

class QuickMenuStateTest {

    @Test
    fun `PetTapped on Closed opens the card at the tapped anchor`() {
        // Fails if reduce returned Closed, or Open with a different anchor than the one tapped.
        val result = reduce(QuickMenuState.Closed, QuickMenuEvent.PetTapped(ANCHOR))

        assertEquals(QuickMenuState.Open(ANCHOR), result)
    }

    @Test
    fun `every event from Open yields Closed - no undismissable state exists`() {
        val eventsFromOpen = listOf(
            QuickMenuEvent.PetTapped(OTHER_ANCHOR),
            QuickMenuEvent.PetDragged,
            QuickMenuEvent.OutsideTouch,
            QuickMenuEvent.AppLaunched,
            QuickMenuEvent.ScreenOff,
        )

        eventsFromOpen.forEach { event ->
            // Fails if any single event left the reducer in Open instead of Closed — that event
            // would be a dead end with no way out, per design decision 9.
            val result = reduce(QuickMenuState.Open(ANCHOR), event)
            assertEquals("event=$event did not close the card", QuickMenuState.Closed, result)
        }
    }

    @Test
    fun `PetTapped while Open closes the card - the ACTION_OUTSIDE fallback`() {
        // Fails if PetTapped while Open re-opened at the new anchor instead of closing — that
        // would break the pet-tap fallback this test names explicitly, per the task wording.
        val result = reduce(QuickMenuState.Open(ANCHOR), QuickMenuEvent.PetTapped(OTHER_ANCHOR))

        assertEquals(QuickMenuState.Closed, result)
    }

    @Test
    fun `non-PetTapped events while Closed are no-ops`() {
        val eventsWhileClosed = listOf(
            QuickMenuEvent.PetDragged,
            QuickMenuEvent.OutsideTouch,
            QuickMenuEvent.AppLaunched,
            QuickMenuEvent.ScreenOff,
        )

        eventsWhileClosed.forEach { event ->
            // Fails if any of these events spontaneously opened the card from Closed.
            val result = reduce(QuickMenuState.Closed, event)
            assertEquals("event=$event unexpectedly changed state", QuickMenuState.Closed, result)
        }
    }

    @Test
    fun `reduce is total and deterministic across the full state x event matrix`() {
        val states = listOf(QuickMenuState.Closed, QuickMenuState.Open(ANCHOR))
        val events = listOf(
            QuickMenuEvent.PetTapped(OTHER_ANCHOR),
            QuickMenuEvent.PetDragged,
            QuickMenuEvent.OutsideTouch,
            QuickMenuEvent.AppLaunched,
            QuickMenuEvent.ScreenOff,
        )

        states.forEach { state ->
            events.forEach { event ->
                // Fails if calling reduce twice with the same (state, event) pair produced two
                // different results — reduce carrying hidden mutable state or non-determinism.
                val first = reduce(state, event)
                val second = reduce(state, event)
                assertEquals("state=$state event=$event was non-deterministic", first, second)
            }
        }
    }
}
