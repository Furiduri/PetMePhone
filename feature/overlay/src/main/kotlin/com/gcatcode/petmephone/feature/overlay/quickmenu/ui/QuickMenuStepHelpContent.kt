package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.draft.AuthoringStep
import com.gcatcode.petmephone.feature.overlay.R

/**
 * What a step is for, shown **in the card** in place of that step (#100).
 *
 * Per step, not one shared page. A single generic explanation is what the first wiring shipped, and
 * it told the user nothing about the field in front of them — which is the only thing they opened
 * it to find out.
 *
 * Each one answers *why answering this changes anything*, not what the control does. The user can
 * see the control.
 *
 * Returning must not lose what was already typed, and it does not: the draft is persisted, and this
 * content replaces the step rather than covering it — so there is no second window and nothing to
 * restore.
 */
@Composable
internal fun QuickMenuStepHelpContent(
    step: AuthoringStep,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(CONTENT_PADDING_DP.dp),
        verticalArrangement = Arrangement.spacedBy(FIELD_SPACING_DP.dp),
    ) {
        Text(
            text = stringResource(step.helpTitleRes()),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.testTag(QUICK_MENU_STEP_HELP_TITLE_TEST_TAG),
        )

        Text(
            text = stringResource(step.helpBodyRes()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(QUICK_MENU_STEP_HELP_BODY_TEST_TAG),
        )
        Text(
            text = stringResource(step.helpExampleRes()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(QUICK_MENU_STEP_HELP_EXAMPLE_TEST_TAG),
        )

        val backLabel = stringResource(R.string.feature_overlay_quickmenu_step_help_back_label)
        Button(
            onClick = onLeave,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .testTag(QUICK_MENU_STEP_HELP_BACK_TEST_TAG),
        ) {
            Text(
                backLabel,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun AuthoringStep.helpTitleRes(): Int = when (this) {
    AuthoringStep.BEHAVIOR -> R.string.feature_overlay_quickmenu_help_behavior_title
    AuthoringStep.MINIMUM -> R.string.feature_overlay_quickmenu_help_minimum_title
    AuthoringStep.ANCHOR -> R.string.feature_overlay_quickmenu_help_anchor_title
}

private fun AuthoringStep.helpBodyRes(): Int = when (this) {
    AuthoringStep.BEHAVIOR -> R.string.feature_overlay_quickmenu_help_behavior_body
    AuthoringStep.MINIMUM -> R.string.feature_overlay_quickmenu_help_minimum_body
    AuthoringStep.ANCHOR -> R.string.feature_overlay_quickmenu_help_anchor_body
}

private fun AuthoringStep.helpExampleRes(): Int = when (this) {
    AuthoringStep.BEHAVIOR -> R.string.feature_overlay_quickmenu_help_behavior_example
    AuthoringStep.MINIMUM -> R.string.feature_overlay_quickmenu_help_minimum_example
    AuthoringStep.ANCHOR -> R.string.feature_overlay_quickmenu_help_anchor_example
}

const val QUICK_MENU_STEP_HELP_TITLE_TEST_TAG = "quick_menu_step_help_title"
const val QUICK_MENU_STEP_HELP_BODY_TEST_TAG = "quick_menu_step_help_body"
const val QUICK_MENU_STEP_HELP_EXAMPLE_TEST_TAG = "quick_menu_step_help_example"
const val QUICK_MENU_STEP_HELP_BACK_TEST_TAG = "quick_menu_step_help_back"

private const val CONTENT_PADDING_DP = 16
private const val FIELD_SPACING_DP = 12
