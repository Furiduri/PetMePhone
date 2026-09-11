package com.gcatcode.petmephone.core.domain.habit

import com.gcatcode.petmephone.core.domain.task.Behavior
import com.gcatcode.petmephone.core.domain.task.BehaviorResult
import com.gcatcode.petmephone.core.domain.task.Minimum
import com.gcatcode.petmephone.core.domain.task.MinimumResult
import com.gcatcode.petmephone.core.domain.time.AppClock
import com.gcatcode.petmephone.core.domain.time.AppDay
import java.time.LocalTime

/**
 * Creates a habit from raw authoring input (#98). Every rule the model requires is enforced here or
 * by the types it builds, so no partially-valid habit reaches [HabitRepository].
 *
 * `clock.now()` and [AppDay] are the only "today" in this write path (`domain-time` spec), and the
 * day is the user's rather than the calendar's: a habit created at 02:00 belongs to the day they
 * are still living.
 *
 * MUST be invoked from an application- or service-scoped scope, never
 * `rememberCoroutineScope()` — a card dismissed the instant after submit must not lose the
 * write, exactly as [com.gcatcode.petmephone.core.domain.task.CreateOneOffTask] requires.
 */
class CreateHabit(
    private val clock: AppClock,
    private val habits: HabitRepository,
    /** The user's start of day; see [AppDay]. */
    private val dayStart: LocalTime,
) {
    suspend operator fun invoke(
        rawBehavior: String,
        rawMinimum: String,
        frequency: HabitFrequency,
        anchors: List<Anchor>,
        rawIdentity: String? = null,
    ): CreateHabitResult {
        val behavior = when (val result = Behavior.of(rawBehavior)) {
            is BehaviorResult.Rejected.Blank -> return CreateHabitResult.Rejected.BlankBehavior
            is BehaviorResult.Rejected.TooLong ->
                return CreateHabitResult.Rejected.BehaviorTooLong(result.length, result.maxLength)
            is BehaviorResult.Valid -> result.behavior
        }

        val minimum = when (val result = Minimum.of(rawMinimum)) {
            is MinimumResult.Rejected.Blank -> return CreateHabitResult.Rejected.BlankMinimum
            is MinimumResult.Rejected.TooLong ->
                return CreateHabitResult.Rejected.MinimumTooLong(result.length, result.maxLength)
            is MinimumResult.Valid -> result.minimum
        }

        val identity = when (val result = Identity.ofOptional(rawIdentity)) {
            is IdentityResult.Absent -> null
            is IdentityResult.Rejected.Blank -> null
            is IdentityResult.Rejected.TooLong ->
                return CreateHabitResult.Rejected.IdentityTooLong(result.length, result.maxLength)
            is IdentityResult.Valid -> result.identity
        }

        if (frequency.occursOn.isEmpty()) return CreateHabitResult.Rejected.NoDays

        val checkedAnchors = when (val result = Anchors.of(anchors)) {
            is AnchorsResult.Rejected.Empty -> return CreateHabitResult.Rejected.NoAnchors
            is AnchorsResult.Valid -> result.anchors
        }

        // The one rule no single field can see: every cue must actually arrive on the days this
        // habit needs. Checked for all of them, not just the first — a habit with one good anchor
        // and one that can never fire is still a habit that goes silent on those days.
        checkedAnchors.values.forEachIndexed { index, anchor ->
            val compatibility = AnchorCompatibility.check(frequency, anchor)
            if (compatibility is AnchorCompatibilityResult.Rejected.MissingDays) {
                return CreateHabitResult.Rejected.IncompatibleAnchor(
                    anchorIndex = index,
                    anchor = anchor,
                    missingDays = compatibility.missingDays,
                )
            }
        }

        val now = clock.now()
        return try {
            val id = habits.create(
                behavior = behavior,
                minimum = minimum,
                frequency = frequency,
                anchors = checkedAnchors,
                identity = identity,
                createdAt = now,
                createdDate = AppDay.at(now, clock.zone(), dayStart),
            )
            CreateHabitResult.Created(id)
        } catch (_: Exception) {
            CreateHabitResult.Rejected.PersistenceFailure
        }
    }
}
