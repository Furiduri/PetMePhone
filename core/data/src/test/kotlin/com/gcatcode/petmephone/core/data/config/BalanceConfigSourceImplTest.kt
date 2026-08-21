package com.gcatcode.petmephone.core.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

/**
 * `config-override-store` spec — an empty store folds to the complete shipped defaults, a write is
 * observed with no re-injection, and absence never resolves to zero. Robolectric only for the
 * `kotlinx.coroutines.flow.combine` + `Dispatchers.Default` scope used by [BalanceConfigSourceImpl];
 * the `DataStore<Preferences>` itself is the same real temp-file instance the rest of this package
 * uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BalanceConfigSourceImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: PreferencesConfigOverrideStore
    private lateinit var source: BalanceConfigSourceImpl

    private fun setUp() {
        val file = temporaryFolder.root.resolve("balance_config_source_test.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        store = PreferencesConfigOverrideStore(dataStore)
        source = BalanceConfigSourceImpl(store)
    }

    @Test
    fun `an empty store yields the complete shipped-default object`() = runTest {
        setUp()

        val resolved = source.config.first { it == BalanceConfig() }

        assertEquals(BalanceConfig(), resolved)
    }

    @Test
    fun `a set is observed with no re-injection`() = runTest {
        setUp()
        source.config.first { it == BalanceConfig() }

        store.set(BalanceConfig.DAILY_TASK_GOAL, 42)

        val resolved = source.config.first { it.dailyTaskGoal == 42 }
        // Every other field is still its shipped default — no whole-config write happened.
        assertEquals(BalanceConfig(dailyTaskGoal = 42), resolved)
        assertNotEquals(BalanceConfig(), resolved)
    }

    @Test
    fun `a failing read yields the complete shipped-default object as one whole-object equality`() = runTest {
        val failingDataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("simulated corrupt read") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                throw UnsupportedOperationException("not used by this test")
        }
        val failingSource = BalanceConfigSourceImpl(PreferencesConfigOverrideStore(failingDataStore))

        val resolved = failingSource.config.first { it == BalanceConfig() }

        assertEquals(BalanceConfig(), resolved)
    }
}
