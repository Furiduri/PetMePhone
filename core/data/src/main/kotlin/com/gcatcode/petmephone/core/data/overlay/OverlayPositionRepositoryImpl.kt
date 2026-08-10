package com.gcatcode.petmephone.core.data.overlay

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.gcatcode.petmephone.core.domain.overlay.OverlayPosition
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Absence of a persisted position is a real state (nothing dragged yet), explicitly mapped to
 * [OverlayPosition.DEFAULT] here rather than silently reading zero out of missing preference keys.
 */
class OverlayPositionRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OverlayPositionRepository {

    override val position: Flow<OverlayPosition>
        get() = dataStore.data.map { preferences ->
            val x = preferences[X_KEY]
            val y = preferences[Y_KEY]
            if (x == null || y == null) {
                OverlayPosition.DEFAULT
            } else {
                OverlayPosition(x, y)
            }
        }

    private companion object {
        val X_KEY = intPreferencesKey("overlay_position_x")
        val Y_KEY = intPreferencesKey("overlay_position_y")
    }
}
