package com.gcatcode.petmephone.feature.overlay.quickmenu.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import com.gcatcode.petmephone.core.domain.metric.MetricReading
import org.junit.Rule
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

/**
 * `overlay-quick-menu`'s "no text field renders in the card" scenario, and #18's blocking
 * question: the shell must not pre-empt the IME spike by shipping a text field anywhere in the
 * card. Fails the moment any editable/IME-triggering node is added — a `TextField` or
 * `BasicTextField` anywhere in [QuickMenuCard]'s tree carries `hasSetTextAction()`/`hasImeAction()`
 * semantics that no other node in this composition produces.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickMenuCardNoTextFieldTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `no editable text field or IME-triggering element exists anywhere in the card`() {
        composeRule.setContent {
            QuickMenuCard(
                hunger = MetricReading.Available(percent = 42),
                happiness = MetricReading.Unavailable,
                energy = MetricReading.Unavailable,
                onLaunchApp = {},
            )
        }

        // Failing input: adding a TextField/BasicTextField anywhere in QuickMenuCard or MetricRow
        // gives it a set-text semantics action, raising this count above 0 — the concrete
        // regression this test guards.
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
}
