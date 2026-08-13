package com.petmephone.spike.imeviability

/**
 * A cross-app observation the spike cannot measure itself (video playback state, whether the app
 * underneath visibly regained input, whether a keyboard the window is never told about actually
 * appeared). Never assumed and never left blank — the maintainer is always prompted with an
 * explicit question and must pick one of these three answers.
 *
 * These answers are authoritative. No machine-derived value overrides them: the 2026-08-12 run
 * recorded `Keyboard appeared: false` while the maintainer was typing into the field, and the
 * human observation was the correct one.
 */
enum class HumanAnswer(val label: String) {
    YES("Yes"),
    NO("No"),
    NOT_TESTED("Not tested"),
}

/**
 * How much of the text field the maintainer could actually see while typing. A boolean "covered"
 * would collapse the interesting middle case — a field whose top edge is visible but whose text is
 * not — into whichever end the answerer felt closer to.
 */
enum class FieldVisibility(val label: String) {
    FULLY_VISIBLE("Fully visible"),
    PARTIALLY_COVERED("Partially covered"),
    FULLY_COVERED("Fully covered"),
    NOT_TESTED("Not tested"),
}

/**
 * How the pet window's repositioning looked to the person watching it, in the two-window mode.
 *
 * "It did not move" is a distinct answer rather than the worst grade of smoothness: a pet that never
 * moved means no keyboard height was measured, which is a different finding entirely from a pet that
 * moved badly. Collapsing the two would hide the one result that decides whether the design works.
 */
enum class PetMovementQuality(val label: String) {
    SMOOTH("Moved smoothly"),
    JUMPED_OR_LAGGED("Jumped or lagged"),
    DID_NOT_MOVE("Did not move at all"),
    NOT_TESTED("Not tested"),
}
