package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.metric.MetricReading
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuContent
import com.gcatcode.petmephone.feature.overlay.ui.PetOverlayStateHolder

/**
 * The quick-menu card's container (#18 Phase 4, `quick-menu-text-input`'s "single-window
 * container" requirement). Shows exactly one of [QuickMenuContent]'s two cases at a time, swapped
 * in place — no new `WindowManager` window is ever opened by a swap.
 *
 * Hosts the package's one and only `BackHandler`, calling [onBack]. `QuickMenuBackWiringCodeTest`
 * enforces that count structurally (design decision 8). This container reads no keyboard or
 * window-inset signal to decide which content is shown — the shown content is a pure function of
 * [content], which the caller derives entirely from explicit user actions
 * ([QuickMenuWindowController.onContentChange] and [QuickMenuWindowController]'s `resolveBack`
 * application); see `design.md`'s measured facts 3–5 for why no such signal is dependable on this
 * window class.
 *
 * `ACTION_OUTSIDE` dismissal is wired by
 * [com.gcatcode.petmephone.feature.overlay.quickmenu.QuickMenuWindowController] on the *host View*,
 * entirely outside this Compose tree, so no scrim exists here.
 */
@Composable
fun QuickMenuCard(
    content: QuickMenuContent,
    hunger: MetricReading,
    happiness: MetricReading,
    energy: MetricReading,
    taskTitleMaxLength: Int,
    inputContentMinHeightDp: Int,
    onLaunchApp: () -> Unit,
    onContentChange: (QuickMenuContent) -> Unit,
    onSubmitTask: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    Surface(
        modifier = modifier.testTag(QUICK_MENU_CARD_TEST_TAG),
        shape = RoundedCornerShape(CARD_CORNER_RADIUS_DP.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = CARD_ELEVATION_DP.dp,
    ) {
        when (content) {
            QuickMenuContent.Dashboard -> QuickMenuDashboardContent(
                hunger = hunger,
                happiness = happiness,
                energy = energy,
                onLaunchApp = onLaunchApp,
                onAddTask = { onContentChange(QuickMenuContent.TaskInput) },
            )

            QuickMenuContent.TaskInput -> QuickMenuTaskInputContent(
                taskTitleMaxLength = taskTitleMaxLength,
                minHeightDp = inputContentMinHeightDp,
                onSubmit = onSubmitTask,
                onLeave = { onContentChange(QuickMenuContent.Dashboard) },
            )
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
    content: QuickMenuContent,
    stateHolder: PetOverlayStateHolder,
    taskTitleMaxLength: Int,
    inputContentMinHeightDp: Int,
    onLaunchApp: () -> Unit,
    onContentChange: (QuickMenuContent) -> Unit,
    onSubmitTask: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hunger by stateHolder.hunger.collectAsState()
    QuickMenuCard(
        content = content,
        hunger = hunger,
        happiness = stateHolder.happiness,
        energy = stateHolder.energy,
        taskTitleMaxLength = taskTitleMaxLength,
        inputContentMinHeightDp = inputContentMinHeightDp,
        onLaunchApp = onLaunchApp,
        onContentChange = onContentChange,
        onSubmitTask = onSubmitTask,
        onBack = onBack,
        modifier = modifier,
    )
}

const val QUICK_MENU_CARD_TEST_TAG = "quick_menu_card"

private const val CARD_CORNER_RADIUS_DP = 16
private const val CARD_ELEVATION_DP = 4

@Preview(widthDp = 280, heightDp = 220)
@Composable
private fun QuickMenuCardPreview() {
    QuickMenuCard(
        content = QuickMenuContent.Dashboard,
        hunger = MetricReading.Available(percent = 62),
        happiness = MetricReading.Unavailable,
        energy = MetricReading.Unavailable,
        taskTitleMaxLength = 140,
        inputContentMinHeightDp = 120,
        onLaunchApp = {},
        onContentChange = {},
        onSubmitTask = {},
        onBack = {},
    )
}

@Preview(widthDp = 280, heightDp = 220, name = "Task input")
@Composable
private fun QuickMenuCardTaskInputPreview() {
    QuickMenuCard(
        content = QuickMenuContent.TaskInput,
        hunger = MetricReading.Available(percent = 62),
        happiness = MetricReading.Unavailable,
        energy = MetricReading.Unavailable,
        taskTitleMaxLength = 140,
        inputContentMinHeightDp = 120,
        onLaunchApp = {},
        onContentChange = {},
        onSubmitTask = {},
        onBack = {},
    )
}
