package com.gcatcode.petmephone.feature.overlay.ui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

/**
 * `config-override-store` spec, "Both BalanceConfig and PetAnimationConfig are observable without
 * a restart" — the `PetAnimationConfig` half, resolved through [MutableFakeConfigOverrideStore], an
 * in-memory fake over the `:core:domain` `ConfigOverrideStore` interface only, so this test needs
 * no `:core:data` import at all — proving design decision 5's seam from the consuming side.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PetAnimationConfigSourceTest {

    @Test
    fun `an empty fake store resolves to the shipped-default PetAnimationConfig`() = runTest {
        val source = PetAnimationConfigSource(MutableFakeConfigOverrideStore())

        val resolved = source.config.first()

        assertEquals(PetAnimationConfig.FRAME_INTERVAL_MILLIS.shippedDefault, resolved.frameIntervalMillis)
        assertEquals(PetAnimationConfig.MIN_FRAME_INTERVAL_MILLIS.shippedDefault, resolved.minFrameIntervalMillis)
        assertEquals(PetAnimationConfig.STATE_SHARING_TIMEOUT_MILLIS.shippedDefault, resolved.stateSharingTimeoutMillis)
    }

    @Test
    fun `a fake set on a field emits a new PetAnimationConfig from config`() = runTest {
        val fakeStore = MutableFakeConfigOverrideStore()
        val source = PetAnimationConfigSource(fakeStore)
        source.config.first()

        fakeStore.set(PetAnimationConfig.FRAME_INTERVAL_MILLIS, 500L)

        val resolved = source.config.first { it.frameIntervalMillis == 500L }
        assertEquals(500L, resolved.frameIntervalMillis)
    }
}
