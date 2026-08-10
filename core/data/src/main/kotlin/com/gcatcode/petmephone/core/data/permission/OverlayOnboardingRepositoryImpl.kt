package com.gcatcode.petmephone.core.data.permission

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.gcatcode.petmephone.core.domain.permission.OverlayOnboardingHistory
import com.gcatcode.petmephone.core.domain.permission.OverlayOnboardingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Persists interaction history only — never a boolean that looks like the permission grant. See
 * [OverlayOnboardingRepository]'s kdoc.
 */
class OverlayOnboardingRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OverlayOnboardingRepository {

    override val history: Flow<OverlayOnboardingHistory>
        get() = dataStore.data.map { preferences ->
            OverlayOnboardingHistory(
                hasSeenOnboarding = preferences[HAS_SEEN_ONBOARDING_KEY] ?: false,
                refusalCount = preferences[REFUSAL_COUNT_KEY] ?: 0,
                lastRefusalAtEpochMillis = preferences[LAST_REFUSAL_AT_KEY],
            )
        }

    override suspend fun markOnboardingSeen() {
        dataStore.edit { preferences -> preferences[HAS_SEEN_ONBOARDING_KEY] = true }
    }

    override suspend fun recordRefusal(atEpochMillis: Long) {
        dataStore.edit { preferences ->
            val currentCount = preferences[REFUSAL_COUNT_KEY] ?: 0
            preferences[REFUSAL_COUNT_KEY] = currentCount + 1
            preferences[LAST_REFUSAL_AT_KEY] = atEpochMillis
        }
    }

    private companion object {
        val HAS_SEEN_ONBOARDING_KEY = booleanPreferencesKey("overlay_onboarding_seen")
        val REFUSAL_COUNT_KEY = intPreferencesKey("overlay_onboarding_refusal_count")
        val LAST_REFUSAL_AT_KEY = longPreferencesKey("overlay_onboarding_last_refusal_at")
    }
}
