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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
 * One step of the authoring form: a single input, its progress, and the three ways out (#100).
 *
 * This is the parameterised generalisation of [QuickMenuTaskInputContent] — same measured
 * constraints, same action-row geometry, but the label, placeholder, value and actions are supplied
 * per step instead of being one hard-coded field.
 *
 * ## One input, because the keyboard's height is unknowable here
 *
 * #82 measured it on real hardware: keyboard insets are never delivered to an overlay window. The
 * listener attaches and fires but never reports a non-zero keyboard inset, so every Compose helper
 * that pads around the keyboard is unusable on this surface — which is why this package is
 * forbidden from naming any of them, and why `QuickMenuNoKeyboardSignalCodeTest` counts string
 * occurrences bluntly enough to catch a mention in a comment like this one.
 *
 * A form with four stacked fields would put the lower ones under a keyboard whose height the app
 * cannot discover. One field, placed high, is a requirement rather than a preference.
 *
 * ## This declares no height at all
 *
 * The window is `WRAP_CONTENT`, so the card is exactly as tall as whatever it holds — and
 * `QuickMenuWindowParams` records that a fixed height was guessed twice and wrong twice before a
 * third guess here clipped the action row off the bottom on a real device.
 *
 * So nothing here names a height. The step is as tall as it needs to be, and it scrolls, so a small
 * screen or a large font scale squeezes it instead of hiding a control. The card may change size
 * between steps; that is the trade taken deliberately, because a size change is visible and a
 * clipped button is not.
 *
 * ## The draft is durable; the cursor is local
 *
 * [value] is the persisted draft, and every edit is reported up so it keeps being persisted — a
 * draft that lives only in the composition dies to a service restart.
 *
 * But the draft round-trips through the database, so it echoes back *after* the keystroke that
 * caused it. Rendering the field straight from it made the field lag by one character, and because
 * a `String`-valued field carries no selection, each out-of-band value dropped the cursor at index
 * 0 and the next character landed in front: typing "Hola" produced "olaH" on a real device.
 *
 * So editing is buffered here as a [TextFieldValue], which carries the selection. [value] seeds it
 * and is adopted whenever it genuinely differs — reopening a saved draft, or moving between steps —
 * but the echo of what was just typed changes nothing, because by then the two already agree.
 *
 * ## Help replaces the content in place
 *
 * [onHelp] swaps the card's content rather than opening anything. A tooltip or popup would be a
 * second `TYPE_APPLICATION_OVERLAY` window with its own dismissal handling, touch conflicts and
 * gravity defects — the class of problem #28 and #87 already document.
 */
@Composable
fun QuickMenuStepContent(
    stepNumber: Int,
    stepCount: Int,
    label: String,
    placeholder: String,
    value: String,
    maxLength: Int,
    /** Null on the last step, where [onSubmit] takes over. */
    onNext: (() -> Unit)?,
    onSubmit: (() -> Unit)?,
    onValueChange: (String) -> Unit,
    onCancel: () -> Unit,
    onHelp: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed by the step, so moving between steps reseeds the buffer instead of carrying the
    // previous field's text and cursor across.
    var field by remember(label) {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    // Adopts an external change only. When this fires for the echo of a local edit the texts
    // already match, so it is a no-op and the cursor stays where the user left it.
    LaunchedEffect(value) {
        if (value != field.text) {
            field = TextFieldValue(value, TextRange(value.length))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // No height: the window wraps this, and the scroll means a squeeze never hides a
            // control. Same shape as QuickMenuDashboardContent.
            .verticalScroll(rememberScrollState())
            .padding(CONTENT_PADDING_DP.dp),
        verticalArrangement = Arrangement.spacedBy(FIELD_SPACING_DP.dp),
    ) {
        // Progress, because one input per card with no sense of how many remain reads as endless,
        // and endless is abandoned.
        val progress = stringResource(
            R.string.feature_overlay_quickmenu_step_progress,
            stepNumber,
            stepCount,
        )
        Text(
            text = progress,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.testTag(QUICK_MENU_STEP_PROGRESS_TEST_TAG),
        )

        OutlinedTextField(
            value = field,
            onValueChange = { newValue ->
                if (newValue.text.length <= maxLength) {
                    field = newValue
                    // Reported only when the text itself changed: a bare cursor move is not an edit
                    // and must not cost a database write.
                    if (newValue.text != value) onValueChange(newValue.text)
                }
            },
            // Label and placeholder both: the placeholder vanishes the moment anything is typed,
            // and the field would then be an unlabelled box.
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = if (onNext != null) ImeAction.Next else ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                // Focus is a fact this app owns; the IME never reports itself to this window class.
                .onFocusChanged { onFocusChanged(it.isFocused) }
                .semantics { contentDescription = label }
                .testTag(QUICK_MENU_STEP_FIELD_TEST_TAG),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ACTION_SPACING_DP.dp),
        ) {
            // Equal weights and no wrapping, for the reason the task-input row records: with
            // SpaceBetween the longer label claimed the width it wanted and the submit button
            // rendered one letter per line on a real device.
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
                StepActionLabel(cancelLabel)
            }

            val helpLabel = stringResource(R.string.feature_overlay_quickmenu_task_input_help_label)
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
                StepActionLabel(helpLabel)
            }

            val advanceLabel = if (onNext != null) {
                stringResource(R.string.feature_overlay_quickmenu_step_next_label)
            } else {
                stringResource(R.string.feature_overlay_quickmenu_step_submit_label)
            }
            val advanceDescription = if (onNext != null) {
                stringResource(R.string.feature_overlay_quickmenu_step_next_description)
            } else {
                stringResource(R.string.feature_overlay_quickmenu_step_submit_description)
            }
            Button(
                onClick = { onNext?.invoke() ?: onSubmit?.invoke() },
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = advanceDescription }
                    .testTag(QUICK_MENU_STEP_ADVANCE_TEST_TAG),
            ) {
                StepActionLabel(advanceLabel)
            }
        }
    }
}

@Composable
private fun StepActionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

const val QUICK_MENU_STEP_FIELD_TEST_TAG = "quick_menu_step_field"
const val QUICK_MENU_STEP_PROGRESS_TEST_TAG = "quick_menu_step_progress"
const val QUICK_MENU_STEP_CANCEL_TEST_TAG = "quick_menu_step_cancel"
const val QUICK_MENU_STEP_HELP_TEST_TAG = "quick_menu_step_help"
const val QUICK_MENU_STEP_ADVANCE_TEST_TAG = "quick_menu_step_advance"

private const val CONTENT_PADDING_DP = 16
private const val FIELD_SPACING_DP = 12
private const val ACTION_SPACING_DP = 8
