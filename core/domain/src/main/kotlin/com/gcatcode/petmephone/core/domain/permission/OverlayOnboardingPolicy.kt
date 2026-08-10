package com.gcatcode.petmephone.core.domain.permission

/**
 * Pure decision logic over [OverlayOnboardingHistory] — no Android, no I/O, unit testable on the
 * JVM. There is no OS-level "never ask again" signal for `SYSTEM_ALERT_WINDOW`, so anti-nagging is
 * entirely the app's discipline: after one refusal, onboarding is not auto-shown again. The
 * passive, dismissible entry point that replaces it belongs to the onboarding-screen issue.
 */
object OverlayOnboardingPolicy {

    fun shouldAutoShowOnboarding(history: OverlayOnboardingHistory): Boolean =
        !history.hasSeenOnboarding && history.refusalCount == 0
}
