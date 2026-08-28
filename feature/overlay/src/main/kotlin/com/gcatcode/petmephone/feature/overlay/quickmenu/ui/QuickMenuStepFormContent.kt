package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gcatcode.petmephone.core.domain.draft.AuthoringFlow
import com.gcatcode.petmephone.core.domain.draft.AuthoringStep
import com.gcatcode.petmephone.core.domain.draft.DraftKind
import com.gcatcode.petmephone.core.domain.habit.DaySegment
import com.gcatcode.petmephone.feature.overlay.R

/**
 * What the card needs to render one step of the authoring form. Everything is supplied from
 * outside: this content owns no state, because every keystroke has to reach the persisted draft.
 */
data class StepFormUiState(
    val kind: DraftKind,
    /** The text of the step currently showing. Empty is a legitimate value, not a missing one. */
    val value: String,
    /** The cue chosen so far, or null on a habit whose cue step has not been answered. */
    val segment: DaySegment?,
    val maxLength: Int,
)

/**
 * Picks the control the step needs and hands it the copy.
 *
 * The text steps and the cue step are different controls — a cue is a choice, not a sentence — but
 * they are the same height and the same action row, so moving between them does not move the card.
 */
@Composable
internal fun QuickMenuStepFormContent(
    state: StepFormUiState?,
    stepIndex: Int,
    heightDp: Int,
    onValueChange: (String) -> Unit,
    onSegmentSelected: (DaySegment) -> Unit,
    onAdvance: () -> Unit,
    onCancel: () -> Unit,
    onHelp: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    /** Called when the step cannot be rendered; see below. */
    onRecover: () -> Unit,
) {
    // Two ways this can be unrenderable: no draft at all, or an index the flow does not contain.
    // Neither is guessed around — showing step one would drop the user somewhere they did not leave
    // off, and rendering an empty form would invite them to type into nothing.
    val step = state?.let { AuthoringFlow.stepAt(it.kind, stepIndex) }
    if (state == null || step == null) {
        QuickMenuInstructionsContent(minHeightDp = heightDp, onLeave = onRecover)
        return
    }

    val stepCount = AuthoringFlow.stepCount(state.kind)
    val stepNumber = stepIndex + 1
    val isLast = AuthoringFlow.isLastStep(state.kind, stepIndex)

    when (step) {
        AuthoringStep.BEHAVIOR, AuthoringStep.MINIMUM -> QuickMenuStepContent(
            stepNumber = stepNumber,
            stepCount = stepCount,
            label = stringResource(step.labelRes()),
            placeholder = stringResource(step.placeholderRes()),
            value = state.value,
            maxLength = state.maxLength,
            heightDp = heightDp,
            onNext = if (isLast) null else onAdvance,
            onSubmit = if (isLast) onAdvance else null,
            onValueChange = onValueChange,
            onCancel = onCancel,
            onHelp = onHelp,
            onFocusChanged = onFocusChanged,
        )

        AuthoringStep.ANCHOR -> QuickMenuAnchorStepContent(
            stepNumber = stepNumber,
            stepCount = stepCount,
            selected = state.segment,
            heightDp = heightDp,
            onSelect = onSegmentSelected,
            onSubmit = onAdvance,
            onCancel = onCancel,
            onHelp = onHelp,
        )
    }
}

private fun AuthoringStep.labelRes(): Int = when (this) {
    AuthoringStep.BEHAVIOR -> R.string.feature_overlay_quickmenu_step_behavior_label
    AuthoringStep.MINIMUM -> R.string.feature_overlay_quickmenu_step_minimum_label
    AuthoringStep.ANCHOR -> R.string.feature_overlay_quickmenu_step_anchor_prompt
}

/**
 * The minimum's placeholder does the most work of any string here. It is the field users will not
 * understand from its label, it is required, and an example teaches it in one line where a
 * definition does not: "Clean the kitchen" against "Put three dishes in the sink".
 */
private fun AuthoringStep.placeholderRes(): Int = when (this) {
    AuthoringStep.BEHAVIOR -> R.string.feature_overlay_quickmenu_step_behavior_placeholder
    AuthoringStep.MINIMUM -> R.string.feature_overlay_quickmenu_step_minimum_placeholder
    AuthoringStep.ANCHOR -> R.string.feature_overlay_quickmenu_step_anchor_prompt
}
