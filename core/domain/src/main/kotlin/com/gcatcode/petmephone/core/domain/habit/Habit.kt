package com.gcatcode.petmephone.core.domain.habit

import com.gcatcode.petmephone.core.domain.task.Behavior
import com.gcatcode.petmephone.core.domain.task.Minimum
import java.time.Instant
import java.time.LocalDate

/**
 * A behavior that repeats by cue (#98).
 *
 * The line against a task is drawn by the fields, not by a flag:
 *
 * | | Task | Habit |
 * | --- | --- | --- |
 * | [Behavior], [Minimum] | yes | yes |
 * | A date | yes | — |
 * | [Anchors], [HabitFrequency] | — | yes |
 * | [Identity] | — | optional |
 *
 * A recurring task is still a task. It fires by calendar, and #98 is explicit that it does not
 * become a habit by inference — the user decides.
 *
 * Every invariant this type needs is carried by its field types rather than checked here: [Anchors]
 * cannot be empty, [Behavior] and [Minimum] cannot be blank, and [HabitFrequency] cannot cover zero
 * days. What remains is the one rule no single field can see — that each anchor can actually arrive
 * on the days [frequency] needs — and that is [AnchorCompatibility]'s job at creation.
 *
 * [createdDate] is the day the *user* was living when they created it, not the calendar date; see
 * [com.gcatcode.petmephone.core.domain.time.AppDay].
 */
data class Habit(
    val id: HabitId,
    val behavior: Behavior,
    val minimum: Minimum,
    val frequency: HabitFrequency,
    val anchors: Anchors,
    /** Absent when the user skipped the question — never a blank [Identity]. */
    val identity: Identity?,
    val createdAt: Instant,
    val createdDate: LocalDate,
    val isActive: Boolean,
)
