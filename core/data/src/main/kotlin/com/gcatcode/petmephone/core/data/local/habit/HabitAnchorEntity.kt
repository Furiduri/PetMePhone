package com.gcatcode.petmephone.core.data.local.habit

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gcatcode.petmephone.core.domain.habit.AnchorKind
import com.gcatcode.petmephone.core.domain.habit.DaySegment
import java.time.LocalTime

/**
 * One cue, stored with structure rather than as a serialized blob (#128).
 *
 * ## Why this is a table and not a string column
 *
 * A blob would make the anchor opaque to the database, and that costs two things at once. The
 * frequency refusal `AnchorCompatibility` performs could never be re-checked or queried on read, so
 * a stored anchor that no longer matches its habit's days would be undiscoverable. And
 * [AnchorKind.AFTER_HABIT] is a *reference to another habit* — inside a blob that is a bare number
 * with no foreign key, so deleting the referenced habit would silently orphan everything stacked
 * onto it.
 *
 * ## The discriminated shape
 *
 * [kind] says which variant this is, and exactly one of the variant columns is non-null for each.
 * `AnchorRowMalformed` is what the mapper raises when that stops being true, rather than guessing.
 *
 * [position] preserves the order the user chose. Nothing re-sorts by cue strength: that ordering is
 * for offering types, not for ranking cues already committed to.
 *
 * ## Deleting an anchor habit
 *
 * [anchorHabitId] cascades, so no dangling reference can survive its target. The consequence is
 * real and deliberate: a habit stacked onto a deleted one can end up with **zero** anchors, which
 * the domain forbids. The mapper surfaces that as a malformed habit rather than inventing a cue.
 * What the app should *do* about it — refuse the delete, or re-prompt for a new cue — is a product
 * decision no schema can make, and nothing deletes a habit yet.
 */
@Entity(
    tableName = "HabitAnchor",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["anchorHabitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["habitId", "position"], unique = true),
        Index(value = ["anchorHabitId"]),
    ],
)
data class HabitAnchorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    /** The user's ordering, 0-based and dense within a habit. */
    val position: Int,
    val kind: AnchorKind,
    /** Set only for [AnchorKind.AFTER_HABIT]; a real foreign key, never a bare number. */
    val anchorHabitId: Long? = null,
    /** Set only for [AnchorKind.AT_DAY_SEGMENT]. */
    val daySegment: DaySegment? = null,
    /** Set only for [AnchorKind.AT_CLOCK_TIME]. */
    val clockTime: LocalTime? = null,
)
