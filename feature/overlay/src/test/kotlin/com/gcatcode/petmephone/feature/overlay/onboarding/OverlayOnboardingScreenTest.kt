package com.gcatcode.petmephone.feature.overlay.onboarding

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gcatcode.petmephone.core.domain.permission.OverlayOnboardingHistory
import com.gcatcode.petmephone.core.domain.permission.OverlayOnboardingRepository
import com.gcatcode.petmephone.core.domain.permission.OverlayPermissionChecker
import com.gcatcode.petmephone.core.domain.permission.OverlaySettingsLauncher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `[ONBOARD-1]` all four claims are present; `[ONBOARD-2]` the primary action calls the mechanics
 * layer exactly once and this file constructs no `Intent(Settings...)` of its own — enforced both
 * structurally (this file's own source has no such import) and behaviourally (the fake launcher's
 * call count).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OverlayOnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakePermissionChecker(var granted: Boolean = false) : OverlayPermissionChecker {
        override fun canDrawOverlays(): Boolean = granted
    }

    private class FakeOnboardingRepository : OverlayOnboardingRepository {
        val flow = MutableStateFlow(OverlayOnboardingHistory.NEVER_SHOWN)
        override val history: Flow<OverlayOnboardingHistory> get() = flow
        override suspend fun markOnboardingSeen() {
            flow.value = flow.value.copy(hasSeenOnboarding = true)
        }
        override suspend fun recordRefusal(atEpochMillis: Long) {
            flow.value = flow.value.copy(
                refusalCount = flow.value.refusalCount + 1,
                lastRefusalAtEpochMillis = atEpochMillis,
            )
        }
    }

    private class FakeSettingsLauncher : OverlaySettingsLauncher {
        var launchCount = 0
        override fun launchOverlaySettings() {
            launchCount++
        }
    }

    @Test
    fun `all four required claims are present in the rendered screen`() {
        val viewModel = OverlayOnboardingViewModel(FakePermissionChecker(), FakeOnboardingRepository())

        composeRule.setContent {
            OverlayOnboardingScreen(viewModel = viewModel, settingsLauncher = FakeSettingsLauncher())
        }

        composeRule.onNodeWithText("A small pet will be drawn on top of your other apps.").assertExists()
        composeRule.onNodeWithText(
            "The app cannot see, read, or interact with the content of other apps, and does not capture your screen.",
        ).assertExists()
        composeRule.onNodeWithText("No data leaves this device.").assertExists()
        composeRule.onNodeWithText("You can revoke this permission at any time from system Settings.").assertExists()
    }

    @Test
    fun `primary action calls the mechanics layer exactly once, never a direct Settings intent`() {
        val viewModel = OverlayOnboardingViewModel(FakePermissionChecker(), FakeOnboardingRepository())
        val settingsLauncher = FakeSettingsLauncher()

        composeRule.setContent {
            OverlayOnboardingScreen(viewModel = viewModel, settingsLauncher = settingsLauncher)
        }

        composeRule.onNodeWithTag(ONBOARDING_PRIMARY_ACTION_TEST_TAG).performClick()

        assertEquals(1, settingsLauncher.launchCount)
    }

    @Test
    fun `the screen source file constructs no direct Settings intent`() {
        val file = resolveScreenSourceFile()
        assertTrue("Expected to find OverlayOnboardingScreen.kt at ${file.path}", file.exists())
        val text = file.readText()
        assertFalse("Screen must not import android.provider.Settings", text.contains("android.provider.Settings"))
        assertFalse("Screen must not construct Intent(Settings", text.contains("Intent(Settings"))
    }

    private fun resolveScreenSourceFile(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val candidate = File(
                dir,
                "src/main/kotlin/com/gcatcode/petmephone/feature/overlay/onboarding/OverlayOnboardingScreen.kt",
            )
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return candidate
        }
        return File(dir, "OverlayOnboardingScreen.kt")
    }
}
