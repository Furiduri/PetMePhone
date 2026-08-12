package com.gcatcode.petmephone.core.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

private val ANCHOR = QuickMenuAnchor(xPx = 100, yPx = 200, sizePx = 220)
private val OTHER_ANCHOR = QuickMenuAnchor(xPx = 500, yPx = 600, sizePx = 220)

/**
 * One sample of every [QuickMenuEvent], used by every test below instead of a per-test list.
 *
 * [coverageOf] is the guard that keeps it honest. `design.md` states plainly that this reducer is
 * meant to be EXTENDED additively when the IME work lands, so a new event is expected, not
 * hypothetical. A hardcoded list would still compile and still pass while silently not testing that
 * new event — someone could make it return `Open` and every test here would stay green.
 *
 * The `when` in [coverageOf] is exhaustive over the sealed type, so adding an event breaks
 * compilation *in this file*, and the accompanying test fails until the new event is added to
 * [ALL_EVENTS] as well. Drift becomes a build failure instead of a silent gap.
 */
private val ALL_EVENTS: List<QuickMenuEvent> = listOf(
    QuickMenuEvent.PetTapped(OTHER_ANCHOR),
    QuickMenuEvent.PetDragged,
    QuickMenuEvent.OutsideTouch,
    QuickMenuEvent.AppLaunched,
    QuickMenuEvent.ScreenOff,
)

/** Every branch here must be reachable from [ALL_EVENTS]; see [QuickMenuStateTest]'s coverage test. */
private fun coverageOf(event: QuickMenuEvent): String = when (event) {
    is QuickMenuEvent.PetTapped -> "PetTapped"
    QuickMenuEvent.PetDragged -> "PetDragged"
    QuickMenuEvent.OutsideTouch -> "OutsideTouch"
    QuickMenuEvent.AppLaunched -> "AppLaunched"
    QuickMenuEvent.ScreenOff -> "ScreenOff"
}

class QuickMenuStateTest {

    @Test
    fun `ALL_EVENTS contains every QuickMenuEvent subtype`() {
        // Fails the moment a sixth event exists and nobody adds it to ALL_EVENTS: the exhaustive
        // `when` in coverageOf stops compiling, and once repaired, this size check goes red until
        // the new event is present. Without this, every dismissability test below would keep
        // passing while ignoring the new event entirely.
        val distinctBranches = ALL_EVENTS.map(::coverageOf).toSet()

        assertEquals(
            "ALL_EVENTS is missing an event; the dismissability tests would silently skip it",
            5,
            distinctBranches.size,
        )
    }

    @Test
    fun `PetTapped on Closed opens the card at the tapped anchor`() {
        // Fails if reduce returned Closed, or Open with a different anchor than the one tapped.
        val result = reduce(QuickMenuState.Closed, QuickMenuEvent.PetTapped(ANCHOR))

        assertEquals(QuickMenuState.Open(ANCHOR), result)
    }

    @Test
    fun `every event from Open yields Closed - no undismissable state exists`() {
        ALL_EVENTS.forEach { event ->
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
        ALL_EVENTS.filterNot { it is QuickMenuEvent.PetTapped }.forEach { event ->
            // Fails if any of these events spontaneously opened the card from Closed.
            val result = reduce(QuickMenuState.Closed, event)
            assertEquals("event=$event unexpectedly changed state", QuickMenuState.Closed, result)
        }
    }

    @Test
    fun `reduce is total and deterministic across the full state x event matrix`() {
        val states = listOf(QuickMenuState.Closed, QuickMenuState.Open(ANCHOR))

        states.forEach { state ->
            ALL_EVENTS.forEach { event ->
                // Fails if calling reduce twice with the same (state, event) pair produced two
                // different results — reduce carrying hidden mutable state or non-determinism.
                val first = reduce(state, event)
                val second = reduce(state, event)
                assertEquals("state=$state event=$event was non-deterministic", first, second)
            }
        }
    }
}
