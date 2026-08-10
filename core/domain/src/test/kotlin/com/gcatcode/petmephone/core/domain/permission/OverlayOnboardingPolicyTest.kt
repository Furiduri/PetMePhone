package com.gcatcode.petmephone.core.domain.permission

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayOnboardingPolicyTest {

    @Test
    fun `never shown and never refused shows onboarding`() {
        assertTrue(OverlayOnboardingPolicy.shouldAutoShowOnboarding(OverlayOnboardingHistory.NEVER_SHOWN))
    }

    @Test
    fun `one refusal stops auto-showing onboarding`() {
        val history = OverlayOnboardingHistory(
            hasSeenOnboarding = true,
            refusalCount = 1,
            lastRefusalAtEpochMillis = 1_000L,
        )

        assertFalse(OverlayOnboardingPolicy.shouldAutoShowOnboarding(history))
    }

    @Test
    fun `already seen with zero refusals does not re-trigger auto-show`() {
        val history = OverlayOnboardingHistory(
            hasSeenOnboarding = true,
            refusalCount = 0,
            lastRefusalAtEpochMillis = null,
        )

        assertFalse(OverlayOnboardingPolicy.shouldAutoShowOnboarding(history))
    }

    @Test
    fun `repeated refusals keep onboarding hidden`() {
        val history = OverlayOnboardingHistory(
            hasSeenOnboarding = true,
            refusalCount = 3,
            lastRefusalAtEpochMillis = 5_000L,
        )

        assertFalse(OverlayOnboardingPolicy.shouldAutoShowOnboarding(history))
    }
}
