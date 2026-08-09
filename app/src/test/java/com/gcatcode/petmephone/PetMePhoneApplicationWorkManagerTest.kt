package com.gcatcode.petmephone

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.WorkManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Covers the dependency-injection spec scenario "Single WorkManager instance at cold start".
 *
 * This cannot be an instrumented (`androidTest`) test: `CustomTestRunner` substitutes
 * `HiltTestApplication` for the production `Application` in every `:app` instrumented test, so
 * `PetMePhoneApplication` itself is never reachable there.
 *
 * Robolectric runs the real, un-substituted `PetMePhoneApplication` — including its real,
 * KSP-generated Hilt component — as the JVM test process' `Application`. Since WorkManager 2.6,
 * `WorkManager.getInstance(context)` performs on-demand initialisation from
 * `Configuration.Provider` when the `Application` implements it, instead of requiring the
 * manifest `WorkManagerInitializer` (which is deliberately removed from the merged manifest by
 * task 3.4). That on-demand path is exactly what this scenario asserts.
 */
// Robolectric 4.16.1 does not yet ship Android SDK 37 shadows (targetSdk/compileSdk pinned by
// ProjectConfig), so `sdk = 36` pins the highest shadow revision Robolectric currently supports.
// This does not narrow what is asserted: the real, un-substituted `PetMePhoneApplication`, its
// real Hilt component, and the real `WorkManager.getInstance` on-demand-init path are still
// exercised — only the emulated platform revision changes.
@RunWith(RobolectricTestRunner::class)
@Config(application = PetMePhoneApplication::class, sdk = [36])
class PetMePhoneApplicationWorkManagerTest {

    @Test
    fun `WorkManager getInstance does not throw at cold start using the custom Configuration`() {
        val application = RuntimeEnvironment.getApplication() as PetMePhoneApplication

        // No IllegalStateException means exactly one WorkManager initialisation occurred, driven
        // by PetMePhoneApplication's own Configuration.Provider implementation.
        val workManager = WorkManager.getInstance(application)

        assertNotNull(workManager)

        // The scenario asserts two things, and "did not throw" is only the first. This is the
        // second: the Configuration driving that initialisation is the app's own, carrying the
        // Hilt-provided factory rather than WorkManager's default reflective one. Without this,
        // the test would pass just as happily against a default Configuration.
        assertTrue(
            "workManagerConfiguration must carry HiltWorkerFactory, not the default factory",
            application.workManagerConfiguration.workerFactory is HiltWorkerFactory,
        )
    }
}
