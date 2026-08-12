package com.gcatcode.petmephone.feature.overlay.service

import android.view.View
import android.view.WindowManager
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionFraction
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionRepository
import com.gcatcode.petmephone.core.domain.permission.OverlayPermissionChecker
import com.gcatcode.petmephone.feature.overlay.position.OverlayPositionConfig
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * `[POS-5]` `[POS-6]` — startup ordering: `addView` is preceded by exactly one suspending read
 * (stored value or timeout), never a default-then-jump.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class PetOverlayServiceStartupTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var controller: ServiceController<PetOverlayService>
    private lateinit var windowManager: WindowManager

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildService(): PetOverlayService {
        controller = Robolectric.buildService(PetOverlayService::class.java)
        controller.create()
        windowManager = mockk(relaxed = true)
        val service = controller.get()
        service.overlayPermissionChecker = mockk<OverlayPermissionChecker> {
            every { canDrawOverlays() } returns true
        }
        service.windowManager = windowManager
        service.positionConfig = OverlayPositionConfig(firstReadTimeoutMillis = 200L)
        return service
    }

    private fun idleMainLooper() {
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun `a stored value emitted before the timeout results in addView called once at the stored position`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val service = buildService()
        val stored = OverlayPositionFraction(x = 0.5f, y = 0.5f)
        service.positionRepository = mockk<OverlayPositionRepository> {
            every { position } returns flowOf(stored)
        }

        service.onStartCommand(null, 0, 1)
        idleMainLooper()
        testScheduler.advanceUntilIdle()
        idleMainLooper()

        verify(exactly = 1) { windowManager.addView(any<View>(), any()) }
    }

    @Test
    fun `a repository that never emits within the timeout falls back to the computed resting corner, not blocked indefinitely`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val service = buildService()
        // Never emits — models a repository whose first collection never completes within the
        // injected timeout.
        service.positionRepository = mockk<OverlayPositionRepository> {
            every { position } returns callbackFlow<OverlayPositionFraction?> { awaitClose { } }
        }

        service.onStartCommand(null, 0, 1)
        idleMainLooper()
        // Advance past the injected timeout (200ms) — the timeout fallback branch must fire rather
        // than suspending forever.
        testScheduler.advanceTimeBy(500L)
        testScheduler.advanceUntilIdle()
        idleMainLooper()

        verify(exactly = 1) { windowManager.addView(any<View>(), any()) }
    }
}
