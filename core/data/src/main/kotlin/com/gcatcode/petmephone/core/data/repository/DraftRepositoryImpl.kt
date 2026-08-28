package com.gcatcode.petmephone.core.data.repository

import com.gcatcode.petmephone.core.data.local.draft.DraftDao
import com.gcatcode.petmephone.core.data.local.draft.DraftEntity
import com.gcatcode.petmephone.core.data.local.habit.HabitDao
import com.gcatcode.petmephone.core.domain.draft.AuthoringDraft
import com.gcatcode.petmephone.core.domain.draft.DraftRepository
import com.gcatcode.petmephone.core.domain.habit.Anchor
import com.gcatcode.petmephone.core.domain.habit.AnchorKind
import com.gcatcode.petmephone.core.domain.habit.HabitFrequency
import com.gcatcode.petmephone.core.domain.habit.HabitFrequencyResult
import com.gcatcode.petmephone.core.domain.habit.HabitId
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [DraftRepository] (#100). The draft lives in the database rather than in memory, so
 * it survives the screen turning off, the overlay service restarting and process death — not just
 * the outside tap that prompted the requirement.
 */
class DraftRepositoryImpl @Inject constructor(
    private val draftDao: DraftDao,
    private val habitDao: HabitDao,
) : DraftRepository {

    override fun current(): Flow<AuthoringDraft?> =
        draftDao.observe().map { entity -> entity?.toDomain() }

    override suspend fun save(draft: AuthoringDraft) {
        draftDao.upsert(
            DraftEntity(
                kind = draft.kind,
                rawBehavior = draft.rawBehavior,
                rawMinimum = draft.rawMinimum,
                anchorKind = draft.anchor?.kind,
                anchorHabitId = (draft.anchor as? Anchor.AfterHabit)?.habitId?.value,
                anchorDaySegment = (draft.anchor as? Anchor.AtDaySegment)?.segment,
                anchorClockTime = (draft.anchor as? Anchor.AtClockTime)?.time,
                step = draft.step,
                createdAt = draft.createdAt,
                updatedAt = draft.updatedAt,
            ),
        )
    }

    override suspend fun discard() = draftDao.clear()

    private suspend fun DraftEntity.toDomain(): AuthoringDraft = AuthoringDraft(
        kind = kind,
        rawBehavior = rawBehavior,
        rawMinimum = rawMinimum,
        anchor = resolveAnchor(),
        step = step,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    /**
     * Rebuilds the chosen cue, or reports none.
     *
     * An [AnchorKind.AFTER_HABIT] draft anchor carries no foreign key (see [DraftEntity]), so the
     * habit it names may since have been deleted. That reads back as **no anchor chosen** rather
     * than failing the draft: the user re-picks one field instead of losing everything they typed.
     */
    private suspend fun DraftEntity.resolveAnchor(): Anchor? = when (anchorKind) {
        null -> null

        AnchorKind.AFTER_HABIT -> {
            val referencedId = anchorHabitId
            val days = referencedId?.let { habitDao.days(it).map { row -> row.dayOfWeek }.toSet() }
            when {
                referencedId == null || days.isNullOrEmpty() -> null
                days == DayOfWeek.entries.toSet() ->
                    Anchor.AfterHabit(HabitId(referencedId), HabitFrequency.Daily)
                else -> when (val result = HabitFrequency.OnDays.of(days)) {
                    is HabitFrequencyResult.Valid -> Anchor.AfterHabit(HabitId(referencedId), result.frequency)
                    else -> null
                }
            }
        }

        AnchorKind.AT_DAY_SEGMENT -> anchorDaySegment?.let(Anchor::AtDaySegment)

        AnchorKind.AT_CLOCK_TIME -> anchorClockTime?.let(Anchor::AtClockTime)
    }
}
