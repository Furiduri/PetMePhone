package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.metric.MetricReading
import com.gcatcode.petmephone.feature.overlay.R

/**
 * The quick-menu card's dashboard content: three [MetricRow]s and the launch button, unchanged in
 * appearance from before the container split (#18 Phase 4). The add-task control, previously
 * disabled, is now the enabled trigger that swaps the container to the task-input content
 * (`quick-menu-text-input`'s "single-window container" requirement) — it is no longer a
 * placeholder for a feature that does not exist yet.
 */
@Composable
fun QuickMenuDashboardContent(
    hunger: MetricReading,
    happiness: MetricReading,
    energy: MetricReading,
    onLaunchApp: () -> Unit,
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(CARD_PADDING_DP.dp),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING_DP.dp),
    ) {
        val addTaskDescription =
            stringResource(R.string.feature_overlay_quickmenu_add_task_description)
        MetricRow(
            label = stringResource(R.string.feature_overlay_quickmenu_metric_hunger),
            reading = hunger,
            trailing = {
                OutlinedButton(
                    onClick = onAddTask,
                    contentPadding = PaddingValues(
                        horizontal = ADD_TASK_BUTTON_HORIZONTAL_PADDING_DP.dp,
                        vertical = 0.dp,
                    ),
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = addTaskDescription }
                        .testTag(QUICK_MENU_ADD_TASK_TEST_TAG),
                ) {
                    Text(
                        stringResource(R.string.feature_overlay_quickmenu_add_task_button),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
        )
        MetricRow(stringResource(R.string.feature_overlay_quickmenu_metric_happiness), happiness)
        MetricRow(stringResource(R.string.feature_overlay_quickmenu_metric_energy), energy)

        val launchDescription = stringResource(R.string.feature_overlay_quickmenu_launch_button_description)
        Button(
            onClick = onLaunchApp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .widthIn(min = 48.dp)
                .semantics { contentDescription = launchDescription }
                .testTag(QUICK_MENU_LAUNCH_BUTTON_TEST_TAG),
        ) {
            Text(stringResource(R.string.feature_overlay_quickmenu_launch_button))
        }
    }
}

const val QUICK_MENU_ADD_TASK_TEST_TAG = "quick_menu_add_task"
const val QUICK_MENU_LAUNCH_BUTTON_TEST_TAG = "quick_menu_launch_button"

private const val CARD_PADDING_DP = 16
private const val ROW_SPACING_DP = 12
private const val ADD_TASK_BUTTON_HORIZONTAL_PADDING_DP = 12
