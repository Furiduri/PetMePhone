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
 * Events the card's window can receive. [BackPressed] is additive: the card becomes focusable in
 * this change, so its window can now receive a back press for the first time (design decision 9).
 * It is its own case rather than a reuse of [OutsideTouch] — reusing `OutsideTouch` would trip the
 * controller's `SAME_GESTURE_WINDOW_MS` suppression, which exists for a touch coincidence back has
 * nothing to do with.
 */
sealed interface QuickMenuEvent {
    data class PetTapped(val anchor: QuickMenuAnchor) : QuickMenuEvent
    data object PetDragged : QuickMenuEvent
    data object OutsideTouch : QuickMenuEvent
    data object AppLaunched : QuickMenuEvent
    data object ScreenOff : QuickMenuEvent
    data object BackPressed : QuickMenuEvent
}

/**
 * Total reducer over [QuickMenuState] x [QuickMenuEvent]. Every event applied to
 * [QuickMenuState.Open] yields [QuickMenuState.Closed] — `PetTapped`, `PetDragged`,
 * `OutsideTouch`, `AppLaunched`, `ScreenOff`, and `BackPressed` all close the card, no exceptions
 * and no guard condition. There is therefore no reachable state in which the card cannot be
 * dismissed (`overlay-quick-menu`'s "no reachable state leaves the card undismissable"
 * requirement, design decision 9's reachability argument). Note that `BackPressed` only ever
 * reaches this reducer via [resolveBack]'s `CloseCard` outcome — the controller applies the
 * content-level back ordering first (design decision 7).
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
        QuickMenuEvent.BackPressed,
        -> QuickMenuState.Closed
    }

    is QuickMenuState.Open -> when (event) {
        is QuickMenuEvent.PetTapped,
        QuickMenuEvent.PetDragged,
        QuickMenuEvent.OutsideTouch,
        QuickMenuEvent.AppLaunched,
        QuickMenuEvent.ScreenOff,
        QuickMenuEvent.BackPressed,
        -> QuickMenuState.Closed
    }
}
