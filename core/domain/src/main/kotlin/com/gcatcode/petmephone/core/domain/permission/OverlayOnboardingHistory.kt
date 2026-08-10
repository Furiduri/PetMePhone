package com.gcatcode.petmephone.core.domain.permission

/**
 * User *interaction history* with the overlay-permission onboarding flow — never the permission
 * grant itself. The system is the only source of truth for the grant
 * ([OverlayPermissionChecker]); this describes what the app has already shown the user and how
 * many times they refused, so onboarding does not nag after a refusal.
 *
 * `lastRefusalAtEpochMillis == null` means "never refused" — a distinct state from "refused at
 * time zero," modelled explicitly rather than defaulted to a sentinel.
 */
data class OverlayOnboardingHistory(
    val hasSeenOnboarding: Boolean,
    val refusalCount: Int,
    val lastRefusalAtEpochMillis: Long?,
) {
    companion object {
        val NEVER_SHOWN = OverlayOnboardingHistory(
            hasSeenOnboarding = false,
            refusalCount = 0,
            lastRefusalAtEpochMillis = null,
        )
    }
}
