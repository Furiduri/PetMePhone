package com.gcatcode.petmephone.feature.overlay.quickmenu

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuAnchor
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuEvent
import com.gcatcode.petmephone.core.domain.overlay.ScreenInsets
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private val ANCHOR = QuickMenuAnchor(xPx = 100, yPx = 100, sizePx = 220)
private val NO_INSETS = ScreenInsets(left = 0, top = 0, right = 0, bottom = 0)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickMenuWindowControllerTest {

    private fun newController(windowManager: WindowManager): QuickMenuWindowController =
        QuickMenuWindowController(
            context = RuntimeEnvironment.getApplication(),
            windowManager = windowManager,
            cardWidthPx = 600,
            maxCardHeightPx = 400,
            gapPx = 8,
            screenBoundsPx = { 1080 to 2400 },
            screenInsets = { NO_INSETS },
        )

    @Test
    fun `PetTapped from Closed adds the window exactly once`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = newController(windowManager)

        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))

        verify(exactly = 1) { windowManager.addView(any<View>(), any()) }
    }

    @Test
    fun `outside touch dismissal calls removeView and clears the view field`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = newController(windowManager)
        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))

        controller.onEvent(QuickMenuEvent.OutsideTouch)

        verify(exactly = 1) { windowManager.removeView(any()) }
        // Removing again (a second OutsideTouch from an already-Closed state) must not call
        // removeView a second time — proves the view field was actually cleared, not just that
        // removeView happened to run once by coincidence of a single onEvent call.
        controller.onEvent(QuickMenuEvent.OutsideTouch)
        verify(exactly = 1) { windowManager.removeView(any()) }
    }

    @Test
    fun `pet tap dismissal calls removeView`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = newController(windowManager)
        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))

        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))

        verify(exactly = 1) { windowManager.removeView(any()) }
    }

    @Test
    fun `pet drag dismissal calls removeView`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = newController(windowManager)
        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))

        controller.onEvent(QuickMenuEvent.PetDragged)

        verify(exactly = 1) { windowManager.removeView(any()) }
    }

    @Test
    fun `screen off dismissal calls removeView`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = newController(windowManager)
        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))

        controller.onEvent(QuickMenuEvent.ScreenOff)

        verify(exactly = 1) { windowManager.removeView(any()) }
    }

    @Test
    fun `app launched dismissal calls removeView`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = newController(windowManager)
        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))

        controller.onEvent(QuickMenuEvent.AppLaunched)

        verify(exactly = 1) { windowManager.removeView(any()) }
    }

    @Test
    fun `destroy on an open card calls removeView`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = newController(windowManager)
        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))

        controller.destroy()

        verify(exactly = 1) { windowManager.removeView(any()) }
    }

    @Test
    fun `destroy while already closed never calls removeView`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = newController(windowManager)

        controller.destroy()

        verify(exactly = 0) { windowManager.removeView(any()) }
    }

    @Test
    fun `an event that stays Closed never calls addView or removeView`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = newController(windowManager)

        controller.onEvent(QuickMenuEvent.PetDragged)

        verify(exactly = 0) { windowManager.addView(any(), any()) }
        verify(exactly = 0) { windowManager.removeView(any()) }
    }

    @Test
    fun `addView failure rolls the state back so a following dismissal event does not call removeView`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        every { windowManager.addView(any(), any()) } throws WindowManager.BadTokenException("no token")
        val controller = newController(windowManager)

        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))
        controller.onEvent(QuickMenuEvent.OutsideTouch)

        verify(exactly = 0) { windowManager.removeView(any()) }
    }

    @Test
    fun `launchApp starts an explicit intent naming this app's own launcher component with NEW_TASK`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val context = RuntimeEnvironment.getApplication()

        // Robolectric's test manifest declares no launcher activity for this application (unlike
        // the real :app manifest), so PackageManager.getLaunchIntentForPackage would otherwise
        // resolve nothing to assert against. Register one against the shadow package manager,
        // exactly like the real launcher component this app ships, so the assertion below proves
        // the intent PackageManager itself resolved — not a hand-built one this test fabricated.
        val launcherComponent = ComponentName(context.packageName, "${context.packageName}.MainActivity")
        val shadowPackageManager = org.robolectric.Shadows.shadowOf(context.packageManager)
        shadowPackageManager.addActivityIfNotPresent(launcherComponent)
        shadowPackageManager.addIntentFilterForActivity(
            launcherComponent,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )

        val controller = newController(windowManager)
        controller.launchApp()

        val shadowApplication = org.robolectric.Shadows.shadowOf(context as android.app.Application)
        val started = shadowApplication.nextStartedActivity
        assertTrue("expected launchApp to start an activity", started != null)
        assertEquals(launcherComponent, started?.component)
        assertTrue(
            "expected FLAG_ACTIVITY_NEW_TASK",
            ((started?.flags ?: 0) and Intent.FLAG_ACTIVITY_NEW_TASK) != 0,
        )
    }
}
