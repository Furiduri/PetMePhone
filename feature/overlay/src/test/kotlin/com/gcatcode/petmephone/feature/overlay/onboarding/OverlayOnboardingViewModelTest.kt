package com.gcatcode.petmephone.feature.overlay.onboarding

import com.gcatcode.petmephone.core.domain.permission.OverlayOnboardingHistory
import com.gcatcode.petmephone.core.domain.permission.OverlayOnboardingRepository
import com.gcatcode.petmephone.core.domain.permission.OverlayPermissionChecker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `[ONBOARD-3]` re-query on resume advances on grant; `[ONBOARD-4]` refusal leaves the app usable
 * and is recorded exactly once per settings round-trip; `[ONBOARD-5]` an explicit fake proves the
 * re-entry affordance can drive the same [OverlayOnboardingViewModel.onResume] path a resumed
 * screen would.
 */
class OverlayOnboardingViewModelTest {

    private class FakePermissionChecker(var granted: Boolean = false) : OverlayPermissionChecker {
        override fun canDrawOverlays(): Boolean = granted
    }

    private class FakeOnboardingRepository : OverlayOnboardingRepository {
        val flow = MutableStateFlow(OverlayOnboardingHistory.NEVER_SHOWN)
        var markSeenCallCount = 0
        var recordRefusalCallCount = 0

        override val history: Flow<OverlayOnboardingHistory> get() = flow

        override suspend fun markOnboardingSeen() {
            markSeenCallCount++
            flow.value = flow.value.copy(hasSeenOnboarding = true)
        }

        override suspend fun recordRefusal(atEpochMillis: Long) {
            recordRefusalCallCount++
            flow.value = flow.value.copy(
                refusalCount = flow.value.refusalCount + 1,
                lastRefusalAtEpochMillis = atEpochMillis,
            )
        }
    }

    @Test
    fun `resume re-queries the live checker and advances isGranted on a grant`() = runTest {
        val checker = FakePermissionChecker(granted = false)
        val viewModel = OverlayOnboardingViewModel(checker, FakeOnboardingRepository())

        viewModel.onResume()
        assertFalse(viewModel.isGranted.value)

        // Simulates the user granting through system Settings between one resume and the next —
        // never a locally cached value, the live checker's own state changed underneath.
        checker.granted = true
        viewModel.onResume()

        assertTrue(viewModel.isGranted.value)
    }

    @Test
    fun `a refusal after launching settings leaves isGranted false and records exactly one refusal`() = runTest {
        val checker = FakePermissionChecker(granted = false)
        val repository = FakeOnboardingRepository()
        val viewModel = OverlayOnboardingViewModel(checker, repository)

        viewModel.onSettingsLaunched()
        viewModel.onResume()

        assertFalse(viewModel.isGranted.value)
        assertEquals(1, repository.recordRefusalCallCount)
        assertEquals(0, repository.markSeenCallCount)
    }

    @Test
    fun `a resume with no prior settings launch records no refusal`() = runTest {
        val checker = FakePermissionChecker(granted = false)
        val repository = FakeOnboardingRepository()
        val viewModel = OverlayOnboardingViewModel(checker, repository)

        viewModel.onResume()

        assertEquals(0, repository.recordRefusalCallCount)
    }

    @Test
    fun `a grant marks onboarding seen and records no refusal`() = runTest {
        val checker = FakePermissionChecker(granted = true)
        val repository = FakeOnboardingRepository()
        val viewModel = OverlayOnboardingViewModel(checker, repository)

        viewModel.onSettingsLaunched()
        viewModel.onResume()

        assertEquals(1, repository.markSeenCallCount)
        assertEquals(0, repository.recordRefusalCallCount)
    }

    @Test
    fun `shouldAutoShow is true before any refusal and false after one refusal`() = runTest {
        val checker = FakePermissionChecker(granted = false)
        val repository = FakeOnboardingRepository()
        val viewModel = OverlayOnboardingViewModel(checker, repository)

        assertTrue(viewModel.shouldAutoShow.first())

        viewModel.onSettingsLaunched()
        viewModel.onResume()

        assertFalse(viewModel.shouldAutoShow.first())
    }

    @Test
    fun `a relaunch after one refusal does not auto-show again, matching the re-entry affordance's job`() = runTest {
        val checker = FakePermissionChecker(granted = false)
        val repository = FakeOnboardingRepository()
        val firstSessionViewModel = OverlayOnboardingViewModel(checker, repository)
        firstSessionViewModel.onSettingsLaunched()
        firstSessionViewModel.onResume()

        // A fresh view-model instance, as a relaunch would construct — history is what persists,
        // never an in-memory field on the view model.
        val relaunchedViewModel = OverlayOnboardingViewModel(checker, repository)

        assertFalse(relaunchedViewModel.shouldAutoShow.first())

        // The re-entry affordance re-launches the exact same flow: onSettingsLaunched + onResume
        // still work correctly on a relaunched instance, proving the affordance's re-launch path
        // is not broken by the earlier refusal.
        checker.granted = true
        relaunchedViewModel.onSettingsLaunched()
        relaunchedViewModel.onResume()
        assertTrue(relaunchedViewModel.isGranted.value)
    }
}
