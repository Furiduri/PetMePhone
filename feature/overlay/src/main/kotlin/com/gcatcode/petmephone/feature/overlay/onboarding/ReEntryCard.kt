package com.gcatcode.petmephone.feature.overlay.onboarding

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.feature.overlay.R

/**
 * Passive, dismissible re-entry point for a user who refused the overlay permission once
 * (`[ONBOARD-5]`). Never auto-shows the onboarding flow itself — [onReopenOnboarding] only fires
 * on an explicit tap, and [onDismiss] never re-launches onboarding.
 */
@Composable
fun ReEntryCard(
    onReopenOnboarding: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.testTag(REENTRY_CARD_TEST_TAG)) {
        Row(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.reentry_card_message))

            val reopenDescription = stringResource(R.string.reentry_card_reopen_description)
            MinTouchTargetButton(
                onClick = onReopenOnboarding,
                contentDescription = reopenDescription,
                testTag = REENTRY_CARD_REOPEN_TEST_TAG,
                label = "Open",
            )

            val dismissDescription = stringResource(R.string.reentry_card_dismiss_description)
            MinTouchTargetButton(
                onClick = onDismiss,
                contentDescription = dismissDescription,
                testTag = REENTRY_CARD_DISMISS_TEST_TAG,
                label = "Dismiss",
            )
        }
    }
}

/** Shared 48dp-minimum, content-described button — the accessibility floor every interactive
 *  element on this card meets (`[ONBOARD-7]`). */
@Composable
private fun RowScope.MinTouchTargetButton(
    onClick: () -> Unit,
    contentDescription: String,
    testTag: String,
    label: String,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .padding(start = 8.dp)
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag),
    ) {
        Text(label)
    }
}

const val REENTRY_CARD_TEST_TAG = "reentry_card"
const val REENTRY_CARD_REOPEN_TEST_TAG = "reentry_card_reopen"
const val REENTRY_CARD_DISMISS_TEST_TAG = "reentry_card_dismiss"
