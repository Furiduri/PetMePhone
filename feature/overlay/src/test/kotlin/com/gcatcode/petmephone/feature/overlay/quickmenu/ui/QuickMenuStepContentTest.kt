package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** #100's step: one input, visible progress, a fixed height, and a hoisted value. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickMenuStepContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        stepNumber: Int = 1,
        stepCount: Int = 3,
        value: String = "",
        maxLength: Int = 10,
        onNext: (() -> Unit)? = {},
        onSubmit: (() -> Unit)? = null,
        onValueChange: (String) -> Unit = {},
        onCancel: () -> Unit = {},
        onHelp: () -> Unit = {},
        onFocusChanged: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            Box {
                QuickMenuStepContent(
                    stepNumber = stepNumber,
                    stepCount = stepCount,
                    label = "Behavior",
                    placeholder = "What will you do?",
                    value = value,
                    maxLength = maxLength,
                    onNext = onNext,
                    onSubmit = onSubmit,
                    onValueChange = onValueChange,
                    onCancel = onCancel,
                    onHelp = onHelp,
                    onFocusChanged = onFocusChanged,
                )
            }
        }
    }

    @Test
    fun `the step shows where the user is in the flow`() {
        // One input per card with no sense of how many remain reads as endless, and endless is
        // abandoned.
        setContent(stepNumber = 2, stepCount = 3)

        composeRule.onNodeWithText("Step 2 of 3").assertExists()
    }

    @Test
    fun `exactly one text field exists on a step`() {
        // #82 measured that IME insets never reach an overlay window, so a second stacked field
        // would sit under a keyboard whose height the app cannot discover.
        setContent()

        composeRule.onAllNodesWithTagCount(QUICK_MENU_STEP_FIELD_TEST_TAG).let { count ->
            assertEquals("a step must have exactly one input", 1, count)
        }
    }

    @Test
    fun `typing reports upward instead of being kept here`() {
        // The value is hoisted precisely so the caller can persist it: a draft living in the
        // composition dies to a service restart.
        var reported: String? = null
        setContent(onValueChange = { reported = it })

        composeRule.onNodeWithTag(QUICK_MENU_STEP_FIELD_TEST_TAG).performTextInput("Read")

        assertEquals("Read", reported)
    }

    @Test
    fun `characters are not lost or reordered when the persisted value lags`() {
        // The defect, reported from a device: typing "Hola" produced "olaH".
        //
        // The hoisted value round-trips through Room, so between a keystroke and its echo the
        // field is still showing the previous value — and a String-valued text field carries no
        // selection, so an out-of-band value drops the cursor at index 0 and the next character
        // lands in front. Here the hoisted value never updates at all, which is that lag taken to
        // its limit.
        var reported = ""
        setContent(value = "", onValueChange = { reported = it })

        composeRule.onNodeWithTag(QUICK_MENU_STEP_FIELD_TEST_TAG).performTextInput("H")
        composeRule.onNodeWithTag(QUICK_MENU_STEP_FIELD_TEST_TAG).performTextInput("o")

        assertEquals("typing must append, whatever the persisted value is doing", "Ho", reported)
    }

    @Test
    fun `an external value change is adopted, so resuming a draft shows what was typed`() {
        // The other half: the local buffer must not ignore the draft. Reopening a saved draft has
        // to put the saved text back in the field.
        setContent(value = "Read one page")

        composeRule.onNodeWithTag(QUICK_MENU_STEP_FIELD_TEST_TAG)
            .assertTextContains("Read one page")
    }

    @Test
    fun `input past the cap is refused rather than truncated silently`() {
        var reported: String? = null
        setContent(value = "0123456789", maxLength = 10, onValueChange = { reported = it })

        composeRule.onNodeWithTag(QUICK_MENU_STEP_FIELD_TEST_TAG).performTextInput("X")

        assertEquals("an over-cap edit must not reach the caller", null, reported)
    }

    @Test
    fun `a non-final step advances with Next and its keyboard action matches`() {
        var advanced = false
        setContent(onNext = { advanced = true }, onSubmit = null)

        composeRule.onNodeWithText("Next").assertExists()
        composeRule.onNodeWithTag(QUICK_MENU_STEP_ADVANCE_TEST_TAG).performClick()

        assertTrue(advanced)
        assertEquals(ImeAction.Next, imeActionOfField())
    }

    @Test
    fun `the final step submits instead of advancing, and says so`() {
        var submitted = false
        setContent(onNext = null, onSubmit = { submitted = true })

        composeRule.onNodeWithText("Done").assertExists()
        composeRule.onNodeWithTag(QUICK_MENU_STEP_ADVANCE_TEST_TAG).performClick()

        assertTrue(submitted)
        assertEquals(ImeAction.Done, imeActionOfField())
    }

    @Test
    fun `Cancel is present on every step, because it is the only way to abandon a draft`() {
        // Once outside tap stops discarding, this is the only way to say "not this one" — without
        // it a draft is unabandonable.
        var cancelled = false
        setContent(onCancel = { cancelled = true })

        composeRule.onNodeWithTag(QUICK_MENU_STEP_CANCEL_TEST_TAG).performClick()

        assertTrue(cancelled)
    }

    @Test
    fun `help swaps content rather than opening a second window`() {
        var helped = false
        setContent(onHelp = { helped = true })

        composeRule.onNodeWithTag(QUICK_MENU_STEP_HELP_TEST_TAG).performClick()

        assertTrue(helped)
    }

    @Test
    fun `the field is not focused until it is tapped`() {
        // Auto-focus would raise the keyboard on a surface where the app is a guest.
        var focused = false
        setContent(onFocusChanged = { focused = it })

        assertFalse("no auto-focus on first composition", focused)
    }

    private fun imeActionOfField(): ImeAction? =
        composeRule.onNodeWithTag(QUICK_MENU_STEP_FIELD_TEST_TAG)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ImeAction)

    private fun ComposeContentTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodesWithTag(tag).fetchSemanticsNodes().size
}
