package com.gcatcode.petmephone.feature.overlay.quickmenu

import com.gcatcode.petmephone.core.domain.draft.AuthoringDraft
import com.gcatcode.petmephone.core.domain.draft.AuthoringFlow
import com.gcatcode.petmephone.core.domain.draft.AuthoringStep
import com.gcatcode.petmephone.core.domain.draft.DraftKind
import com.gcatcode.petmephone.core.domain.draft.DraftRepository
import com.gcatcode.petmephone.core.domain.draft.SubmitDraft
import com.gcatcode.petmephone.core.domain.draft.SubmitDraftResult
import com.gcatcode.petmephone.core.domain.habit.Anchor
import com.gcatcode.petmephone.core.domain.habit.DaySegment
import com.gcatcode.petmephone.core.domain.time.AppClock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Drives the authoring form: reads the persisted draft, writes every edit straight back to it, and
 * submits when the last step is done (#100).
 *
 * ## Every change is written, not batched
 *
 * There is no "save on advance" here. A batched write would lose the current step's text to
 * everything the draft exists to survive — the screen turning off mid-sentence, the service being
 * restarted, process death. The draft is the single copy of what the user typed; the composition
 * holds none of it.
 *
 * ## Suspend functions, not fire-and-forget
 *
 * Each of these returns only once the write is committed, so the caller decides which scope it runs
 * in. That scope must be application- or service-scoped and never `rememberCoroutineScope()`: a
 * card dismissed the instant after a keystroke must not lose it.
 */
class AuthoringFormController @Inject constructor(
    private val drafts: DraftRepository,
    private val submitDraft: SubmitDraft,
    private val clock: AppClock,
) {
    /** The pending draft, whatever step it is on. */
    val draft: Flow<AuthoringDraft?> = drafts.current()

    /** The text of [stepIndex] for the current draft, or empty when that step is not a text step. */
    fun valueAt(draft: AuthoringDraft, stepIndex: Int): String =
        when (AuthoringFlow.stepAt(draft.kind, stepIndex)) {
            AuthoringStep.BEHAVIOR -> draft.rawBehavior
            AuthoringStep.MINIMUM -> draft.rawMinimum
            AuthoringStep.ANCHOR, null -> ""
        }

    /** Starts a fresh draft, replacing any pending one. */
    suspend fun start(kind: DraftKind) {
        val now = clock.now()
        drafts.save(
            AuthoringDraft(
                kind = kind,
                rawBehavior = "",
                rawMinimum = "",
                anchor = null,
                step = 0,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** Writes [value] into whichever field [stepIndex] names. */
    suspend fun edit(draft: AuthoringDraft, stepIndex: Int, value: String) {
        val edited = when (AuthoringFlow.stepAt(draft.kind, stepIndex)) {
            AuthoringStep.BEHAVIOR -> draft.copy(rawBehavior = value)
            AuthoringStep.MINIMUM -> draft.copy(rawMinimum = value)
            // A text edit arriving for the cue step means the form and the flow disagree. Ignoring
            // it beats writing the sentence into a field that does not hold one.
            AuthoringStep.ANCHOR, null -> return
        }
        drafts.save(edited.copy(updatedAt = clock.now()))
    }

    /** Records the chosen cue. Day segments only on this surface — see the anchor step's doc. */
    suspend fun chooseSegment(draft: AuthoringDraft, segment: DaySegment) {
        drafts.save(draft.copy(anchor = Anchor.AtDaySegment(segment), updatedAt = clock.now()))
    }

    /**
     * Moves to the next step, or submits when there is none.
     *
     * Returns the index to show next, or null once the draft has been submitted — so the caller
     * never re-derives the end of the flow and gets the off-by-one wrong.
     */
    suspend fun advance(draft: AuthoringDraft, stepIndex: Int): AdvanceOutcome {
        val next = AuthoringFlow.nextIndex(draft.kind, stepIndex)
        if (next != null) {
            drafts.save(draft.copy(step = next, updatedAt = clock.now()))
            return AdvanceOutcome.ShowStep(next)
        }
        return when (val result = submitDraft(draft)) {
            is SubmitDraftResult.Created -> AdvanceOutcome.Submitted
            is SubmitDraftResult.Rejected -> AdvanceOutcome.Refused(result)
        }
    }

    /**
     * Cancel, and the only thing that throws the draft away.
     *
     * Outside tap must keep dismissing the window without discarding, which is exactly why this
     * control has to exist: without it a draft would be unabandonable.
     */
    suspend fun cancel() = drafts.discard()

    /** True while a draft is worth offering back. */
    fun offerable(maxAgeHours: Int): Flow<AuthoringDraft?> = drafts.current().map { pending ->
        pending?.takeIf {
            it.hasContent() &&
                it.isOfferable(clock.now(), java.time.Duration.ofHours(maxAgeHours.toLong()))
        }
    }
}

/** What [AuthoringFormController.advance] decided. */
sealed interface AdvanceOutcome {
    data class ShowStep(val stepIndex: Int) : AdvanceOutcome

    /** The habit exists and the draft is gone. */
    data object Submitted : AdvanceOutcome

    /** The domain refused it; the reason is carried through so the form can say what was wrong. */
    data class Refused(val reason: SubmitDraftResult.Rejected) : AdvanceOutcome
}
