package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.feature.overlay.R

/**
 * The task-input content (`quick-menu-text-input` spec): a length-bounded text field, a submit
 * action, and a leave-to-dashboard action. Submission is out of scope for this change — [onSubmit]
 * is wired to a no-op logging lambda by the controller (design decision 12); **no
 * `:core:domain/task` import exists anywhere in this file**, per the orchestrator's hard
 * constraint and the threat matrix's "untrusted input crossing the window boundary" row.
 *
 * The field's text is deliberately **not** hoisted: [rememberSaveable] here still only survives a
 * process-owned configuration change with a `SavedStateRegistry`, and this window has none (design
 * decision 5) — the whole composition is destroyed on every dismissal, so the state dies with it
 * and there is no clearing call anyone could forget. `rememberSaveable` is used only because it is
 * the same composition-local mechanism `remember` is here, not because anything is actually saved
 * across the boundary that matters (the window removal).
 *
 * No `FocusRequester` exists in this file, and no `showSoftInput` call is made anywhere: the field
 * relies entirely on the platform's default tap-to-focus behaviour, so it is focused only in
 * direct response to a real tap (design decision 10) — no auto-focus on this content's first
 * composition, including when it is the content a restoration-path reopen renders directly, and
 * none on the swap back here from the instructions content either.
 *
 * Every control here carries **two** strings: a short visible label and a longer
 * `contentDescription`. They were once the same string, and the longer one ("Back to the
 * dashboard") ate the action row's width on a real device until the submit button rendered one
 * letter per line. The row below therefore does not rely on labels staying short: the two wide
 * actions take equal weights and refuse to wrap, so no translation can bring the letter stack back.
 */
@Composable
fun QuickMenuTaskInputContent(
    taskTitleMaxLength: Int,
    minHeightDp: Int,
    onSubmit: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onLeave: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeightDp.dp)
            .padding(CONTENT_PADDING_DP.dp),
        verticalArrangement = Arrangement.spacedBy(FIELD_SPACING_DP.dp),
    ) {
        val fieldDescription =
            stringResource(R.string.feature_overlay_quickmenu_task_input_field_description)
        val placeholder = stringResource(R.string.feature_overlay_quickmenu_task_input_placeholder)
        val fieldLabel = stringResource(R.string.feature_overlay_quickmenu_task_input_field_label)

        OutlinedTextField(
            value = title,
            onValueChange = { newValue ->
                if (newValue.length <= taskTitleMaxLength) title = newValue
            },
            // Label *and* placeholder: the placeholder disappears the moment anything is typed,
            // and the field would then be an unlabelled box. The label survives typing.
            //
            // Measured, not assumed: with a label present, Material3 shows the label in the empty
            // unfocused box and reveals the placeholder on focus. Both strings therefore stay
            // reachable, and `QuickMenuTaskInputContentTest` asserts each in the state it occupies.
            label = { Text(fieldLabel) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                // The card's placement follows this, never a keyboard or inset signal: the IME
                // never reports itself to this window class, and focus is a fact this app owns.
                .onFocusChanged { onFocusChanged(it.isFocused) }
                .semantics { contentDescription = fieldDescription }
                .testTag(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ACTION_SPACING_DP.dp),
        ) {
            // Labelled buttons, not bare glyphs — this project has no icon-font dependency, and a
            // labelled control is the established convention here (see QuickMenuDashboardContent's
            // add-task control history).
            //
            // `weight(1f)` on the two wide actions, not SpaceBetween: with SpaceBetween the longer
            // label claimed the width it wanted and the other button was squeezed to its minimum.
            // Equal weights make the split independent of label length, and `softWrap = false`
            // means a label too long for its half ellipsises rather than stacking letters
            // vertically — the visible defect this replaces.
            val leaveLabel = stringResource(R.string.feature_overlay_quickmenu_task_input_leave_label)
            val leaveDescription =
                stringResource(R.string.feature_overlay_quickmenu_task_input_leave_description)
            OutlinedButton(
                onClick = onLeave,
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = leaveDescription }
                    .testTag(QUICK_MENU_TASK_INPUT_LEAVE_TEST_TAG),
            ) {
                Text(
                    leaveLabel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val helpLabel = stringResource(R.string.feature_overlay_quickmenu_task_input_help_label)
            val helpDescription =
                stringResource(R.string.feature_overlay_quickmenu_task_input_help_description)
            // Unweighted and fixed at the touch-target minimum: a one-glyph control has no reason
            // to claim a share of the row, and keeping it out of the weighting leaves the two real
            // actions an even split.
            OutlinedButton(
                onClick = onHelp,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = helpDescription }
                    .testTag(QUICK_MENU_TASK_INPUT_HELP_TEST_TAG),
            ) {
                Text(
                    helpLabel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                )
            }

            val submitLabel = stringResource(R.string.feature_overlay_quickmenu_task_input_submit_label)
            val submitDescription =
                stringResource(R.string.feature_overlay_quickmenu_task_input_submit_description)
            Button(
                onClick = { onSubmit(title) },
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = submitDescription }
                    .testTag(QUICK_MENU_TASK_INPUT_SUBMIT_TEST_TAG),
            ) {
                Text(
                    submitLabel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

const val QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG = "quick_menu_task_input_field"
const val QUICK_MENU_TASK_INPUT_SUBMIT_TEST_TAG = "quick_menu_task_input_submit"
const val QUICK_MENU_TASK_INPUT_LEAVE_TEST_TAG = "quick_menu_task_input_leave"
const val QUICK_MENU_TASK_INPUT_HELP_TEST_TAG = "quick_menu_task_input_help"

private const val CONTENT_PADDING_DP = 16
private const val FIELD_SPACING_DP = 12
private const val ACTION_SPACING_DP = 8
