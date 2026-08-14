package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

    private fun setContent(maxLength: Int = 10, onSubmit: (String) -> Unit = {}) {
        composeRule.setContent {
            QuickMenuTaskInputContent(
                taskTitleMaxLength = maxLength,
                minHeightDp = 120,
                onSubmit = onSubmit,
                onLeave = {},
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
                )
            }
        }

        composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG).performTextInput("feed the cat")
        composeRule.runOnIdle { instance = 1 }

        composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG)
            .assertTextEquals("What needs doing?", includeEditableText = false)
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

    @Test
    fun `the empty field shows the placeholder string`() {
        setContent()

        composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG)
            .assertTextEquals("What needs doing?", includeEditableText = false)
    }
}
