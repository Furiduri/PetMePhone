package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.metric.MetricReading
import com.gcatcode.petmephone.feature.overlay.R
import com.gcatcode.petmephone.feature.overlay.ui.PetOverlayStateHolder

/**
 * The quick-menu card's real Compose content (design.md's PR 6). Three [MetricRow]s and one
 * launch button — no text field of any kind (`overlay-quick-menu`'s "no text field renders in the
 * card" scenario, and #18's spike, not this file, decides whether one is ever added).
 *
 * No full-bounds click target exists on this composition: [Surface] here carries no
 * `clickable`/`onClick` of its own, so the only clickable node in the whole tree is the launch
 * [Button] — verified by `QuickMenuCardAccessibilityTest`. `ACTION_OUTSIDE` dismissal is wired by
 * [com.gcatcode.petmephone.feature.overlay.quickmenu.QuickMenuWindowController] on the *host
 * View*, entirely outside this Compose tree, so it never needs a scrim here at all.
 */
@Composable
fun QuickMenuCard(
    hunger: MetricReading,
    happiness: MetricReading,
    energy: MetricReading,
    onLaunchApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag(QUICK_MENU_CARD_TEST_TAG),
        shape = RoundedCornerShape(CARD_CORNER_RADIUS_DP.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = CARD_ELEVATION_DP.dp,
    ) {
        Column(
            modifier = Modifier
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
                    // Visible now, deliberately inert: creating a task needs the text field from
                    // #18 and the submit wiring from #27, neither of which is in this change.
                    // Disabled rather than enabled-and-silent, so tapping it cannot look like a
                    // failure — the control says plainly that it does not work yet.
                    // A labelled button, not a bare glyph. As an unlabelled "+" it read as
                    // decoration rather than as a control, so nobody would find it. Still
                    // disabled: creating a task needs #18's text field and #27's submit wiring.
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        contentPadding = PaddingValues(
                            horizontal = FEED_BUTTON_HORIZONTAL_PADDING_DP.dp,
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
}

/**
 * Collects [PetOverlayStateHolder.hunger] (the only reactive metric, design decision 3) and
 * passes the plain `happiness`/`energy` vals straight through. The sole seam between the service's
 * injected state holder and the pure, `@Preview`-friendly [QuickMenuCard] above.
 */
@Composable
fun QuickMenuCardRoute(
    stateHolder: PetOverlayStateHolder,
    onLaunchApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hunger by stateHolder.hunger.collectAsState()
    QuickMenuCard(
        hunger = hunger,
        happiness = stateHolder.happiness,
        energy = stateHolder.energy,
        onLaunchApp = onLaunchApp,
        modifier = modifier,
    )
}

const val QUICK_MENU_CARD_TEST_TAG = "quick_menu_card"
const val QUICK_MENU_LAUNCH_BUTTON_TEST_TAG = "quick_menu_launch_button"
const val QUICK_MENU_ADD_TASK_TEST_TAG = "quick_menu_add_task"

private const val CARD_CORNER_RADIUS_DP = 16
private const val CARD_ELEVATION_DP = 4
private const val CARD_PADDING_DP = 16
private const val ROW_SPACING_DP = 12
private const val FEED_BUTTON_HORIZONTAL_PADDING_DP = 12

@Preview(widthDp = 280, heightDp = 220)
@Composable
private fun QuickMenuCardPreview() {
    QuickMenuCard(
        hunger = MetricReading.Available(percent = 62),
        happiness = MetricReading.Unavailable,
        energy = MetricReading.Unavailable,
        onLaunchApp = {},
    )
}

@Preview(widthDp = 280, heightDp = 220, name = "Hunger loading")
@Composable
private fun QuickMenuCardLoadingPreview() {
    QuickMenuCard(
        hunger = MetricReading.Loading,
        happiness = MetricReading.Unavailable,
        energy = MetricReading.Unavailable,
        onLaunchApp = {},
    )
}
