package com.gcatcode.petmephone.feature.overlay.quickmenu

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuAnchor
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuEvent
import com.gcatcode.petmephone.core.domain.overlay.ScreenInsets
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real `WindowManager`, real permission (granted via `adb shell appops set <pkg>
 * SYSTEM_ALERT_WINDOW allow` before this suite runs, per the module's connected-test evidence
 * requirement). Exercises `overlay-quick-menu`'s machine-verifiable scenarios end to end on
 * `emulator-5554` rather than through a mocked `WindowManager`.
 *
 * Every `WindowManager` call and every [QuickMenuWindowController.onEvent] call runs via
 * [runOnMain] — `ViewRootImpl` requires a `Looper.prepare()`'d thread, which the instrumentation
 * runner's own test thread is not.
 *
 * **Not covered here, and why**: the "launch button starts this app's launcher `Activity`"
 * scenario needs a real, installed launcher component under this exact package. This module's own
 * `androidTest` target has no launcher `Activity` in its manifest (only `:app` does, and
 * `:feature:overlay` cannot depend on `:app`), so `PackageManager.getLaunchIntentForPackage`
 * resolves nothing to launch from *this* isolated instrumentation — a property of the module
 * boundary, not missed work. [QuickMenuWindowControllerTest]'s Robolectric test already exercises
 * `launchApp()`'s real production code path end to end (same method, not a reimplementation) and
 * asserts the exact explicit-intent-plus-`NEW_TASK` shape the threat matrix requires. The
 * component this method resolves against in production is real once `:app` installs it and this
 * controller runs inside `PetOverlayService`'s own process, where `context.packageName` is
 * `:app`'s package and does have a launcher `Activity`.
 */
@RunWith(AndroidJUnit4::class)
class QuickMenuWindowLifecycleTest {

    private lateinit var windowManager: WindowManager
    private lateinit var petParams: WindowManager.LayoutParams
    private lateinit var petView: View
    private lateinit var controller: QuickMenuWindowController

    private val anchor = QuickMenuAnchor(xPx = 100, yPx = 100, sizePx = 220)

    private fun runOnMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        windowManager = context.getSystemService(WindowManager::class.java)

        // Stands in for the pet window: same flag/type shape as OverlayWindowParams, added
        // through the same real WindowManager the card's controller uses, so "the pet window's
        // LayoutParams are unchanged" is checked against a genuinely separate, live window — not
        // a value the test just happens to never touch.
        petView = View(context)
        petParams = WindowManager.LayoutParams(
            220,
            220,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        controller = QuickMenuWindowController(
            context = context,
            windowManager = windowManager,
            cardWidthPx = 400,
            maxCardHeightPx = 300,
            gapPx = 8,
            screenBoundsPx = { 1080 to 2400 },
            screenInsets = { ScreenInsets(left = 0, top = 0, right = 0, bottom = 0) },
        )

        runOnMain { windowManager.addView(petView, petParams) }
    }

    @After
    fun tearDown() {
        runOnMain {
            controller.destroy()
            runCatching { windowManager.removeView(petView) }
        }
    }

    private fun snapshotOf(params: WindowManager.LayoutParams) =
        listOf(params.x, params.y, params.width, params.height, params.type, params.flags, params.format, params.gravity)

    @Test
    fun tappingOpensASecondWindowAndLeavesThePetWindowUnchanged() {
        val before = snapshotOf(petParams)

        runOnMain { controller.onEvent(QuickMenuEvent.PetTapped(anchor)) }

        assertNotNull("expected the card window to have opened", cardViewOrNull())
        assertEquals("pet window's LayoutParams must be bit-for-bit unchanged", before, snapshotOf(petParams))
    }

    @Test
    fun outsideTouchDismissesTheCard() {
        runOnMain { controller.onEvent(QuickMenuEvent.PetTapped(anchor)) }
        val cardHost = cardViewOrNull()
        assertNotNull("expected the card window to be open before dismissing it", cardHost)

        runOnMain {
            cardHost!!.dispatchTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_OUTSIDE, -1f, -1f, 0))
        }

        assertNull("expected the card window to be removed after ACTION_OUTSIDE", cardViewOrNull())
    }

    @Test
    fun petTapWhileOpenDismissesTheCard() {
        runOnMain { controller.onEvent(QuickMenuEvent.PetTapped(anchor)) }
        assertNotNull(cardViewOrNull())

        runOnMain { controller.onEvent(QuickMenuEvent.PetTapped(anchor)) }

        assertNull("expected the second PetTapped to close the card", cardViewOrNull())
    }

    @Test
    fun petDragWhileOpenDismissesTheCard() {
        runOnMain { controller.onEvent(QuickMenuEvent.PetTapped(anchor)) }
        assertNotNull(cardViewOrNull())

        runOnMain { controller.onEvent(QuickMenuEvent.PetDragged) }

        assertNull("expected PetDragged to close the card", cardViewOrNull())
    }

    /**
     * Task 4.13: the card window is added with the expected focus flags and `softInputMode`, on
     * the real `WindowManager` — the closest available check to "at `addView` time" from outside
     * the class, since the params object itself is not exposed.
     */
    @Test
    fun cardWindowIsAddedFocusableWithAdjustResize() {
        runOnMain { controller.onEvent(QuickMenuEvent.PetTapped(anchor)) }

        val cardHost = cardViewOrNull()
        assertNotNull("expected the card window to be open", cardHost)
        val params = cardHost!!.layoutParams as WindowManager.LayoutParams

        assertEquals(
            "expected FLAG_NOT_FOCUSABLE to be absent — the card is focusable",
            0,
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        )
        assertEquals(
            "expected SOFT_INPUT_ADJUST_RESIZE",
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            params.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        )
    }

    /**
     * Task 4.13: `destroy()` leaves no view attached, and the pet window's `LayoutParams` are
     * bit-for-bit unchanged before and after the card's full open/dismiss lifecycle.
     */
    @Test
    fun destroyLeavesNoViewAttachedAndPetWindowUnchanged() {
        val before = snapshotOf(petParams)

        runOnMain { controller.onEvent(QuickMenuEvent.PetTapped(anchor)) }
        assertNotNull("expected the card window to be open before destroy()", cardViewOrNull())

        runOnMain { controller.destroy() }

        assertNull("expected destroy() to leave no card view attached", cardViewOrNull())
        assertEquals(
            "pet window's LayoutParams must be bit-for-bit unchanged after the card's full lifecycle",
            before,
            snapshotOf(petParams),
        )
    }

    /**
     * The controller keeps its own [View] field private, so this reflects it out for assertions
     * only — the same pattern the class already documents ("owns add/remove" is an invariant this
     * test verifies from outside, not a reason to expose a production getter nothing else needs).
     */
    private fun cardViewOrNull(): View? {
        val field = QuickMenuWindowController::class.java.getDeclaredField("view")
        field.isAccessible = true
        return field.get(controller) as View?
    }
}
