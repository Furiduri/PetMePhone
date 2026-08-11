package com.gcatcode.petmephone.core.data.overlay

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionFraction
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** `[POS-1]` `[POS-2]` — float keys only, never a fabricated `0f`, explicit legacy-key removal. */
class OverlayPositionRepositoryImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: OverlayPositionRepositoryImpl

    private fun setUp() {
        // Windows rename semantics reject an atomic write over a file that already exists, so the
        // path must be a location inside the temp folder that has not been created yet — DataStore
        // itself creates the file on first write.
        val file = temporaryFolder.root.resolve("overlay_position_test.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        repository = OverlayPositionRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        // Nothing to release explicitly — the temp folder rule deletes the backing file.
    }

    @Test
    fun `no keys written emits null`() = runTest {
        setUp()

        val emitted = repository.position.first()

        assertNull(emitted)
    }

    @Test
    fun `a directly poked NaN emits null, never a fabricated 0f`() = runTest {
        setUp()
        dataStore.edit { preferences ->
            preferences[floatPreferencesKey("overlay_position_x_fraction")] = Float.NaN
            preferences[floatPreferencesKey("overlay_position_y_fraction")] = 0.5f
        }

        val emitted = repository.position.first()

        assertNull(emitted)
    }

    @Test
    fun `a directly poked out-of-range value emits null, never a fabricated 0f`() = runTest {
        setUp()
        dataStore.edit { preferences ->
            preferences[floatPreferencesKey("overlay_position_x_fraction")] = 1.5f
            preferences[floatPreferencesKey("overlay_position_y_fraction")] = 0.5f
        }

        val emitted = repository.position.first()

        assertNull(emitted)
    }

    @Test
    fun `save then read round-trips the stored fraction`() = runTest {
        setUp()
        val saved = OverlayPositionFraction(0.25f, 0.75f)

        repository.save(saved)
        val emitted = repository.position.first()

        assertEquals(saved, emitted)
    }

    @Test
    fun `no legacy int keys exist for position after a save`() = runTest {
        setUp()
        repository.save(OverlayPositionFraction(0.5f, 0.5f))

        val preferences = dataStore.data.first()
        val keyNames = preferences.asMap().keys.map { it.name }

        assertFalse(keyNames.contains("overlay_position_x"))
        assertFalse(keyNames.contains("overlay_position_y"))
    }

    /**
     * Seeding a real on-disk DataStore with legacy keys and then calling [save] requires two
     * sequential writes to the same backing file. `androidx.datastore-core` 1.2.1's Windows
     * `FileStorage` implementation rejects the second write's atomic rename whenever the target
     * already exists (`java.io.IOException: Unable to rename ...`) — reproduced independently with
     * two bare `dataStore.edit` calls against the same file, no repository code involved. This is a
     * Windows-JVM-only limitation of the library (Android's real filesystem allows the same rename
     * fine, which is why production and the real-file tests above pass), so this test fakes the
     * `DataStore<Preferences>` boundary instead of touching a real file, to prove the *transform*
     * [OverlayPositionRepositoryImpl.save] submits removes both legacy keys in the same `edit`
     * block as the write.
     */
    @Test
    fun `legacy int keys present before a save are removed on first successful save`() = runTest {
        val legacyX = intPreferencesKey("overlay_position_x")
        val legacyY = intPreferencesKey("overlay_position_y")
        val seeded = mutablePreferencesOf(legacyX to 42, legacyY to 84)

        var appliedResult: Preferences? = null
        val fakeDataStore = mockk<DataStore<Preferences>>()
        coEvery { fakeDataStore.updateData(any()) } coAnswers {
            val transform = firstArg<suspend (Preferences) -> Preferences>()
            transform(seeded).also { appliedResult = it }
        }
        val repositoryUnderTest = OverlayPositionRepositoryImpl(fakeDataStore)

        repositoryUnderTest.save(OverlayPositionFraction(0.5f, 0.5f))

        val result = requireNotNull(appliedResult)
        assertNull(result[legacyX])
        assertNull(result[legacyY])
        assertEquals(0.5f, result[floatPreferencesKey("overlay_position_x_fraction")])
        assertEquals(0.5f, result[floatPreferencesKey("overlay_position_y_fraction")])
    }
}
