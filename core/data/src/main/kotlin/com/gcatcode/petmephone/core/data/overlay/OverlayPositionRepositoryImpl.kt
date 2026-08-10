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
 * Absence of a persisted position is a real state (nothing dragged yet) and is emitted as `null`,
 * never as a stand-in coordinate and never as zero read out of missing preference keys. Resolving
 * that absence into a real placement needs the screen bounds, which this module does not have.
 */
class OverlayPositionRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OverlayPositionRepository {

    override val position: Flow<OverlayPosition?>
        get() = dataStore.data.map { preferences ->
            val x = preferences[X_KEY]
            val y = preferences[Y_KEY]
            // Both keys or neither. A half-written pair is treated as absent rather than combined
            // with a substitute for the missing half, which would place the pet somewhere the user
            // never chose while looking like a restored position.
            if (x == null || y == null) null else OverlayPosition(x, y)
        }

    private companion object {
        val X_KEY = intPreferencesKey("overlay_position_x")
        val Y_KEY = intPreferencesKey("overlay_position_y")
    }
}
