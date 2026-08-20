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

    /**
     * Plain instructions about the task-input content, reached from that content's help control.
     * It is a content of the same container, not a dialog or a second window.
     */
    data object Instructions : QuickMenuContent
}

/**
 * The levels of back handling this app owns. The keyboard level is not represented here — a
 * back press only reaches [resolveBack] when the IME did not consume it, per design decision 6.
 */
sealed interface BackOutcome {
    /** Unwind the instructions content by one step, back to the task input. The window stays open. */
    data object ShowTaskInput : BackOutcome

    /** Unwind the container by one step, back to the dashboard. The window stays open. */
    data object ShowDashboard : BackOutcome

    /** Nothing left to unwind. The window closes. */
    data object CloseCard : BackOutcome
}

/**
 * Total over [QuickMenuContent]: `Instructions -> ShowTaskInput`, `TaskInput -> ShowDashboard`,
 * `Dashboard -> CloseCard`. Each case unwinds exactly one level and never skips one (design
 * decision 7). A press that reaches this function is by definition one the IME did not take, so
 * the ordering reduces to this total function over the container's own stack.
 */
fun resolveBack(content: QuickMenuContent): BackOutcome = when (content) {
    QuickMenuContent.Instructions -> BackOutcome.ShowTaskInput
    QuickMenuContent.TaskInput -> BackOutcome.ShowDashboard
    QuickMenuContent.Dashboard -> BackOutcome.CloseCard
}
