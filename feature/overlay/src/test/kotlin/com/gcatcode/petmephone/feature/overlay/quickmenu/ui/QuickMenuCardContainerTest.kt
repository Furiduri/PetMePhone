package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.gcatcode.petmephone.core.domain.metric.MetricReading
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `quick-menu-text-input`'s "single-window container" requirement (tasks 4.2–4.3): the container
 * shows exactly one of its three contents at a time, activating the add-task control swaps to the
 * input content, the help control swaps to the instructions content, and each leave control
 * unwinds one level — all in place, with no second window opened (that
 * guarantee is structural here: this whole test never touches `WindowManager` at all, only the
 * container's own recomposition).
 *
 * Also carries `overlay-quick-menu`'s "a text field renders in the card's task-input content"
 * scenario — the inversion of the retired `QuickMenuCardNoTextFieldTest`, now that the container
 * hosts a real field.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickMenuCardContainerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(content: QuickMenuContent, onContentChange: (QuickMenuContent) -> Unit) {
        composeRule.setContent {
            QuickMenuCard(
                content = content,
                hunger = MetricReading.Available(percent = 42),
                happiness = MetricReading.Unavailable,
                energy = MetricReading.Unavailable,
                taskTitleMaxLength = 140,
                inputContentMinHeightDp = 120,
                onLaunchApp = {},
                onContentChange = onContentChange,
                onSubmitTask = {},
                onBack = {},
                onFieldFocusChanged = {},
            )
        }
    }

    @Test
    fun `the dashboard content shows exactly one content, no text field present`() {
        setContent(QuickMenuContent.Dashboard) {}

        composeRule.onAllNodes(hasSetTextAction()).apply {
            assertEquals(0, fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `the task-input content shows an editable text field`() {
        setContent(QuickMenuContent.TaskInput) {}

        // overlay-quick-menu: "a text field renders in the card's task-input content".
        composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG).assertExists()
        composeRule.onAllNodes(hasSetTextAction()).apply {
            assertTrue(fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun `activating the add-task control requests a swap to the task-input content`() {
        var requested: QuickMenuContent? = null
        setContent(QuickMenuContent.Dashboard) { requested = it }

        composeRule.onNodeWithTag(QUICK_MENU_ADD_TASK_TEST_TAG).performClick()

        assertEquals(QuickMenuContent.TaskInput, requested)
    }

    @Test
    fun `leaving the task-input content requests a swap back to the dashboard`() {
        var requested: QuickMenuContent? = null
        setContent(QuickMenuContent.TaskInput) { requested = it }

        composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_LEAVE_TEST_TAG).performClick()

        assertEquals(QuickMenuContent.Dashboard, requested)
    }

    @Test
    fun `activating the help control requests a swap to the instructions content`() {
        var requested: QuickMenuContent? = null
        setContent(QuickMenuContent.TaskInput) { requested = it }

        composeRule.onNodeWithTag(QUICK_MENU_TASK_INPUT_HELP_TEST_TAG).performClick()

        assertEquals(QuickMenuContent.Instructions, requested)
    }

    @Test
    fun `leaving the instructions content requests a swap back to the task input`() {
        var requested: QuickMenuContent? = null
        setContent(QuickMenuContent.Instructions) { requested = it }

        composeRule.onNodeWithTag(QUICK_MENU_INSTRUCTIONS_LEAVE_TEST_TAG).performClick()

        assertEquals(QuickMenuContent.TaskInput, requested)
    }

    @Test
    fun `exactly one of the three contents is shown at a time`() {
        // Each content is identified by a control only it renders. Driven through ONE composition
        // whose `content` argument changes, so this asserts the container really swaps in place —
        // three independent trees each rendering one content would prove nothing about swapping.
        var current by mutableStateOf<QuickMenuContent>(QuickMenuContent.Dashboard)
        composeRule.setContent {
            QuickMenuCard(
                content = current,
                hunger = MetricReading.Available(percent = 42),
                happiness = MetricReading.Unavailable,
                energy = MetricReading.Unavailable,
                taskTitleMaxLength = 140,
                inputContentMinHeightDp = 120,
                onLaunchApp = {},
                onContentChange = {},
                onSubmitTask = {},
                onBack = {},
                onFieldFocusChanged = {},
            )
        }

        val markers = listOf(
            QuickMenuContent.Dashboard to QUICK_MENU_ADD_TASK_TEST_TAG,
            QuickMenuContent.TaskInput to QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG,
            QuickMenuContent.Instructions to QUICK_MENU_INSTRUCTIONS_LEAVE_TEST_TAG,
        )

        markers.forEach { (shown, _) ->
            composeRule.runOnIdle { current = shown }

            markers.forEach { (content, tag) ->
                if (content == shown) {
                    composeRule.onNodeWithTag(tag).assertExists()
                } else {
                    composeRule.onNodeWithTag(tag).assertDoesNotExist()
                }
            }
        }
    }
}
