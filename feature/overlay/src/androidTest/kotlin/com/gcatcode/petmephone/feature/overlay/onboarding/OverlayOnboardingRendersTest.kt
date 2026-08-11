package com.gcatcode.petmephone.feature.overlay.onboarding

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.gcatcode.petmephone.core.domain.permission.OverlayOnboardingHistory
import com.gcatcode.petmephone.core.domain.permission.OverlayOnboardingRepository
import com.gcatcode.petmephone.core.domain.permission.OverlayPermissionChecker
import com.gcatcode.petmephone.core.domain.permission.OverlaySettingsLauncher
import com.gcatcode.petmephone.feature.overlay.R
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * `[ONBOARD-1]` `[ONBOARD-2]` on a real device or emulator rather than under Robolectric.
 *
 * The JVM suite already asserts these claims; this exists because the slice owes one instrumented
 * attempt per screen that can take one, and because the four claims are the app's honest account of
 * a permission to draw over everything else on the phone — worth confirming they survive a real
 * resource lookup and a real composition, not only a simulated one.
 */
class OverlayOnboardingRendersTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private class RecordingLauncher : OverlaySettingsLauncher {
        var launches = 0
        override fun launchOverlaySettings() {
            launches++
        }
    }

    private fun viewModel() = OverlayOnboardingViewModel(
        permissionChecker = object : OverlayPermissionChecker {
            override fun canDrawOverlays(): Boolean = false
        },
        onboardingRepository = object : OverlayOnboardingRepository {
            override val history = flowOf(OverlayOnboardingHistory.NEVER_SHOWN)
            override suspend fun markOnboardingSeen() = Unit
            override suspend fun recordRefusal(atEpochMillis: Long) = Unit
        },
    )

    @Test
    fun allFourClaimsRenderOnDevice() {
        val launcher = RecordingLauncher()
        composeRule.setContent {
            OverlayOnboardingScreen(viewModel = viewModel(), settingsLauncher = launcher)
        }

        // The four claims the spec requires, read from real resources on a real device.
        for (claim in listOf(
            R.string.feature_overlay_onboarding_claim_what_appears,
            R.string.feature_overlay_onboarding_claim_no_access,
            R.string.feature_overlay_onboarding_claim_no_data_leaves,
            R.string.feature_overlay_onboarding_claim_revocable,
        )) {
            composeRule.onNodeWithText(context.getString(claim)).assertIsDisplayed()
        }
    }

    @Test
    fun theOnlyActionIsClickableAndDelegatesToTheMechanicsLayer() {
        val launcher = RecordingLauncher()
        composeRule.setContent {
            OverlayOnboardingScreen(viewModel = viewModel(), settingsLauncher = launcher)
        }

        composeRule.onNodeWithTag(ONBOARDING_PRIMARY_ACTION_TEST_TAG)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        // `[ONBOARD-2]`: the screen never builds its own Settings intent, it asks the mechanics
        // layer to. Counting the delegation is how that stays true.
        assertEquals(1, launcher.launches)
    }
}
