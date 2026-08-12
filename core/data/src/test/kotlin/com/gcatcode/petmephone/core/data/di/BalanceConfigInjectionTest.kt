package com.gcatcode.petmephone.core.data.di

import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `balance-configuration` spec, "BalanceConfig resolves through injection": builds a real Hilt
 * `SingletonComponent` — no manual `BalanceConfig()` construction at the call site — and confirms
 * `DataModule` supplies it. `HiltTestApplication` runs under Robolectric exactly like
 * `PetOverlayServiceTest` does in `:feature:overlay`, just hosted here since `DataModule` is the
 * one that must be under test.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class BalanceConfigInjectionTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BalanceConfigEntryPoint {
        fun balanceConfig(): BalanceConfig
    }

    @Test
    fun `DataModule supplies BalanceConfig through the Hilt graph`() {
        hiltRule.inject()

        val entryPoint = EntryPointAccessors.fromApplication(
            RuntimeEnvironment.getApplication(),
            BalanceConfigEntryPoint::class.java,
        )

        assertEquals(BalanceConfig(), entryPoint.balanceConfig())
    }
}
