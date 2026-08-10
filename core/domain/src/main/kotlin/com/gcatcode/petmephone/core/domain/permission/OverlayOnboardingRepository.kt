package com.gcatcode.petmephone.core.domain.permission

import kotlinx.coroutines.flow.Flow

/**
 * Persists overlay-onboarding *interaction history* only — `hasSeenOnboarding`, refusal count,
 * last refusal timestamp. Deliberately exposes nothing named or shaped like a permission grant;
 * see [OverlayPermissionChecker] for that.
 */
interface OverlayOnboardingRepository {
    val history: Flow<OverlayOnboardingHistory>

    suspend fun markOnboardingSeen()

    suspend fun recordRefusal(atEpochMillis: Long)
}
