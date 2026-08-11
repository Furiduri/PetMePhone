package com.gcatcode.petmephone.core.data.overlay

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionFraction
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Absence of a persisted position is a real state (nothing dragged yet) and is emitted as `null`,
 * never as a stand-in coordinate and never as zero read out of missing preference keys.
 * [OverlayPositionFraction.validOrNull] is the single gate a value coming off disk passes through,
 * so a half-written pair, a `NaN`, or an out-of-range float can never masquerade as a real
 * position.
 *
 * Explicit non-migration (`design.md` — "The fraction migration"): slice 1's
 * `intPreferencesKey("overlay_position_x"/"_y")` were shipped ahead of drag and are read by nothing
 * before or after this change — no device in the world holds a value under them. They are never
 * read here, and [save] removes them in the same `edit` block as its first successful write, so no
 * orphan survives a real usage.
 */
class OverlayPositionRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OverlayPositionRepository {

    override val position: Flow<OverlayPositionFraction?>
        get() = dataStore.data.map { preferences ->
            OverlayPositionFraction.validOrNull(preferences[X_FRACTION_KEY], preferences[Y_FRACTION_KEY])
        }

    override suspend fun save(position: OverlayPositionFraction) {
        dataStore.edit { preferences ->
            preferences[X_FRACTION_KEY] = position.x
            preferences[Y_FRACTION_KEY] = position.y
            // Legacy pixel keys were never written by any shipped path, but removing them here —
            // rather than trusting that absence — is what makes the non-migration explicit instead
            // of merely assumed.
            preferences.remove(LEGACY_X_KEY)
            preferences.remove(LEGACY_Y_KEY)
        }
    }

    private companion object {
        val X_FRACTION_KEY = floatPreferencesKey("overlay_position_x_fraction")
        val Y_FRACTION_KEY = floatPreferencesKey("overlay_position_y_fraction")

        // Legacy slice-1 keys. Never read; removed on first successful save. See class kdoc.
        val LEGACY_X_KEY = intPreferencesKey("overlay_position_x")
        val LEGACY_Y_KEY = intPreferencesKey("overlay_position_y")
    }
}
