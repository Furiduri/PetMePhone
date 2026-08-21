package com.gcatcode.petmephone.core.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import com.gcatcode.petmephone.core.domain.config.ConfigField
import com.gcatcode.petmephone.core.domain.config.ConfigWriteResult
import com.gcatcode.petmephone.core.domain.config.StoredOverride
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * `config-override-store` spec — round trip, out-of-range rejection with no persistence, and
 * reset-deletes-the-entry, all against a real temp-file `DataStore<Preferences>` (Robolectric-free:
 * `androidx.datastore-core` is pure JVM, matching `OverlayPositionRepositoryImplTest`'s pattern).
 */
class PreferencesConfigOverrideStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: PreferencesConfigOverrideStore

    private fun setUp() {
        val file = temporaryFolder.root.resolve("config_override_test.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        store = PreferencesConfigOverrideStore(dataStore)
    }

    @Test
    fun `an in-range set persists and override emits Present`() = runTest {
        setUp()

        val result = store.set(BalanceConfig.DAILY_TASK_GOAL, 42)

        assertEquals(ConfigWriteResult.Accepted, result)
        val stored = store.override(BalanceConfig.DAILY_TASK_GOAL).first()
        assertTrue(stored is StoredOverride.Present)
        assertEquals(42, (stored as StoredOverride.Present).value)
    }

    @Test
    fun `an out-of-range set returns OutOfRange and leaves raw Preferences unchanged`() = runTest {
        setUp()
        val before = dataStore.data.first()

        val result = store.set(BalanceConfig.DAILY_TASK_GOAL, 0)

        assertEquals(
            ConfigWriteResult.OutOfRange(BalanceConfig.DAILY_TASK_GOAL.key, 1, 100, 0),
            result,
        )
        assertEquals(before, dataStore.data.first())
    }

    /**
     * Seeded via a fake `DataStore<Preferences>` boundary rather than two real sequential writes on
     * the same on-disk file — `OverlayPositionRepositoryImplTest`'s kdoc documents why:
     * `androidx.datastore-core` 1.2.1's Windows `FileStorage` rejects a second real write's atomic
     * rename against the same backing file. This proves the *transform* [PreferencesConfigOverrideStore.reset]
     * submits removes the key, the same way that other test proves its own transform.
     */
    @Test
    fun `reset removes the key from the raw Preferences, not just the resolved default`() = runTest {
        val key = intPreferencesKey(BalanceConfig.DAILY_TASK_GOAL.key)
        val seeded = mutablePreferencesOf(key to 42)
        var appliedResult: Preferences? = null
        val fakeDataStore = mockk<DataStore<Preferences>>()
        coEvery { fakeDataStore.updateData(any()) } coAnswers {
            val transform = firstArg<suspend (Preferences) -> Preferences>()
            transform(seeded).also { appliedResult = it }
        }
        val storeUnderTest = PreferencesConfigOverrideStore(fakeDataStore)

        storeUnderTest.reset(BalanceConfig.DAILY_TASK_GOAL)

        val result = requireNotNull(appliedResult)
        assertNull(result[key])
    }

    @Test
    fun `a rejected write on a field that already holds a valid override leaves it unchanged`() = runTest {
        setUp()
        store.set(BalanceConfig.DAILY_TASK_GOAL, 42)

        store.set(BalanceConfig.DAILY_TASK_GOAL, 0)

        val stored = store.override(BalanceConfig.DAILY_TASK_GOAL).first()
        assertEquals(42, (stored as StoredOverride.Present).value)
    }

    /**
     * Applies one store operation against seeded [Preferences] and returns the result the store's
     * transform produced, without touching a real file.
     *
     * The version-stamp tests below need two writes to be meaningful, and two real sequential
     * writes to one temp file cannot be done here: `DataStore` fails the second rename on Windows
     * while the first write still holds the file. This is the same fake-transform pattern the
     * reset test above already uses, and it asserts the stronger property anyway — exactly which
     * keys one operation touches, rather than only where the value landed.
     */
    private suspend fun applyTransform(
        seeded: Preferences,
        operation: suspend (PreferencesConfigOverrideStore) -> Unit,
    ): Preferences {
        var applied: Preferences? = null
        val fakeDataStore = mockk<DataStore<Preferences>>()
        coEvery { fakeDataStore.updateData(any()) } coAnswers {
            val transform = firstArg<suspend (Preferences) -> Preferences>()
            transform(seeded).also { applied = it }
        }
        operation(PreferencesConfigOverrideStore(fakeDataStore))
        return requireNotNull(applied)
    }

    private fun versionKey(field: ConfigField<*>) = intPreferencesKey("${field.key}.written_under_version")

    @Test
    fun `a write stamps the version on that field alone`() = runTest {
        val result = applyTransform(mutablePreferencesOf()) { store ->
            store.set(BalanceConfig.DAILY_TASK_GOAL, 42)
        }

        assertEquals(BalanceConfig.GROUP.currentVersion, result[versionKey(BalanceConfig.DAILY_TASK_GOAL)])
        // The stamp belongs to the field, not to the group. A group-wide stamp is what lets a
        // later sibling write re-date this field.
        assertNull(result[versionKey(BalanceConfig.STANDARD_TASK_POINTS)])
    }

    @Test
    fun `a stamp written under an older version survives a sibling write under the current one`() = runTest {
        // A field tuned under balance revision 0, still holding that stamp.
        val seeded = mutablePreferencesOf(
            intPreferencesKey(BalanceConfig.DAILY_TASK_GOAL.key) to 42,
            versionKey(BalanceConfig.DAILY_TASK_GOAL) to 0,
        )

        val result = applyTransform(seeded) { store ->
            store.set(BalanceConfig.STANDARD_TASK_POINTS, 3)
        }

        // Writing a different field of the same group must not re-date this one. Otherwise a
        // number carried over from an earlier revision reads as a fresh decision, which is the
        // state version staleness exists to make visible.
        assertEquals(0, result[versionKey(BalanceConfig.DAILY_TASK_GOAL)])
        assertEquals(42, result[intPreferencesKey(BalanceConfig.DAILY_TASK_GOAL.key)])
    }

    @Test
    fun `reset drops the version stamp along with the value`() = runTest {
        val seeded = mutablePreferencesOf(
            intPreferencesKey(BalanceConfig.DAILY_TASK_GOAL.key) to 42,
            versionKey(BalanceConfig.DAILY_TASK_GOAL) to 1,
        )

        val result = applyTransform(seeded) { store ->
            store.reset(BalanceConfig.DAILY_TASK_GOAL)
        }

        // A field with no override has nothing to date, and a stamp left behind would re-attach
        // itself to whatever is written next.
        assertNull(result[intPreferencesKey(BalanceConfig.DAILY_TASK_GOAL.key)])
        assertNull(result[versionKey(BalanceConfig.DAILY_TASK_GOAL)])
    }
}
