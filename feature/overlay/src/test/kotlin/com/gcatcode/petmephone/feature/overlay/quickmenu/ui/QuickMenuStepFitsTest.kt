package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.gcatcode.petmephone.core.domain.draft.DraftKind
import com.gcatcode.petmephone.core.domain.habit.DaySegment
import com.gcatcode.petmephone.feature.overlay.quickmenu.QuickMenuConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every control of every authoring step must be **reachable at the height the app actually ships**.
 *
 * This is the guard for a defect that reached a real device. The step form was pinned to
 * `inputContentMinHeightDp`, a 120dp value whose name says FLOOR: the old single-field content used
 * it as a minimum and grew past it, while the step form treated it as exact. Roughly 180dp of
 * content laid out inside 88dp of usable space, the action row fell past the bottom edge, and the
 * card appeared to have no buttons at all.
 *
 * `QuickMenuStepContentTest` did not catch it, and the reason is the lesson: it passes its own
 * `heightDp = 220`, a height where everything fits. It proves the height stays FIXED; it never
 * proved the content FITS. A test that picks its own inputs cannot fail on the shipped ones.
 *
 * So every case here reads [QuickMenuConfig.DEFAULT_STEP_CONTENT_HEIGHT_DP]. Lower that constant
 * below what the tallest step needs and these fail — which is the whole point.
 *
 * Same family as [QuickMenuCardFitsTest], whose own doc already recorded that guessing a fixed
 * height is how this class of defect happens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickMenuStepFitsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val shippedHeight = QuickMenuConfig.DEFAULT_STEP_CONTENT_HEIGHT_DP

    private fun setStep(stepIndex: Int, segment: DaySegment? = null, value: String = "") {
        composeRule.setContent {
            Box {
                QuickMenuStepFormContent(
                    state = StepFormUiState(
                        kind = DraftKind.HABIT,
                        value = value,
                        segment = segment,
                        maxLength = 200,
                    ),
                    stepIndex = stepIndex,
                    heightDp = shippedHeight,
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
        // Displayed, not merely present: a node laid out past the bottom edge still exists in the
        // tree, which is exactly how the shipped defect passed every structural assertion.
        composeRule.onNodeWithTag(QUICK_MENU_STEP_PROGRESS_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(QUICK_MENU_STEP_CANCEL_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(QUICK_MENU_STEP_HELP_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(QUICK_MENU_STEP_ADVANCE_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `the behavior step fits at the shipped height`() {
        setStep(stepIndex = 0)

        composeRule.onNodeWithTag(QUICK_MENU_STEP_FIELD_TEST_TAG).assertIsDisplayed()
        assertEveryActionIsReachable()
    }

    @Test
    fun `the minimum step fits at the shipped height`() {
        setStep(stepIndex = 1)

        composeRule.onNodeWithTag(QUICK_MENU_STEP_FIELD_TEST_TAG).assertIsDisplayed()
        assertEveryActionIsReachable()
    }

    @Test
    fun `the cue step, the tallest one, fits at the shipped height`() {
        // It carries a prompt and a row of segment choices on top of what a text step has, so it is
        // the step the height has to be sized by.
        setStep(stepIndex = 2)

        composeRule.onNodeWithTag(QUICK_MENU_ANCHOR_MORNING_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(QUICK_MENU_ANCHOR_AFTERNOON_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(QUICK_MENU_ANCHOR_EVENING_TEST_TAG).assertIsDisplayed()
        assertEveryActionIsReachable()
    }

    @Test
    fun `a long typed value does not push the actions off the card`() {
        // The field is single-line, so text must never grow the column. If it ever does, the action
        // row is the first thing to fall off the bottom.
        setStep(stepIndex = 0, value = "a".repeat(200))

        assertEveryActionIsReachable()
    }

    @Test
    fun `a chosen cue does not change what fits`() {
        // The selected choice renders as a filled Button and the others as outlined; if those ever
        // differ in height, the row grows and the actions below it get clipped.
        setStep(stepIndex = 2, segment = DaySegment.EVENING)

        assertEveryActionIsReachable()
    }
}
