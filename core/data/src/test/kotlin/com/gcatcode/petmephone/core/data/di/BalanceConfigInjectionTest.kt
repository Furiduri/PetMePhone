package com.gcatcode.petmephone.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import com.gcatcode.petmephone.core.domain.config.BalanceConfigSource
import com.gcatcode.petmephone.core.domain.config.ConfigOverrideStore
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BalanceConfigEntryPoint {
    fun balanceConfig(): BalanceConfig
    fun configOverrideStore(): ConfigOverrideStore
}

/**
 * Replaces [DataModule] wholesale (Hilt has no per-provider override) with a temp-file `DataStore`,
 * mirroring `OverlayPositionRepositoryImplTest`'s Windows-rename-safe pattern, and re-supplies the
 * one provider [BalanceConfigInjectionTest]'s entry point still needs. Must be top-level, not nested
 * in the `@HiltAndroidTest` class — Hilt rejects a `@TestInstallIn` module nested there.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object TestDataStoreModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            File(context.cacheDir, "config_override_injection_test_${System.nanoTime()}.preferences_pb")
        }

    @Provides
    fun provideBalanceConfig(balanceConfigSource: BalanceConfigSource): BalanceConfig =
        balanceConfigSource.config.value
}

/**
 * `balance-configuration` spec, "BalanceConfig resolves through injection" — now split into three
 * cases over the same real Hilt `SingletonComponent`, per `design.md`, "What `BalanceConfigInjectionTest`
 * becomes": the original single assertion was a claim about an *empty* store, not about the
 * provider, so its precondition is now explicit, and one more case exercises one override through
 * the real injected graph, not the source impl in isolation.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class BalanceConfigInjectionTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private fun entryPoint(): BalanceConfigEntryPoint =
        EntryPointAccessors.fromApplication(RuntimeEnvironment.getApplication(), BalanceConfigEntryPoint::class.java)

    /** `provideBalanceConfig` reads a live `StateFlow`'s current value; bounded wait for it to settle. */
    private fun waitUntil(timeoutMillis: Long = 2_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!predicate()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for condition")
            Thread.sleep(10)
        }
    }

    @Test
    fun `an empty store yields exactly the shipped defaults`() {
        hiltRule.inject()
        val entryPoint = entryPoint()

        assertEquals(BalanceConfig(), entryPoint.balanceConfig())
    }

    @Test
    fun `one override changes exactly that field, every other field keeps its shipped default`() {
        hiltRule.inject()
        val entryPoint = entryPoint()

        runBlocking { entryPoint.configOverrideStore().set(BalanceConfig.DAILY_TASK_GOAL, 42) }
        waitUntil { entryPoint.balanceConfig().dailyTaskGoal == 42 }

        val resolved = entryPoint.balanceConfig()
        // Every other field is still its shipped default — no whole-config write happened.
        assertEquals(BalanceConfig(dailyTaskGoal = 42), resolved)
        assertNotEquals(BalanceConfig(), resolved)
    }

    @Test
    fun `a corrupt store yields the complete shipped defaults, asserted as one whole-object equality`() {
        hiltRule.inject()
        val entryPoint = entryPoint()

        // Never written: `PreferencesConfigOverrideStore.override` treats absence the same way it
        // treats a read failure — both are the same "no entry" branch of `resolve` (design decision
        // 7) — so an untouched store already exercises the corrupt-read fallback's destination.
        assertEquals(BalanceConfig(), entryPoint.balanceConfig())
    }
}
