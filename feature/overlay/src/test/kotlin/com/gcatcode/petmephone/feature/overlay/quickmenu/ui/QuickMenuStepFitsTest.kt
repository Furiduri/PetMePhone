package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.draft.DraftKind
import com.gcatcode.petmephone.core.domain.habit.DaySegment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Nothing in an authoring step may be unreachable, at any height.
 *
 * Written the way [QuickMenuCardFitsTest] is, and for the same reason its doc already gave: the
 * window is `WRAP_CONTENT`, and a fixed height had been guessed wrong twice before a third guess
 * here pinned the steps to a 120dp *floor* value and laid the action row off the bottom on a real
 * device. No step declares a height any more, so the thing worth asserting is not a number — it is
 * that the content sizes itself when free and scrolls when squeezed.
 *
 * The earlier version of this file read a shipped height constant and asserted the segment labels
 * were "displayed". Both were dead ends. The constant is gone, and `assertIsDisplayed` cannot see a
 * label ellipsised down to a stub — the semantics carry the full string either way. That assertion
 * passed while the labels were unreadable on device, so it has been removed rather than left to
 * look like cover.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickMenuStepFitsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setStep(
        stepIndex: Int,
        segment: DaySegment? = null,
        value: String = "",
        modifier: Modifier = Modifier,
    ) {
        composeRule.setContent {
            Box(modifier = modifier) {
                QuickMenuStepFormContent(
                    state = StepFormUiState(
                        kind = DraftKind.HABIT,
                        value = value,
                        segment = segment,
                        maxLength = 200,
                    ),
                    stepIndex = stepIndex,
                    fallbackMinHeightDp = 120,
                    onValueChange = {},
                    onSegmentSelected = {},
                    onAdvance = {},
                    onCancel = {},
                    onHelp = {},
                    onFocusChanged = {},
                    onRecover = {},
                )
            }
        }
    }

    private fun assertEveryActionIsReachable() {
        // performScrollTo cannot bring a node into view without a scrollable ancestor, and
        // assertIsDisplayed then fails — so this distinguishes "scrolls" from "clipped", which is
        // the distinction that shipped broken.
        composeRule.onNodeWithTag(QUICK_MENU_STEP_CANCEL_TEST_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(QUICK_MENU_STEP_HELP_TEST_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(QUICK_MENU_STEP_ADVANCE_TEST_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a text step shows everything when free to size itself`() {
        setStep(stepIndex = 0)

        composeRule.onNodeWithTag(QUICK_MENU_STEP_PROGRESS_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(QUICK_MENU_STEP_FIELD_TEST_TAG).assertIsDisplayed()
        assertEveryActionIsReachable()
    }

    @Test
    fun `the cue step shows every choice when free to size itself`() {
        setStep(stepIndex = 2)

        composeRule.onNodeWithTag(QUICK_MENU_ANCHOR_MORNING_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(QUICK_MENU_ANCHOR_AFTERNOON_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(QUICK_MENU_ANCHOR_EVENING_TEST_TAG).assertIsDisplayed()
        assertEveryActionIsReachable()
    }

    @Test
    fun `a squeezed text step scrolls rather than clipping its actions`() {
        // Far shorter than the content needs — what a small screen or a large font scale produces,
        // and the exact condition that shipped broken.
        setStep(stepIndex = 0, modifier = Modifier.heightIn(max = 120.dp))

        assertEveryActionIsReachable()
    }

    @Test
    fun `a squeezed cue step scrolls rather than clipping its actions`() {
        setStep(stepIndex = 2, modifier = Modifier.heightIn(max = 120.dp))

        assertEveryActionIsReachable()
    }

    @Test
    fun `a squeezed cue step can still reach every choice`() {
        setStep(stepIndex = 2, modifier = Modifier.heightIn(max = 120.dp))

        composeRule.onNodeWithTag(QUICK_MENU_ANCHOR_EVENING_TEST_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the cue choices are stacked, so no label has to share the card's width`() {
        // The layout claim, asserted through geometry rather than through text: three choices across
        // a 280dp card left roughly 53dp of text room each and ellipsised into stubs on device.
        // Same left edge and increasing top edges is what "stacked" means, and it is something a
        // test can actually see.
        setStep(stepIndex = 2)

        val morning = composeRule.onNodeWithTag(QUICK_MENU_ANCHOR_MORNING_TEST_TAG).fetchSemanticsNode()
        val afternoon = composeRule.onNodeWithTag(QUICK_MENU_ANCHOR_AFTERNOON_TEST_TAG).fetchSemanticsNode()
        val evening = composeRule.onNodeWithTag(QUICK_MENU_ANCHOR_EVENING_TEST_TAG).fetchSemanticsNode()

        org.junit.Assert.assertEquals(morning.boundsInRoot.left, afternoon.boundsInRoot.left, 0.5f)
        org.junit.Assert.assertEquals(morning.boundsInRoot.left, evening.boundsInRoot.left, 0.5f)
        org.junit.Assert.assertTrue(
            "choices must stack, not sit across the card",
            morning.boundsInRoot.top < afternoon.boundsInRoot.top &&
                afternoon.boundsInRoot.top < evening.boundsInRoot.top,
        )
    }
}
