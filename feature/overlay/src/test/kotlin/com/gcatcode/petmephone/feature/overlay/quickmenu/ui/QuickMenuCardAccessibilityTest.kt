package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.metric.MetricReading
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuContent
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `overlay-quick-menu`'s accessibility-minimums requirement and `quick-menu-text-input`'s field
 * accessibility requirement: every interactive element carries a content description, every touch
 * target is at least 48dp, and no undescribed full-bounds touchable scrim exists (issue #17's
 * sharpest named failure — losing the app underneath with no explanation). Every assertion below
 * iterates `onAllNodes(hasClickAction())` and asserts per node, never by naming a fixed list of
 * test tags, per this project's standing convention.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickMenuCardAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setDashboard() {
        composeRule.setContent {
            QuickMenuCard(
                content = QuickMenuContent.Dashboard,
                hunger = MetricReading.Available(percent = 42),
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
    }

    private fun setTaskInput() {
        composeRule.setContent {
            QuickMenuCard(
                content = QuickMenuContent.TaskInput,
                hunger = MetricReading.Available(percent = 42),
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
    }

    @Test
    fun `the launch button has a content description and a 48dp touch target`() {
        setDashboard()

        // Failing input: removing the .semantics { contentDescription = ... } modifier, or
        // shrinking the button below 48dp on either axis, fails this assertion.
        composeRule.onNodeWithTag(QUICK_MENU_LAUNCH_BUTTON_TEST_TAG)
            .assertContentDescriptionEquals("Open the full app")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    /**
     * The requirement says *every* interactive element, so this asserts over every clickable node
     * rather than over a named list of them. A control added tomorrow is covered by this without
     * anyone remembering to extend the test, which is the only version of this assertion that stays
     * true.
     */
    @Test
    fun `every clickable element on the dashboard carries a content description and a 48dp touch target`() {
        setDashboard()

        val clickableNodes = composeRule.onAllNodes(hasClickAction())
        val clickableCount = clickableNodes.fetchSemanticsNodes().size
        // A card that rendered no clickable node at all would otherwise pass an empty loop.
        check(clickableCount > 0) { "expected at least one clickable node in the card" }

        repeat(clickableCount) { index ->
            clickableNodes[index]
                .assert(hasNonBlankContentDescription)
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    fun `every clickable element on the task-input content carries a content description and a 48dp touch target`() {
        setTaskInput()

        val clickableNodes = composeRule.onAllNodes(hasClickAction())
        val clickableCount = clickableNodes.fetchSemanticsNodes().size
        check(clickableCount > 0) { "expected at least one clickable node in the task-input content" }

        repeat(clickableCount) { index ->
            clickableNodes[index]
                .assert(hasNonBlankContentDescription)
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    fun `only the launch button and the add-task control are clickable on the dashboard — no undescribed full-bounds scrim`() {
        setDashboard()

        // Two clickable nodes, both named and both inside the card: the launch button, and the
        // add-task control, now enabled as the swap trigger into the task-input content.
        //
        // Failing input: adding any clickable modifier to the card's root Surface/Column — the
        // shape an undescribed full-bounds scrim would take — raises this count to 3. That scrim is
        // the specific accessibility failure #17 singles out, because it costs TalkBack users
        // access to the app underneath with no explanation.
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(2)

        composeRule.onNodeWithTag(QUICK_MENU_ADD_TASK_TEST_TAG).assertIsEnabled()
        composeRule.onNodeWithTag(QUICK_MENU_LAUNCH_BUTTON_TEST_TAG).assertIsEnabled()
    }

    @Test
    fun `the card's root node itself carries no click action`() {
        setDashboard()

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

/**
 * Non-blank rather than merely present: a node carrying `contentDescription = ""` satisfies
 * TalkBack's API and tells the user nothing, so an empty string must not pass for a description.
 */
private val hasNonBlankContentDescription = SemanticsMatcher("has a non-blank content description") { node ->
    node.config.getOrNull(SemanticsProperties.ContentDescription)
        ?.any { description -> description.isNotBlank() } == true
}
