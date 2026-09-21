package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.habit.DaySegment
import com.gcatcode.petmephone.feature.overlay.R

/**
 * The cue step: pick the part of the day this habit hangs off (#100).
 *
 * ## Why only day segments here
 *
 * #98 ranks cues by strength — after another habit, then a day segment, then a clock time — and
 * #100 scopes the overlay to *what is required*, offering the rest in the full app.
 *
 * The two stronger and weaker ends both need a control this card cannot afford. "After another
 * habit" needs a browsable list of the user's habits; a clock time needs a time picker, which on
 * this surface would mean a second overlay window — exactly the thing #28 and #87 document as a
 * defect class. A day segment needs three buttons and no reference to anything, so it is the one
 * cue the overlay can collect honestly.
 *
 * The full app is where a habit gets stacked onto another one. That is a deliberate split, not a
 * missing feature.
 *
 * Like [QuickMenuStepContent] it declares no height: the window wraps its content, and a fixed
 * height was already guessed wrong three times in this package.
 */
@Composable
fun QuickMenuAnchorStepContent(
    stepNumber: Int,
    stepCount: Int,
    selected: DaySegment?,
    onSelect: (DaySegment) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onHelp: () -> Unit,
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
            text = stringResource(
                R.string.feature_overlay_quickmenu_step_progress,
                stepNumber,
                stepCount,
            ),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.testTag(QUICK_MENU_STEP_PROGRESS_TEST_TAG),
        )

        Text(
            text = stringResource(R.string.feature_overlay_quickmenu_step_anchor_prompt),
            style = MaterialTheme.typography.bodyMedium,
        )

        // Stacked, not across. Measured: the card is 280dp wide, so three weighted buttons leave
        // roughly 53dp of text room each once their internal padding is taken, and "Afternoon"
        // needs more — the labels ellipsised into unreadable stubs on device. A choice the user
        // cannot read is not a choice.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ACTION_SPACING_DP.dp),
        ) {
            DaySegment.entries.forEach { segment ->
                val label = stringResource(segment.labelRes())
                // Selected reads as filled, unselected as outlined — a state a screen reader also
                // gets, through the description rather than through the shape alone.
                val description = if (segment == selected) {
                    stringResource(R.string.feature_overlay_quickmenu_step_anchor_selected_description, label)
                } else {
                    stringResource(R.string.feature_overlay_quickmenu_step_anchor_choice_description, label)
                }
                val choiceModifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .semantics { contentDescription = description }
                    .testTag(segment.testTag())

                if (segment == selected) {
                    Button(onClick = { onSelect(segment) }, modifier = choiceModifier) {
                        SegmentLabel(label)
                    }
                } else {
                    OutlinedButton(onClick = { onSelect(segment) }, modifier = choiceModifier) {
                        SegmentLabel(label)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ACTION_SPACING_DP.dp),
        ) {
            val cancelLabel = stringResource(R.string.feature_overlay_quickmenu_step_cancel_label)
            val cancelDescription =
                stringResource(R.string.feature_overlay_quickmenu_step_cancel_description)
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = cancelDescription }
                    .testTag(QUICK_MENU_STEP_CANCEL_TEST_TAG),
            ) {
                SegmentLabel(cancelLabel)
            }

            val helpDescription =
                stringResource(R.string.feature_overlay_quickmenu_step_help_description)
            OutlinedButton(
                onClick = onHelp,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = helpDescription }
                    .testTag(QUICK_MENU_STEP_HELP_TEST_TAG),
            ) {
                SegmentLabel(stringResource(R.string.feature_overlay_quickmenu_step_help_label))
            }

            val submitLabel = stringResource(R.string.feature_overlay_quickmenu_step_submit_label)
            val submitDescription =
                stringResource(R.string.feature_overlay_quickmenu_step_submit_description)
            Button(
                // Disabled until a cue is chosen: a habit without one is not a habit, and letting
                // the press through only to refuse it teaches nothing.
                enabled = selected != null,
                onClick = onSubmit,
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = submitDescription }
                    .testTag(QUICK_MENU_STEP_ADVANCE_TEST_TAG),
            ) {
                SegmentLabel(submitLabel)
            }
        }
    }
}

@Composable
private fun SegmentLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun DaySegment.labelRes(): Int = when (this) {
    DaySegment.MORNING -> R.string.feature_overlay_quickmenu_step_anchor_morning
    DaySegment.AFTERNOON -> R.string.feature_overlay_quickmenu_step_anchor_afternoon
    DaySegment.EVENING -> R.string.feature_overlay_quickmenu_step_anchor_evening
}

private fun DaySegment.testTag(): String = when (this) {
    DaySegment.MORNING -> QUICK_MENU_ANCHOR_MORNING_TEST_TAG
    DaySegment.AFTERNOON -> QUICK_MENU_ANCHOR_AFTERNOON_TEST_TAG
    DaySegment.EVENING -> QUICK_MENU_ANCHOR_EVENING_TEST_TAG
}

const val QUICK_MENU_ANCHOR_MORNING_TEST_TAG = "quick_menu_anchor_morning"
const val QUICK_MENU_ANCHOR_AFTERNOON_TEST_TAG = "quick_menu_anchor_afternoon"
const val QUICK_MENU_ANCHOR_EVENING_TEST_TAG = "quick_menu_anchor_evening"

private const val CONTENT_PADDING_DP = 16
private const val FIELD_SPACING_DP = 12
private const val ACTION_SPACING_DP = 8
