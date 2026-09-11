package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
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
 * The dashboard's add control opens the authoring form, and does not put anything in front of it.
 *
 * The defect this pins, found on a device: add went to the single-field [QuickMenuTaskInputContent],
 * whose label asks for a "Task title". Submitting there started a fresh empty draft and **discarded
 * the typed title** — the field reached neither the draft nor the database, and the user landed on
 * step one with an empty box wondering where their words went.
 *
 * A screen that asks you to type and then throws it away is worse than no screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickMenuAddOpensFormTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `add opens the authoring form rather than the single-field title screen`() {
        var startedAuthoring = false
        var requestedContent: QuickMenuContent? = null

        composeRule.setContent {
            QuickMenuCard(
                content = QuickMenuContent.Dashboard,
                hunger = MetricReading.Available(percent = 42),
                happiness = MetricReading.Unavailable,
                energy = MetricReading.Unavailable,
                taskTitleMaxLength = 140,
                inputContentMinHeightDp = 120,
                onLaunchApp = {},
                onContentChange = { requestedContent = it },
                onSubmitTask = {},
                onBack = {},
                onFieldFocusChanged = {},
                onStartAuthoring = { startedAuthoring = true },
            )
        }

        composeRule.onNodeWithTag(QUICK_MENU_ADD_TASK_TEST_TAG).performClick()

        assertTrue("add must open the authoring form", startedAuthoring)
        assertNull(
            "add must not route through any other content, least of all the discarded-title screen",
            requestedContent,
        )
    }

    @Test
    fun `the add control asks for no title of its own`() {
        // Guards the regression directly: if the dashboard ever puts a text field in front of the
        // form again, whatever is typed there has nowhere to go.
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
                onFieldFocusChanged = {},
                onStartAuthoring = {},
            )
        }

        composeRule.onNodeWithTag(QUICK_MENU_ADD_TASK_TEST_TAG).performClick()

        assertEquals(
            "no text field may stand between the dashboard and the form",
            0,
            composeRule.onAllNodesWithTagCount(QUICK_MENU_TASK_INPUT_FIELD_TEST_TAG),
        )
    }

    private fun ComposeContentTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodesWithTag(tag).fetchSemanticsNodes().size
}
