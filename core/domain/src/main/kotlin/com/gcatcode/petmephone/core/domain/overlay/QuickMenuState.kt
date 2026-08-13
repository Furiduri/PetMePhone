package com.gcatcode.petmephone.core.domain.overlay

/**
 * The quick-menu card's dismissal state machine. `Closed` | `Open(anchor)` only — no `Focused` or
 * `Submitted` sibling exists, per design decision 9. This is deliberately the shell's honest
 * subset: the spike's outcome extends this reducer rather than replacing it.
 */
sealed interface QuickMenuState {
    data object Closed : QuickMenuState
    data class Open(val anchor: QuickMenuAnchor) : QuickMenuState
}

/**
 * Events the card's window can receive. There is deliberately no `BackPressed` case — the card
 * ships non-focusable in this change, so no window ever receives a key event to react to (design
 * decision 7). Adding one back in would be dead code dressed as a feature.
 */
sealed interface QuickMenuEvent {
    data class PetTapped(val anchor: QuickMenuAnchor) : QuickMenuEvent
    data object PetDragged : QuickMenuEvent
    data object OutsideTouch : QuickMenuEvent
    data object AppLaunched : QuickMenuEvent
    data object ScreenOff : QuickMenuEvent
}

/**
 * Total reducer over [QuickMenuState] x [QuickMenuEvent]. Every event applied to
 * [QuickMenuState.Open] yields [QuickMenuState.Closed] — `PetTapped`, `PetDragged`,
 * `OutsideTouch`, `AppLaunched`, and `ScreenOff` all close the card, no exceptions and no guard
 * condition. There is therefore no reachable state in which the card cannot be dismissed
 * (`overlay-quick-menu`'s "no reachable state leaves the card undismissable" requirement,
 * design decision 9's reachability argument).
 *
 * From [QuickMenuState.Closed], only [QuickMenuEvent.PetTapped] has any effect — it opens the
 * card at the tapped anchor. Every other event arriving while already closed is a no-op: the
 * reducer is total, so it must still return a value for those pairs, and the only value that
 * preserves "closed events don't do anything" is the same [QuickMenuState.Closed] instance.
 */
fun reduce(state: QuickMenuState, event: QuickMenuEvent): QuickMenuState = when (state) {
    is QuickMenuState.Closed -> when (event) {
        is QuickMenuEvent.PetTapped -> QuickMenuState.Open(event.anchor)
        QuickMenuEvent.PetDragged,
        QuickMenuEvent.OutsideTouch,
        QuickMenuEvent.AppLaunched,
        QuickMenuEvent.ScreenOff,
        -> QuickMenuState.Closed
    }

    is QuickMenuState.Open -> when (event) {
        is QuickMenuEvent.PetTapped,
        QuickMenuEvent.PetDragged,
        QuickMenuEvent.OutsideTouch,
        QuickMenuEvent.AppLaunched,
        QuickMenuEvent.ScreenOff,
        -> QuickMenuState.Closed
    }
}
