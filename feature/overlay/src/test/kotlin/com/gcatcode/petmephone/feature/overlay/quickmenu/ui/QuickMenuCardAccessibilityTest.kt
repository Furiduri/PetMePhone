package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.metric.MetricReading
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `overlay-quick-menu`'s accessibility-minimums requirement: every interactive element carries a
 * content description, every touch target is at least 48dp, and no undescribed full-bounds
 * touchable scrim exists (issue #17's sharpest named failure — losing the app underneath with no
 * explanation).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickMenuCardAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the launch button has a content description and a 48dp touch target`() {
        composeRule.setContent {
            QuickMenuCard(
                hunger = MetricReading.Available(percent = 42),
                happiness = MetricReading.Unavailable,
                energy = MetricReading.Unavailable,
                onLaunchApp = {},
            )
        }

        // Failing input: removing the .semantics { contentDescription = ... } modifier, or
        // shrinking the button below 48dp on either axis, fails this assertion.
        composeRule.onNodeWithTag(QUICK_MENU_LAUNCH_BUTTON_TEST_TAG)
            .assertContentDescriptionEquals("Open the full app")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun `the only clickable node in the card is the launch button — no undescribed full-bounds scrim`() {
        composeRule.setContent {
            QuickMenuCard(
                hunger = MetricReading.Available(percent = 42),
                happiness = MetricReading.Unavailable,
                energy = MetricReading.Unavailable,
                onLaunchApp = {},
            )
        }

        // Failing input: adding any clickable modifier to the card's root Surface/Column (the
        // shape a full-bounds scrim would take) raises this count to 2 and fails the assertion.
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun `the card's root node itself carries no click action`() {
        composeRule.setContent {
            QuickMenuCard(
                hunger = MetricReading.Available(percent = 42),
                happiness = MetricReading.Unavailable,
                energy = MetricReading.Unavailable,
                onLaunchApp = {},
            )
        }

        // Failing input: wrapping the whole card in a clickable Modifier (the exact shape of an
        // undescribed full-bounds scrim) makes this assertion fail.
        assertFalse(
            "expected the card's root node to have no click action",
            composeRule.onRoot().fetchSemanticsNode().config.contains(
                androidx.compose.ui.semantics.SemanticsActions.OnClick,
            ),
        )
    }
}
