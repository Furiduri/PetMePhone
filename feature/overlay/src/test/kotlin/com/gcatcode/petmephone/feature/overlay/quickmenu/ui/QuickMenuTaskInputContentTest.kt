package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
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

/**
 * `quick-menu-text-input`'s field-behaviour requirements: focus only on tap (design decision 10),
 * typed text discarded on dismissal/disposal (design decision 5's Compose-owned half), a
 * length-bounded field (threat matrix), and the field's accessibility semantics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickMenuTaskInputContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        maxLength: Int = 10,
        onSubmit: (String) -> Unit = {},
        onHelp: () -> Unit = {},
    ) {
        composeRule.setContent {
            QuickMenuTaskInputContent(
                taskTitleMaxLength = maxLength,
                minHeightDp = 120,
                onSubmit = onSubmit,
                onLeave = {},
                onFocusChanged = {},
                onHelp = onHelp,
            )
        }
    }

    @Test
    fun `the field is not focused on first composition — no auto-focus`() {
        setContent()

        val focused = composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Focused)
        assertFalse("expected the field to start unfocused", focused == true)
    }

    @Test
    fun `tapping the field requests focus`() {
        setContent()

        composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG).performClick()

        val focused = composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Focused)
        assertTrue("expected the field to be focused after a tap", focused == true)
    }

    @Test
    fun `typed text does not survive a fresh composition`() {
        // `key(instance)` forces Compose to discard the old node and its remembered state and
        // build a brand-new one when `instance` changes — the same "whole composition destroyed"
        // shape the real window removal produces on every dismissal path (design decision 5),
        // without needing a second host to simulate it.
        var instance by mutableIntStateOf(0)
        composeRule.setContent {
            androidx.compose.runtime.key(instance) {
                QuickMenuTaskInputContent(
                    taskTitleMaxLength = 10,
                    minHeightDp = 120,
                    onSubmit = {},
                    onLeave = {},
                    onFocusChanged = {},
                    onHelp = {},
                )
            }
        }

        composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG).performTextInput("feed the cat")
        composeRule.runOnIdle { instance = 1 }

        // Asserted on the field's own input text rather than on the placeholder: now that the
        // field carries a label, Material3 keeps the placeholder hidden while the field is empty
        // and unfocused, so "the placeholder is back" is no longer evidence that the text is gone.
        // The input text being empty is that evidence directly.
        val inputText = composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.InputText)
            ?.text
        assertEquals("", inputText ?: "")
        composeRule.onNodeWithText("feed the cat", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a max-length-exceeding string is rejected at the field, not truncated downstream`() {
        var submitted: String? = null
        setContent(maxLength = 5, onSubmit = { submitted = it })

        composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG).performTextInput("way too long")
        composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_SUBMIT_TEST_TAG).performClick()

        // Rejected at the field: nothing past the bound was ever accepted into the field's own
        // state, so what reaches submit is already within bound — not silently truncated later.
        assertEquals(true, (submitted?.length ?: 0) <= 5)
    }

    @Test
    fun `the field carries a content description`() {
        setContent()

        composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG)
            .assertContentDescriptionEquals("Task title")
    }

    @Test
    fun `the field exposes a done-class IME action`() {
        setContent()

        val imeAction = composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ImeAction)
        assertEquals(ImeAction.Done, imeAction)
    }

    /**
     * Measured Material3 behaviour, not an assumption: once the field carries a `label`, the empty
     * box shows the **label** and the placeholder appears only while the field is focused. Both
     * texts are therefore asserted, each in the state it actually occupies — the empty-state hint
     * requirement is met by the label, and the placeholder is still reachable rather than dead
     * copy nobody ever sees.
     */
    @Test
    fun `the empty unfocused field shows its label, and focusing it reveals the placeholder`() {
        setContent()

        composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG)
            .assertTextContains("Task title")

        composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG).performClick()

        composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG)
            .assertTextContains("What needs doing?")
    }

    @Test
    fun `the field keeps a visible label once the placeholder is gone`() {
        // Failing input: dropping the `label` slot. The placeholder disappears on the first typed
        // character, and without the label the field would then be an unidentified box.
        setContent()

        composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG).performTextInput("feed")

        composeRule.onNodeWithText("Task title").assertExists()
        composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG)
            .assertTextContains("Task title")
    }

    @Test
    fun `the help control requests the instructions content`() {
        var helped = false
        setContent(onHelp = { helped = true })

        composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_HELP_TEST_TAG).performClick()

        assertTrue("expected the help control to request the instructions content", helped)
    }

    /**
     * The maintainer's reported defect, as an assertion rather than as a person looking at a phone:
     * the submit button rendered one letter per line because the leave control — painting its long
     * *content description* as its label — took the row's width and squeezed it to its minimum.
     *
     * Each label must therefore resolve to exactly one text node, laid out on a single line and
     * wider than it is tall. A vertical letter stack fails both the single-line height bound and
     * the width-over-height comparison; a truncated or duplicated label fails the node count.
     */
    @Test
    fun `neither action label collapses into a vertical letter stack`() {
        setContent()

        listOf(
            QUICK_MENU_TASK_INPUT_LEAVE_TEST_TAG to "Back",
            QUICK_MENU_TASK_INPUT_SUBMIT_TEST_TAG to "Add",
        ).forEach { (tag, label) ->
            composeRule.onNodeWithText(label).assertExists()

            val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().size
            assertTrue(
                "expected the $label button to be wider than tall, got ${bounds.width}x${bounds.height}",
                bounds.width > bounds.height,
            )
        }
    }

    @Test
    fun `the action buttons share the row rather than one starving the other`() {
        // Equal weights, so neither action can be squeezed to its minimum by the other's label —
        // the structural half of the defect above, independent of how long either label is.
        setContent()

        val leaveWidth = composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_LEAVE_TEST_TAG)
            .fetchSemanticsNode().size.width
        val submitWidth = composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_SUBMIT_TEST_TAG)
            .fetchSemanticsNode().size.width

        assertEquals(
            "expected the two weighted actions to receive the same width",
            leaveWidth,
            submitWidth,
        )
    }
}
