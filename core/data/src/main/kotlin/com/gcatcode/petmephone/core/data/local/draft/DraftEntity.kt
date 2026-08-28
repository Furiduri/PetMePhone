package com.gcatcode.petmephone.core.data.local.draft

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gcatcode.petmephone.core.domain.draft.DraftKind
import com.gcatcode.petmephone.core.domain.habit.AnchorKind
import com.gcatcode.petmephone.core.domain.habit.DaySegment
import java.time.Instant
import java.time.LocalTime

/**
 * The single pending [com.gcatcode.petmephone.core.domain.draft.AuthoringDraft] (#100).
 *
 * ## One row, enforced by the key
 *
 * [id] is fixed at [SINGLETON_ID] rather than generated, so "one draft at a time" is a property of
 * the schema instead of a rule some future writer has to remember. A save is a REPLACE onto the
 * same key; there is no second row to reconcile because there is no second key.
 *
 * ## The anchor reference carries no foreign key, on purpose
 *
 * Everywhere else a habit reference is a real foreign key. Here it must not be: a CASCADE would
 * delete the user's half-written draft because they tidied up an unrelated habit, and that is the
 * app throwing away their words. A draft is scratch state, not a record.
 *
 * The cost is that the reference can dangle, and that is handled on read rather than pretended
 * away — an anchor pointing at a habit that no longer exists reads back as *no anchor chosen*, and
 * the user picks again. Losing one field beats losing the draft.
 */
@Entity(tableName = "AuthoringDraft")
data class DraftEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val kind: DraftKind,
    val rawBehavior: String,
    val rawMinimum: String,
    val anchorKind: AnchorKind? = null,
    val anchorHabitId: Long? = null,
    val anchorDaySegment: DaySegment? = null,
    val anchorClockTime: LocalTime? = null,
    val step: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        /** The only key this table ever holds. */
        const val SINGLETON_ID = 1
    }
}
