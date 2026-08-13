package com.gcatcode.petmephone.feature.overlay.quickmenu

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuAnchor
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuContent
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuEvent
import com.gcatcode.petmephone.core.domain.overlay.ScreenInsets
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun newController(
        windowManager: WindowManager,
        nowMs: () -> Long = System::currentTimeMillis,
    ): QuickMenuWindowController =
        QuickMenuWindowController(
            context = RuntimeEnvironment.getApplication(),
            windowManager = windowManager,
            cardWidthPx = 600,
            maxCardHeightPx = 400,
            gapPx = 8,
            screenBoundsPx = { 1080 to 2400 },
            screenInsets = { NO_INSETS },
            nowMs = nowMs,
        )

    /** A clock that jumps well past the production `SAME_GESTURE_WINDOW_MS` between reads, so a `PetTapped`
     *  reopen right after an `OutsideTouch` close in a restoration test is a genuine second tap,
     *  not the one-finger ACTION_OUTSIDE-then-PetTapped coincidence the production suppression
     *  exists for (see [QuickMenuWindowController.onEvent]'s kdoc). Using the real clock here
     *  would make those two events land within the same millisecond and silently suppress the
     *  reopen, which would let a broken restoration pass by accident — the window never actually
     *  reopens, but `content` was never touched either, so the assertion would look green. */
    private fun advancingClock(stepMs: Long = 10_000L): () -> Long {
        var current = 0L
        return {
            current += stepMs
            current
        }
    }

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
    fun `focusability is fixed at construction - updateViewLayout is never called across a full open-close-reopen cycle`() {
        // Design decision 2: a flag toggled after addView has an interval where the wrong value
        // is live, and that interval is exactly #18's "left focusable after dismissal" hazard.
        // Focusability must be a property of the LayoutParams passed to addView only.
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = newController(windowManager)

        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))
        controller.onEvent(QuickMenuEvent.OutsideTouch)
        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))
        controller.destroy()

        verify(exactly = 0) { windowManager.updateViewLayout(any(), any()) }
    }

    // --- Restoration on reopen (design decisions 5, 5b; task 3.1) ---------------------------
    //
    // Each dismissal path gets its own test rather than one parameterised case, per the
    // orchestrator's explicit instruction: a parameterised loop can pass while one path is
    // silently broken (for instance if a future change special-cases one event's handling and
    // forgets the others). Six paths, six tests, one assertion each.

    private fun controllerLeftOnTaskInput(
        windowManager: WindowManager,
        nowMs: () -> Long = System::currentTimeMillis,
    ): QuickMenuWindowController {
        val controller = newController(windowManager, nowMs)
        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))
        controller.onContentChange(QuickMenuContent.TaskInput)
        return controller
    }

    @Test
    fun `dismissal by outside touch reopens on the content it was left on`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        // Advancing clock: an OutsideTouch close followed by a PetTapped reopen is exactly the
        // one-finger coincidence SAME_GESTURE_WINDOW_MS exists to suppress, so a real clock here
        // would make this test pass even if restoration were broken (see advancingClock's kdoc).
        val controller = controllerLeftOnTaskInput(windowManager, advancingClock())

        controller.onEvent(QuickMenuEvent.OutsideTouch)
        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))

        assertEquals(QuickMenuContent.TaskInput, controller.content)
    }

    @Test
    fun `dismissal by pet tap reopens on the content it was left on`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = controllerLeftOnTaskInput(windowManager)

        // Pet-tap dismissal and pet-tap reopen are the same event; the SAME_GESTURE_WINDOW_MS
        // suppression only fires after an OutsideTouch close, so back-to-back PetTapped events
        // dismiss then reopen exactly as intended here.
        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))
        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))

        assertEquals(QuickMenuContent.TaskInput, controller.content)
    }

    @Test
    fun `dismissal by pet drag reopens on the content it was left on`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = controllerLeftOnTaskInput(windowManager)

        controller.onEvent(QuickMenuEvent.PetDragged)
        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))

        assertEquals(QuickMenuContent.TaskInput, controller.content)
    }

    @Test
    fun `dismissal by back reopens on the content it was left on`() {
        // Back can only ever close the card from Dashboard (design decision 7 — a press from
        // TaskInput unwinds to Dashboard first, level 2, and the window stays open; only a
        // *second* press, now from Dashboard, reaches level 3 and closes it). So a back-triggered
        // dismissal always leaves — and therefore always restores — the Dashboard content. This
        // is the honest shape of "dismiss via back reopens on the content it was left on": what
        // it was left on is Dashboard, by construction of the back-ordering rule itself.
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = controllerLeftOnTaskInput(windowManager)

        controller.onEvent(QuickMenuEvent.BackPressed) // level 2: TaskInput -> Dashboard, stays open
        assertTrue("expected the card to remain open after the first back press", controller.isOpen)
        controller.onEvent(QuickMenuEvent.BackPressed) // level 3: Dashboard -> closes
        assertFalse("expected the card to be closed after the second back press", controller.isOpen)

        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))

        assertEquals(QuickMenuContent.Dashboard, controller.content)
    }

    @Test
    fun `dismissal by AppLaunched reopens on the content it was left on`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = controllerLeftOnTaskInput(windowManager)

        controller.onEvent(QuickMenuEvent.AppLaunched)
        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))

        assertEquals(QuickMenuContent.TaskInput, controller.content)
    }

    @Test
    fun `dismissal by ScreenOff reopens on the content it was left on`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = controllerLeftOnTaskInput(windowManager)

        controller.onEvent(QuickMenuEvent.ScreenOff)
        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))

        assertEquals(QuickMenuContent.TaskInput, controller.content)
    }

    @Test
    fun `dismissal from the dashboard reopens on the dashboard`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = newController(windowManager, advancingClock())
        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))

        controller.onEvent(QuickMenuEvent.OutsideTouch)
        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))

        assertEquals(QuickMenuContent.Dashboard, controller.content)
    }

    @Test
    fun `a fresh controller opens on the dashboard`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = newController(windowManager)

        assertEquals(QuickMenuContent.Dashboard, controller.content)
    }

    @Test
    fun `destroy then reopen yields the dashboard`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = controllerLeftOnTaskInput(windowManager)

        controller.destroy()
        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))

        assertEquals(QuickMenuContent.Dashboard, controller.content)
    }

    // --- Back application (design decision 7; task 3.3) ---------------------------------------

    @Test
    fun `BackPressed from TaskInput swaps content to Dashboard without closing the window`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = controllerLeftOnTaskInput(windowManager)

        controller.onEvent(QuickMenuEvent.BackPressed)

        assertEquals(QuickMenuContent.Dashboard, controller.content)
        assertTrue("expected the card to remain open", controller.isOpen)
        verify(exactly = 0) { windowManager.removeView(any()) }
    }

    @Test
    fun `BackPressed from Dashboard forwards into reduce and closes the card`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = newController(windowManager)
        controller.onEvent(QuickMenuEvent.PetTapped(ANCHOR))

        controller.onEvent(QuickMenuEvent.BackPressed)

        assertFalse("expected the card to be closed", controller.isOpen)
        verify(exactly = 1) { windowManager.removeView(any()) }
    }

    @Test
    fun `BackPressed while closed is a no-op`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val controller = newController(windowManager)

        controller.onEvent(QuickMenuEvent.BackPressed)

        assertFalse(controller.isOpen)
        verify(exactly = 0) { windowManager.addView(any<View>(), any()) }
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
