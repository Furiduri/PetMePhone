package com.gcatcode.petmephone.feature.overlay.service

import android.app.Service
import android.view.View
import android.view.WindowManager
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionFraction
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionRepository
import com.gcatcode.petmephone.core.domain.permission.OverlayPermissionChecker
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * Covers issue #13's Robolectric test-case note: `onCreate`/`onStartCommand` add the overlay
 * exactly once, `onDestroy` removes it exactly once, and `onStartCommand` returns `START_STICKY`.
 * `WindowManager` is injected (not fetched inline) precisely so it can be mocked here instead of
 * needing a real display — issue #13's explicit reasoning for making it injectable.
 *
 * `@AndroidEntryPoint`'s generated `onCreate` unconditionally calls Hilt's `inject()` before any
 * test code runs, so this needs a real (if test-scoped) Hilt component — [HiltTestApplication] —
 * exactly like the project's existing `androidTest` Hilt tests, just hosted under Robolectric
 * instead of on-device.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltAndroidTest
// See PetMePhoneApplicationWorkManagerTest: Robolectric 4.16.1 has no SDK 37 shadows yet.
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class PetOverlayServiceTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var controller: ServiceController<PetOverlayService>
    private lateinit var windowManager: WindowManager

    @Before
    fun setUp() {
        hiltRule.inject()
        // Unconfined, not Standard: the service's position-collection coroutine must run
        // synchronously up to its first suspension point the moment it is launched, since these
        // tests assert on `addView` having already happened right after `onStartCommand` returns.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * `controller.create()` runs the real `@AndroidEntryPoint`-generated `onCreate`, which calls
     * Hilt's `inject()` and overwrites any field set beforehand with the real (production)
     * bindings. So the test doubles are installed *after* `create()`, once Hilt has already had
     * its turn, and before `onStartCommand` — the method under test — runs.
     */
    private fun buildAndCreateService(
        canDrawOverlays: Boolean = true,
        position: OverlayPositionFraction? = OverlayPositionFraction(x = 0.1f, y = 0.2f),
    ): ServiceController<PetOverlayService> {
        controller = Robolectric.buildService(PetOverlayService::class.java)
        controller.create()

        windowManager = mockk(relaxed = true)
        val service = controller.get()
        service.overlayPermissionChecker = mockk<OverlayPermissionChecker> {
            every { canDrawOverlays() } returns canDrawOverlays
        }
        service.positionRepository = mockk<OverlayPositionRepository> {
            every { this@mockk.position } returns flowOf(position)
        }
        service.windowManager = windowManager
        return controller
    }

    private fun idleMainLooper() {
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun `onStartCommand returns START_STICKY and adds the overlay exactly once`() {
        val controller = buildAndCreateService()

        val result = controller.get().onStartCommand(null, 0, 1)
        idleMainLooper()

        assertEquals(Service.START_STICKY, result)
        verify(exactly = 1) { windowManager.addView(any<View>(), any()) }
    }

    @Test
    fun `onDestroy removes the overlay exactly once`() {
        val controller = buildAndCreateService()
        controller.get().onStartCommand(null, 0, 1)
        idleMainLooper()

        controller.destroy()

        verify(exactly = 1) { windowManager.removeView(any()) }
    }

    @Test
    fun `permission not granted stops the service without adding a window`() {
        val controller = buildAndCreateService(canDrawOverlays = false)

        val result = controller.get().onStartCommand(null, 0, 1)

        assertEquals(Service.START_NOT_STICKY, result)
        verify(exactly = 0) { windowManager.addView(any(), any()) }
    }

    @Test
    fun `onTaskRemoved does not stop the service`() {
        val controller = buildAndCreateService()
        controller.get().onStartCommand(null, 0, 1)
        idleMainLooper()

        controller.get().onTaskRemoved(null)

        // No removeView call means the service (and its window) is still alive.
        verify(exactly = 0) { windowManager.removeView(any()) }
    }
}
