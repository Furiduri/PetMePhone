package com.gcatcode.petmephone.feature.overlay.quickmenu

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuAnchor
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuEvent
import com.gcatcode.petmephone.core.domain.overlay.ScreenInsets
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `overlay-quick-menu`'s "pressing back while the card is open does not dismiss it" scenario
 * (design decision 7 — no `OnBackPressedDispatcher`, no `KEYCODE_BACK` interception; the card
 * window is non-focusable, so it structurally cannot receive a back key even if one were sent).
 * [NoBackGestureCodeTest] already proves no such code exists source-side; this proves the
 * *observable behaviour* on a real device: the device back key does not remove the card window.
 */
@RunWith(AndroidJUnit4::class)
class QuickMenuBackGestureDoesNotDismissTest {

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

    private fun cardViewOrNull(): View? {
        val field = QuickMenuWindowController::class.java.getDeclaredField("view")
        field.isAccessible = true
        return field.get(controller) as View?
    }

    @Test
    fun devicesBackKeyDoesNotDismissTheOpenCard() {
        runOnMain { controller.onEvent(QuickMenuEvent.PetTapped(anchor)) }
        assertNotNull("expected the card window to be open before pressing back", cardViewOrNull())

        // Failing input this guards: a wired OnBackPressedDispatcher (or any KEYCODE_BACK
        // interception) reacting to this key press would remove the card here, making the
        // following assertNotNull fail.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

        assertNotNull(
            "the card must remain open after a device back press — dismissal is only via " +
                "ACTION_OUTSIDE or the pet-tap/drag fallback",
            cardViewOrNull(),
        )
    }
}
