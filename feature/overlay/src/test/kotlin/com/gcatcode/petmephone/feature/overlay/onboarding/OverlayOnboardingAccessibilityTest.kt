package com.gcatcode.petmephone.feature.overlay.onboarding

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.gcatcode.petmephone.core.domain.permission.OverlayOnboardingHistory
import com.gcatcode.petmephone.core.domain.permission.OverlayOnboardingRepository
import com.gcatcode.petmephone.core.domain.permission.OverlayPermissionChecker
import com.gcatcode.petmephone.core.domain.permission.OverlaySettingsLauncher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `[ONBOARD-7]` — every interactive element on [OverlayOnboardingScreen] and [ReEntryCard] has a
 * content description and a touch target of at least 48dp. Sized assertions fail if the modifier
 * chain ever drops the minimum size, so this cannot pass vacuously.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OverlayOnboardingAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakePermissionChecker : OverlayPermissionChecker {
        override fun canDrawOverlays(): Boolean = false
    }

    private class FakeOnboardingRepository : OverlayOnboardingRepository {
        val flow = MutableStateFlow(OverlayOnboardingHistory.NEVER_SHOWN)
        override val history: Flow<OverlayOnboardingHistory> get() = flow
        override suspend fun markOnboardingSeen() {}
        override suspend fun recordRefusal(atEpochMillis: Long) {}
    }

    private class FakeSettingsLauncher : OverlaySettingsLauncher {
        override fun launchOverlaySettings() {}
    }

    @Test
    fun `the onboarding screen's primary action has a description and a 48dp touch target`() {
        val viewModel = OverlayOnboardingViewModel(FakePermissionChecker(), FakeOnboardingRepository())

        composeRule.setContent {
            OverlayOnboardingScreen(viewModel = viewModel, settingsLauncher = FakeSettingsLauncher())
        }

        composeRule.onNodeWithTag(ONBOARDING_PRIMARY_ACTION_TEST_TAG)
            .assertContentDescriptionEquals("Open system Settings to grant the overlay permission")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun `every interactive element on the re-entry card has a description and a 48dp touch target`() {
        composeRule.setContent {
            ReEntryCard(onReopenOnboarding = {}, onDismiss = {})
        }

        composeRule.onNodeWithTag(REENTRY_CARD_REOPEN_TEST_TAG)
            .assertContentDescriptionEquals("Open the overlay permission screen again")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)

        composeRule.onNodeWithTag(REENTRY_CARD_DISMISS_TEST_TAG)
            .assertContentDescriptionEquals("Dismiss this reminder")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }
}
