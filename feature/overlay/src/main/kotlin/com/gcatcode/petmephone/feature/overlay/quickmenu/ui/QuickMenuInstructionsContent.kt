package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
 * The instructions content (`quick-menu-text-input` spec): plain text explaining what the
 * task-input field currently does, plus one control returning to that field.
 *
 * It is a **content of the same card container**, not a dialog and not a second
 * `WindowManager` window — reaching it is an in-place swap exactly like the dashboard/task-input
 * swap, and back from here unwinds one level to the task input (`resolveBack`'s `Instructions`
 * case). The copy is deliberately honest that submission creates nothing yet; #100 owns that.
 *
 * The text scrolls rather than clipping, since the card's height is bounded by
 * [com.gcatcode.petmephone.feature.overlay.quickmenu.QuickMenuConfig.maxCardHeightDp] and a large
 * font scale must not make the return control unreachable.
 */
@Composable
fun QuickMenuInstructionsContent(
    minHeightDp: Int,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeightDp.dp)
            .verticalScroll(rememberScrollState())
            .padding(CONTENT_PADDING_DP.dp),
        verticalArrangement = Arrangement.spacedBy(BLOCK_SPACING_DP.dp),
    ) {
        Text(
            stringResource(R.string.feature_overlay_quickmenu_instructions_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.feature_overlay_quickmenu_instructions_body),
            style = MaterialTheme.typography.bodyMedium,
        )

        // Short visible label, longer content description — the same split the task-input row
        // uses, for the same reason: a description painted as a label distorts the layout.
        val leaveLabel = stringResource(R.string.feature_overlay_quickmenu_instructions_leave_label)
        val leaveDescription =
            stringResource(R.string.feature_overlay_quickmenu_instructions_leave_description)
        OutlinedButton(
            onClick = onLeave,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = leaveDescription }
                .testTag(QUICK_MENU_INSTRUCTIONS_LEAVE_TEST_TAG),
        ) {
            Text(leaveLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

const val QUICK_MENU_INSTRUCTIONS_LEAVE_TEST_TAG = "quick_menu_instructions_leave"

private const val CONTENT_PADDING_DP = 16
private const val BLOCK_SPACING_DP = 12
