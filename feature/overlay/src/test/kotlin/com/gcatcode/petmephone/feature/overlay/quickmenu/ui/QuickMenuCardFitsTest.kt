package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.metric.MetricReading
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Nothing in the card may be unreachable, at any height.
 *
 * This is the guard for a defect that actually shipped: the card window had a fixed 180dp height,
 * the launch button laid out past its bottom edge, and on device the card appeared to have no
 * button at all while the bottom metric row absorbed the taps meant for it.
 *
 * `QuickMenuCardAccessibilityTest` did not catch it — it asserts the button is the only enabled
 * clickable node, which stayed true while the button was off-screen. Structure was verified;
 * reachability was not.
 *
 * The fix was to stop guessing a height: the window now wraps its content, and the content scrolls
 * if it ever exceeds the ceiling. Both halves of that are asserted below, because a wrapping window
 * that silently clipped, or a scroll container that could not actually be scrolled, would each
 * reintroduce the same class of failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickMenuCardFitsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `every control is displayed when the card is free to size itself to its content`() {
        composeRule.setContent { CardUnderTest(Modifier) }

        // Fails if the card clips its own content when unconstrained — the wrap-content promise.
        composeRule.onNodeWithTag(QUICK_MENU_CARD_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(QUICK_MENU_ADD_TASK_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(QUICK_MENU_LAUNCH_BUTTON_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `the launch button is still reachable when the card is squeezed below its content height`() {
        // Deliberately far shorter than the content needs — the exact condition that shipped broken,
        // and what a small screen or a large font scale produces in practice.
        composeRule.setContent { CardUnderTest(Modifier.heightIn(max = 120.dp)) }

        // Fails if the content is clipped rather than scrollable: performScrollTo cannot bring a
        // node into view when there is no scrollable ancestor, and assertIsDisplayed then fails.
        composeRule.onNodeWithTag(QUICK_MENU_LAUNCH_BUTTON_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @androidx.compose.runtime.Composable
    private fun CardUnderTest(modifier: Modifier) {
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
            onFieldFocusChanged = {},
            modifier = modifier,
        )
    }
}
