package com.gcatcode.petmephone.core.domain.overlay

/**
 * Which content the quick-menu card is showing, once it is open. Pure domain type — no
 * `android.*` import — held as one field on the controller rather than widening
 * [QuickMenuState.Open] (design decision 4): the reducer's "every event from `Open` yields
 * `Closed`" property stays a claim about dismissability only, not about product state.
 *
 * The single-field task input and its instructions page used to live here. They were left behind
 * when the authoring form landed: the dashboard opened them, their "Task title" was discarded when
 * the form started, and their copy went on promising that adding creates nothing and that typed
 * text is thrown away — both false once the draft was persisted. Unreachable code is debt; copy
 * that lies to the user is worse.
 */
sealed interface QuickMenuContent {
    data object Dashboard : QuickMenuContent

    /**
     * One step of the authoring form (#100), carrying which step it is.
     *
     * The index lives on the content rather than beside it because back has to unwind it: a step
     * held as a separate field would let the two disagree, and a back press would then leave from a
     * step the card is not showing.
     */
    data class StepForm(val stepIndex: Int) : QuickMenuContent

    /**
     * The explanation for a step, shown **in the card** in place of that step. It carries the index
     * so returning goes back to the step the user asked about, not to the start of the form.
     *
     * A tooltip or popup would be a second overlay window, with its own dismissal handling, touch
     * conflicts and gravity defects — the class of problem #28 and #87 already document.
     */
    data class StepHelp(val stepIndex: Int) : QuickMenuContent
}

/**
 * The levels of back handling this app owns. The keyboard level is not represented here — a
 * back press only reaches [resolveBack] when the IME did not consume it, per design decision 6.
 */
sealed interface BackOutcome {
    /** Unwind the container by one step, back to the dashboard. The window stays open. */
    data object ShowDashboard : BackOutcome

    /** Nothing left to unwind. The window closes. */
    data object CloseCard : BackOutcome

    /** Unwind to a specific authoring step — one step back, or back out of that step's help. */
    data class ShowStep(val stepIndex: Int) : BackOutcome
}

/**
 * Total over [QuickMenuContent]: `Dashboard -> CloseCard`, `StepHelp(i) -> ShowStep(i)`, and
 * `StepForm(i)` to the step before it or out to the dashboard. Each case unwinds exactly one level
 * and never skips one (design decision 7). A press that reaches this function is by definition one
 * the IME did not take, so the ordering reduces to this total function over the container's own
 * stack.
 */
fun resolveBack(content: QuickMenuContent): BackOutcome = when (content) {
    QuickMenuContent.Dashboard -> BackOutcome.CloseCard

    // A step's help returns to that step, never to the start of the form.
    is QuickMenuContent.StepHelp -> BackOutcome.ShowStep(content.stepIndex)

    // Inside the form, back walks the steps; from the first one it leaves the form entirely. It
    // never discards — only Cancel does that (#100), which is why back is safe to press.
    is QuickMenuContent.StepForm ->
        if (content.stepIndex > 0) {
            BackOutcome.ShowStep(content.stepIndex - 1)
        } else {
            BackOutcome.ShowDashboard
        }
}
