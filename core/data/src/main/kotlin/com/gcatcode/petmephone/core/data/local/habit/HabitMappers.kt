package com.gcatcode.petmephone.core.data.local.habit

import com.gcatcode.petmephone.core.domain.habit.Anchor
import com.gcatcode.petmephone.core.domain.habit.AnchorKind
import com.gcatcode.petmephone.core.domain.habit.Anchors
import com.gcatcode.petmephone.core.domain.habit.AnchorsResult
import com.gcatcode.petmephone.core.domain.habit.Habit
import com.gcatcode.petmephone.core.domain.habit.HabitFrequency
import com.gcatcode.petmephone.core.domain.habit.HabitFrequencyResult
import com.gcatcode.petmephone.core.domain.habit.HabitId
import com.gcatcode.petmephone.core.domain.habit.Identity
import com.gcatcode.petmephone.core.domain.habit.IdentityResult
import com.gcatcode.petmephone.core.domain.task.Behavior
import com.gcatcode.petmephone.core.domain.task.BehaviorResult
import com.gcatcode.petmephone.core.domain.task.Minimum
import com.gcatcode.petmephone.core.domain.task.MinimumResult
import java.time.DayOfWeek

/**
 * Rebuilding a habit from its rows, and saying so plainly when the rows cannot make one.
 *
 * Every domain type here refuses invalid input by construction, which means a corrupt or
 * partially-deleted set of rows genuinely *cannot* become a `Habit`. That is the point: the
 * alternative is a mapper that quietly substitutes a default and hands the app a habit nobody
 * created.
 */
sealed interface HabitReadResult {
    data class Valid(val habit: Habit) : HabitReadResult

    /** The rows exist but do not describe a habit the domain would accept. */
    data class Malformed(val habitId: Long, val reason: String) : HabitReadResult
}

/**
 * Builds the domain [Habit] from its rows.
 *
 * [anchorHabitDays] resolves the weekdays of a habit referenced by an [AnchorKind.AFTER_HABIT]
 * anchor. That frequency is **derived, never stored twice**: keeping a second copy on the anchor
 * row would let the two drift, which is the same duplicated-default defect
 * `BalanceConfigTest` now guards against on the balance side.
 */
internal fun buildHabit(
    entity: HabitEntity,
    dayRows: List<HabitDayEntity>,
    anchorRows: List<HabitAnchorEntity>,
    anchorHabitDays: (Long) -> Set<DayOfWeek>?,
): HabitReadResult {
    val behavior = when (val result = Behavior.of(entity.behavior)) {
        is BehaviorResult.Valid -> result.behavior
        else -> return HabitReadResult.Malformed(entity.id, "behavior is not valid: rejected on read")
    }

    val minimum = when (val result = Minimum.of(entity.minimum)) {
        is MinimumResult.Valid -> result.minimum
        else -> return HabitReadResult.Malformed(entity.id, "minimum is not valid: rejected on read")
    }

    // A stored identity that no longer validates is dropped rather than failing the whole habit:
    // it is optional, and losing the sentence is not worth losing the habit. A stored BLANK one,
    // however, should never have been written, so it simply reads back as absent.
    val identity: Identity? = entity.identity?.let { raw ->
        when (val result = Identity.of(raw)) {
            is IdentityResult.Valid -> result.identity
            else -> null
        }
    }

    val days = dayRows.map { it.dayOfWeek }.toSet()
    val frequency: HabitFrequency = when {
        days.isEmpty() ->
            return HabitReadResult.Malformed(entity.id, "no HabitDay rows: a habit that occurs on no day")
        days == DayOfWeek.entries.toSet() -> HabitFrequency.Daily
        else -> when (val result = HabitFrequency.OnDays.of(days)) {
            is HabitFrequencyResult.Valid -> result.frequency
            else -> return HabitReadResult.Malformed(entity.id, "day rows rejected: $days")
        }
    }

    val anchors = mutableListOf<Anchor>()
    for (row in anchorRows) {
        val anchor = when (row.kind) {
            AnchorKind.AFTER_HABIT -> {
                val referencedId = row.anchorHabitId
                    ?: return HabitReadResult.Malformed(entity.id, "AFTER_HABIT anchor ${row.id} has no anchorHabitId")
                val referencedDays = anchorHabitDays(referencedId)
                    ?: return HabitReadResult.Malformed(entity.id, "anchor habit $referencedId has no days")
                val referencedFrequency = if (referencedDays == DayOfWeek.entries.toSet()) {
                    HabitFrequency.Daily
                } else {
                    when (val result = HabitFrequency.OnDays.of(referencedDays)) {
                        is HabitFrequencyResult.Valid -> result.frequency
                        else -> return HabitReadResult.Malformed(entity.id, "anchor habit $referencedId has no valid days")
                    }
                }
                Anchor.AfterHabit(HabitId(referencedId), referencedFrequency)
            }

            AnchorKind.AT_DAY_SEGMENT -> {
                val segment = row.daySegment
                    ?: return HabitReadResult.Malformed(entity.id, "AT_DAY_SEGMENT anchor ${row.id} has no daySegment")
                Anchor.AtDaySegment(segment)
            }

            AnchorKind.AT_CLOCK_TIME -> {
                val time = row.clockTime
                    ?: return HabitReadResult.Malformed(entity.id, "AT_CLOCK_TIME anchor ${row.id} has no clockTime")
                Anchor.AtClockTime(time)
            }
        }
        anchors += anchor
    }

    val checkedAnchors = when (val result = Anchors.of(anchors)) {
        is AnchorsResult.Valid -> result.anchors
        is AnchorsResult.Rejected.Empty ->
            // Reachable: the anchor habit was deleted and its cue cascaded away. Surfaced, never
            // replaced with an invented cue.
            return HabitReadResult.Malformed(entity.id, "no anchors: a habit with no cue is not a habit")
    }

    return HabitReadResult.Valid(
        Habit(
            id = HabitId(entity.id),
            behavior = behavior,
            minimum = minimum,
            frequency = frequency,
            anchors = checkedAnchors,
            identity = identity,
            createdAt = entity.createdAt,
            createdDate = entity.createdDate,
            isActive = entity.isActive,
        ),
    )
}

/** The stored rows for one anchor, in the order the user chose. */
internal fun Anchor.toEntity(habitId: Long, position: Int): HabitAnchorEntity = when (this) {
    is Anchor.AfterHabit -> HabitAnchorEntity(
        habitId = habitId,
        position = position,
        kind = AnchorKind.AFTER_HABIT,
        anchorHabitId = this.habitId.value,
    )

    is Anchor.AtDaySegment -> HabitAnchorEntity(
        habitId = habitId,
        position = position,
        kind = AnchorKind.AT_DAY_SEGMENT,
        daySegment = segment,
    )

    is Anchor.AtClockTime -> HabitAnchorEntity(
        habitId = habitId,
        position = position,
        kind = AnchorKind.AT_CLOCK_TIME,
        clockTime = time,
    )
}
