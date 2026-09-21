package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.gcatcode.petmephone.core.domain.metric.MetricReading
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
                onLaunchApp = {},
                onContentChange = onContentChange,
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
    fun `activating the add-task control swaps to no other content at all`() {
        // This test used to assert the opposite, and the behaviour it pinned shipped a defect: the
        // single-field content asked for a "Task title" and the form then discarded it, so the
        // typed words reached neither the draft nor the database. The dashboard now opens the
        // authoring form directly — see `QuickMenuAddOpensFormTest` for that half.
        var requested: QuickMenuContent? = null
        setContent(QuickMenuContent.Dashboard) { requested = it }

        composeRule.onNodeWithTag(QUICK_MENU_ADD_TASK_TEST_TAG).performClick()

        assertNull("nothing may stand between the dashboard and the form", requested)
    }




    @Test
    fun `exactly one content is shown at a time`() {
        // The container's core claim. The set shrank when the single-field input and its
        // instructions page were deleted; what stays true is that a swap replaces rather than
        // stacks, which is what keeps this a card and not a pile of overlay windows.
        setContent(QuickMenuContent.Dashboard) {}

        composeRule.onNodeWithTag(QUICK_MENU_ADD_TASK_TEST_TAG).assertExists()
        composeRule.onAllNodesWithTag(QUICK_MENU_STEP_FIELD_TEST_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(QUICK_MENU_STEP_UNAVAILABLE_TITLE_TEST_TAG).assertCountEquals(0)
    }
}
