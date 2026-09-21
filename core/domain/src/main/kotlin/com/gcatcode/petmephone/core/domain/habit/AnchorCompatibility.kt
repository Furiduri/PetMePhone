package com.gcatcode.petmephone.core.domain.habit

import java.time.DayOfWeek

/**
 * #98's frequency refusal: "an anchor whose frequency does not match the habit's is a validation
 * error, not a preference".
 *
 * The rule is one containment check. An anchor supports a habit when it arrives on **every** day
 * the habit needs. Stacking a daily habit onto a Monday-only habit leaves Tuesday through Sunday
 * with no cue, so the habit silently cannot fire — and the user experiences that as the app
 * forgetting, which is why it is refused at construction rather than surfaced later as a warning.
 *
 * The reverse is fine: an anchor that arrives more often than the habit needs simply goes unused on
 * the extra days. Only missing days are a defect.
 *
 * In practice only [Anchor.AfterHabit] can fail, since a day segment and a clock time both arrive
 * daily. That is worth stating rather than special-casing: the check is written over
 * [Anchor.arrivesOn] so a future anchor type with a narrower arrival is covered the day it is
 * added, without anyone remembering to extend a `when`.
 */
object AnchorCompatibility {

    /** Refuses [anchor] when it cannot arrive on some day [frequency] needs. */
    fun check(frequency: HabitFrequency, anchor: Anchor): AnchorCompatibilityResult {
        val missing = frequency.occursOn - anchor.arrivesOn
        return if (missing.isEmpty()) {
            AnchorCompatibilityResult.Compatible
        } else {
            AnchorCompatibilityResult.Rejected.MissingDays(
                requiredDays = frequency.occursOn,
                anchorArrivesOn = anchor.arrivesOn,
                missingDays = missing,
            )
        }
    }
}

/** Outcome of [AnchorCompatibility.check] — measured values, as the task rejections do. */
sealed interface AnchorCompatibilityResult {
    data object Compatible : AnchorCompatibilityResult

    sealed interface Rejected : AnchorCompatibilityResult {
        /**
         * [missingDays] is the reason #98 asks for: the exact days the habit needs and the anchor
         * cannot supply, so a surface can say which ones rather than "incompatible".
         */
        data class MissingDays(
            val requiredDays: Set<DayOfWeek>,
            val anchorArrivesOn: Set<DayOfWeek>,
            val missingDays: Set<DayOfWeek>,
        ) : Rejected
    }
}
