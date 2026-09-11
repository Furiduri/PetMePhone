package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.test.core.app.ApplicationProvider
import com.gcatcode.petmephone.feature.overlay.R
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.gcatcode.petmephone.core.domain.draft.AuthoringStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Help is per step, and it explains why answering matters.
 *
 * The first wiring pointed every step's `?` at the old generic task-input instructions, so all
 * three showed the same page. The user opens it to learn about the field in front of them; a shared
 * page answers a question they did not ask.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickMenuStepHelpContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun textOf(tag: String): String =
        composeRule.onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Text)
            ?.joinToString(" ") { annotated -> annotated.text }
            .orEmpty()

    private fun setHelp(step: AuthoringStep, onLeave: () -> Unit = {}) {
        composeRule.setContent {
            Box {
                QuickMenuStepHelpContent(
                    step = step,
                    onLeave = onLeave,
                )
            }
        }
    }

    @Test
    fun `the help and its way back are reachable`() {
        setHelp(AuthoringStep.MINIMUM)

        composeRule.onNodeWithTag(QUICK_MENU_STEP_HELP_TITLE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(QUICK_MENU_STEP_HELP_BODY_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(QUICK_MENU_STEP_HELP_BACK_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `returning goes back to the step rather than out of the form`() {
        var left = false
        setHelp(AuthoringStep.BEHAVIOR, onLeave = { left = true })

        composeRule.onNodeWithTag(QUICK_MENU_STEP_HELP_BACK_TEST_TAG).performClick()

        assertTrue(left)
    }

    @Test
    fun `no two steps share a title, a body or an example`() {
        // The exact defect this replaces: one generic page behind all three question marks. Read
        // from the resources rather than the tree, because one Compose rule renders one step and
        // the claim is about all three at once.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val titles = AuthoringStep.entries.map { context.getString(it.titleRes()) }
        val bodies = AuthoringStep.entries.map { context.getString(it.bodyRes()) }
        val examples = AuthoringStep.entries.map { context.getString(it.exampleRes()) }

        assertEquals("two steps share a title: $titles", titles.size, titles.toSet().size)
        assertEquals("two steps share a body: $bodies", bodies.size, bodies.toSet().size)
        assertEquals("two steps share an example: $examples", examples.size, examples.toSet().size)
        assertTrue("no help text may be blank", (titles + bodies + examples).none { it.isBlank() })
    }

    private fun AuthoringStep.titleRes(): Int = when (this) {
        AuthoringStep.BEHAVIOR -> R.string.feature_overlay_quickmenu_help_behavior_title
        AuthoringStep.MINIMUM -> R.string.feature_overlay_quickmenu_help_minimum_title
        AuthoringStep.ANCHOR -> R.string.feature_overlay_quickmenu_help_anchor_title
    }

    private fun AuthoringStep.bodyRes(): Int = when (this) {
        AuthoringStep.BEHAVIOR -> R.string.feature_overlay_quickmenu_help_behavior_body
        AuthoringStep.MINIMUM -> R.string.feature_overlay_quickmenu_help_minimum_body
        AuthoringStep.ANCHOR -> R.string.feature_overlay_quickmenu_help_anchor_body
    }

    private fun AuthoringStep.exampleRes(): Int = when (this) {
        AuthoringStep.BEHAVIOR -> R.string.feature_overlay_quickmenu_help_behavior_example
        AuthoringStep.MINIMUM -> R.string.feature_overlay_quickmenu_help_minimum_example
        AuthoringStep.ANCHOR -> R.string.feature_overlay_quickmenu_help_anchor_example
    }

    @Test
    fun `the behavior help explains why concreteness matters`() {
        setHelp(AuthoringStep.BEHAVIOR)

        val body = textOf(QUICK_MENU_STEP_HELP_BODY_TEST_TAG)
        assertTrue("expected the vague-cue reasoning, got: $body", body.contains("vague", ignoreCase = true))
    }

    @Test
    fun `the minimum help says it is worth as much as the full version`() {
        // The field users do not understand from its label, and the one #99's rows depend on.
        setHelp(AuthoringStep.MINIMUM)

        val body = textOf(QUICK_MENU_STEP_HELP_BODY_TEST_TAG)
        assertTrue("expected the no-reduced-credit promise, got: $body", body.contains("as much", ignoreCase = true))
    }

    @Test
    fun `the cue help explains why a part of the day beats a clock`() {
        setHelp(AuthoringStep.ANCHOR)

        val body = textOf(QUICK_MENU_STEP_HELP_BODY_TEST_TAG)
        assertTrue("expected the cue-strength reasoning, got: $body", body.contains("clock", ignoreCase = true))
    }

    @Test
    fun `every step carries a worked example`() {
        setHelp(AuthoringStep.MINIMUM)

        val example = textOf(QUICK_MENU_STEP_HELP_EXAMPLE_TEST_TAG)
        assertTrue("an example teaches this where a definition does not, got: $example", example.isNotBlank())
    }
}
