package com.gcatcode.petmephone.core.domain.draft

import com.gcatcode.petmephone.core.domain.habit.CreateHabit
import com.gcatcode.petmephone.core.domain.habit.CreateHabitResult
import com.gcatcode.petmephone.core.domain.habit.HabitFrequency
import com.gcatcode.petmephone.core.domain.habit.HabitId

/**
 * Turns a finished [AuthoringDraft] into a real habit, and clears the draft once it is one (#100).
 *
 * The draft is discarded **only after** the write succeeds. Clearing first would lose the user's
 * words to a failed insert, and a form that eats what you typed when something goes wrong is worse
 * than one that never saved it.
 */
class SubmitDraft(
    private val drafts: DraftRepository,
    private val createHabit: CreateHabit,
) {
    suspend operator fun invoke(draft: AuthoringDraft): SubmitDraftResult {
        if (draft.kind == DraftKind.TASK) {
            // Not an oversight and not a TODO: `Task` carries a title, with no column for a
            // minimum. Submitting here would silently drop the field #98 makes mandatory, which is
            // worse than refusing — so this refuses, in a way the caller has to handle.
            return SubmitDraftResult.Rejected.TaskMinimumNotPersistable
        }

        val anchor = draft.anchor
            ?: return SubmitDraftResult.Rejected.Incomplete(AuthoringStep.ANCHOR)
        if (draft.rawBehavior.isBlank()) {
            return SubmitDraftResult.Rejected.Incomplete(AuthoringStep.BEHAVIOR)
        }
        if (draft.rawMinimum.isBlank()) {
            return SubmitDraftResult.Rejected.Incomplete(AuthoringStep.MINIMUM)
        }

        // Daily until the full app collects a frequency: the overlay deliberately gathers only what
        // #100 calls required, and a habit with no chosen frequency is one the user means to do
        // every day rather than one with no days at all.
        val result = createHabit(
            rawBehavior = draft.rawBehavior,
            rawMinimum = draft.rawMinimum,
            frequency = HabitFrequency.Daily,
            anchors = listOf(anchor),
        )

        return when (result) {
            is CreateHabitResult.Created -> {
                drafts.discard()
                SubmitDraftResult.Created(result.id)
            }

            is CreateHabitResult.Rejected -> SubmitDraftResult.Rejected.Refused(result)
        }
    }
}

/** Outcome of [SubmitDraft]. */
sealed interface SubmitDraftResult {
    data class Created(val id: HabitId) : SubmitDraftResult

    sealed interface Rejected : SubmitDraftResult {
        /** A required step was never filled in. Names which, so the form can go back to it. */
        data class Incomplete(val step: AuthoringStep) : Rejected

        /** The domain refused it; the reason is the use case's own, carried through unflattened. */
        data class Refused(val reason: CreateHabitResult.Rejected) : Rejected

        /**
         * A task draft cannot be submitted from here yet, because `Task` has no minimum to store.
         *
         * Kept as a typed refusal rather than a silent drop: #98 makes the minimum mandatory on
         * tasks too, and the day `Task` carries one this case disappears rather than being
         * discovered missing.
         */
        data object TaskMinimumNotPersistable : Rejected
    }
}
