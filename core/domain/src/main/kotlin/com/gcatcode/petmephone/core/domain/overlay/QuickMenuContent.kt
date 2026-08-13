package com.gcatcode.petmephone.core.domain.overlay

/**
 * Which content the quick-menu card is showing, once it is open. Pure domain type — no
 * `android.*` import — held as one field on the controller rather than widening
 * [QuickMenuState.Open] (design decision 4): the reducer's "every event from `Open` yields
 * `Closed`" property stays a claim about dismissability only, not about product state.
 */
sealed interface QuickMenuContent {
    data object Dashboard : QuickMenuContent
    data object TaskInput : QuickMenuContent
}

/**
 * The two levels of back handling this app owns. The keyboard level is not represented here — a
 * back press only reaches [resolveBack] when the IME did not consume it, per design decision 6.
 */
sealed interface BackOutcome {
    /** Level 2: unwind the container by one step. The window stays open. */
    data object ShowDashboard : BackOutcome

    /** Level 3: nothing left to unwind. The window closes. */
    data object CloseCard : BackOutcome
}

/**
 * Total over [QuickMenuContent]: `TaskInput -> ShowDashboard`, `Dashboard -> CloseCard`. A press
 * that reaches this function is by definition one the IME did not take, so the ordering reduces
 * to this two-case total function (design decision 7).
 */
fun resolveBack(content: QuickMenuContent): BackOutcome = when (content) {
    QuickMenuContent.TaskInput -> BackOutcome.ShowDashboard
    QuickMenuContent.Dashboard -> BackOutcome.CloseCard
}
