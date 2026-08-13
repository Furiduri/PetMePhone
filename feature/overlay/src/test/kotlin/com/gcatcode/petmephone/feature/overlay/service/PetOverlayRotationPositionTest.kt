package com.gcatcode.petmephone.feature.overlay.service

import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.Insets
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionFraction
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionRepository
import com.gcatcode.petmephone.core.domain.permission.OverlayPermissionChecker
import com.gcatcode.petmephone.feature.overlay.position.OverlayPositionConfig
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * `[POS-8]` — a configuration change re-derives the position from the stored fraction against the
 * new bounds.
 *
 * Regression for the defect where rotation only clamped the previous pixel coordinates. Rotating
 * into a wider screen leaves every old coordinate inside the new bounds, so the clamp was a no-op
 * and the pet kept its absolute pixels — landing mid-screen instead of against the edge its stored
 * fraction names.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class PetOverlayRotationPositionTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var windowManager: WindowManager

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Zero insets keep the arithmetic in the assertions about bounds, not about system bars. */
    private fun metricsOf(widthPx: Int, heightPx: Int): WindowMetrics = mockk {
        every { bounds } returns Rect(0, 0, widthPx, heightPx)
        every { windowInsets } returns mockk<WindowInsets> {
            every { getInsetsIgnoringVisibility(any()) } returns Insets.of(0, 0, 0, 0)
        }
    }

    private fun idleMainLooper() {
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun `rotating re-derives the position from the stored fraction instead of keeping stale pixels`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        val controller = Robolectric.buildService(PetOverlayService::class.java)
        controller.create()
        windowManager = mockk(relaxed = true)
        val service = controller.get()
        service.overlayPermissionChecker = mockk<OverlayPermissionChecker> {
            every { canDrawOverlays() } returns true
        }
        service.windowManager = windowManager
        service.positionConfig = OverlayPositionConfig(firstReadTimeoutMillis = 200L)

        // 1.0 means "flush against the far edge", whatever the screen turns out to be.
        service.positionRepository = mockk<OverlayPositionRepository> {
            every { position } returns flowOf(OverlayPositionFraction(x = 1f, y = 0f))
        }

        every { windowManager.currentWindowMetrics } returns metricsOf(PORTRAIT_W, PORTRAIT_H)

        service.onStartCommand(null, 0, 1)
        idleMainLooper()
        testScheduler.advanceUntilIdle()
        idleMainLooper()

        val paramsSlot = slot<WindowManager.LayoutParams>()
        verify(exactly = 1) { windowManager.addView(any<View>(), capture(paramsSlot)) }
        val params = paramsSlot.captured
        assertEquals(
            "in portrait, a fraction of 1.0 is the right edge",
            PORTRAIT_W - petSizeFor(PORTRAIT_W, PORTRAIT_H),
            params.x,
        )

        // Rotate: the same window, a wider screen, and the stored fraction untouched.
        every { windowManager.currentWindowMetrics } returns metricsOf(LANDSCAPE_W, LANDSCAPE_H)
        service.onConfigurationChanged(Configuration())
        idleMainLooper()

        // The whole point of storing a fraction. Before the fix this stayed at PORTRAIT_W - SIZE,
        // which the widened bounds happily accept, leaving the pet stranded mid-screen.
        assertEquals(
            "after rotating, a fraction of 1.0 must be the NEW right edge, not the old one",
            LANDSCAPE_W - petSizeFor(LANDSCAPE_W, LANDSCAPE_H),
            params.x,
        )
    }

    /**
     * Mirrors the service's own rule: a quarter of the shorter edge, capped by the ceiling. The pet
     * is no longer a fixed size, so an expectation written as a constant would only hold on the one
     * screen it was written for — and rotation changes which edge is shorter.
     */
    private fun petSizeFor(widthPx: Int, heightPx: Int): Int =
        (minOf(widthPx, heightPx) / 4).coerceAtMost(OverlayWindowParams.MAX_SIZE_PX)

    private companion object {
        const val PORTRAIT_W = 1220
        const val PORTRAIT_H = 2712
        const val LANDSCAPE_W = 2712
        const val LANDSCAPE_H = 1220
    }
}
