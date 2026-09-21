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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.feature.overlay.R

/**
 * Shown when a step cannot be rendered: no draft at all, or an index the flow does not contain.
 *
 * It replaces the old instructions page, which was being used for this and told the user that
 * adding creates nothing and that typed text is discarded — both false since the draft was
 * persisted. A screen reached by accident must not also be wrong.
 *
 * The copy makes no promise about the draft in either direction, because this content does not know
 * whether one survived. It says what happened and offers the one way out.
 */
@Composable
internal fun QuickMenuStepUnavailableContent(
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
            text = stringResource(R.string.feature_overlay_quickmenu_step_unavailable_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.testTag(QUICK_MENU_STEP_UNAVAILABLE_TITLE_TEST_TAG),
        )
        Text(
            text = stringResource(R.string.feature_overlay_quickmenu_step_unavailable_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        val leaveDescription = stringResource(R.string.feature_overlay_quickmenu_step_unavailable_leave_label)
        Button(
            onClick = onLeave,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = leaveDescription }
                .testTag(QUICK_MENU_STEP_UNAVAILABLE_LEAVE_TEST_TAG),
        ) {
            Text(
                stringResource(R.string.feature_overlay_quickmenu_step_unavailable_leave_label),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

const val QUICK_MENU_STEP_UNAVAILABLE_TITLE_TEST_TAG = "quick_menu_step_unavailable_title"
const val QUICK_MENU_STEP_UNAVAILABLE_LEAVE_TEST_TAG = "quick_menu_step_unavailable_leave"

private const val CONTENT_PADDING_DP = 16
private const val FIELD_SPACING_DP = 12
