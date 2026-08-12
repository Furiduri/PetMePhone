package com.gcatcode.petmephone.feature.overlay.quickmenu

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuAnchor
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuEvent
import com.gcatcode.petmephone.core.domain.overlay.ScreenInsets
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tapping the pet while the card is open must CLOSE it, and leave it closed.
 *
 * On a device it reopened instead. The card window carries `FLAG_WATCH_OUTSIDE_TOUCH`, so one
 * finger produces two events: `OutsideTouch` closes the card, then the pet's own tap listener
 * reports `PetTapped`, which reopens it from `Closed`.
 *
 * Every event was individually correct, so no test on the pure `reduce` could catch it — and none
 * did. The defect lived only in their coincidence, which is why this test drives the controller
 * with both events from one gesture rather than asserting on the reducer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickMenuTapToggleTest {

    private lateinit var context: Context
    private lateinit var windowManager: WindowManager
    private var now: Long = 1_000L

    private val anchor = QuickMenuAnchor(xPx = 0, yPx = 1800, sizePx = 220)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private fun controller() = QuickMenuWindowController(
        context = context,
        windowManager = windowManager,
        cardWidthPx = 320,
        maxCardHeightPx = 400,
        gapPx = 16,
        screenBoundsPx = { 1080 to 2280 },
        screenInsets = { ScreenInsets(0, 0, 0, 0) },
        nowMs = { now },
    )

    @Test
    fun `one tap on the pet while open closes the card and does not reopen it`() {
        val controller = controller()
        controller.onEvent(QuickMenuEvent.PetTapped(anchor))
        assertEquals(true, controller.isOpen)

        // The two events one physical tap produces, in the order the framework delivers them.
        now += 5
        controller.onEvent(QuickMenuEvent.OutsideTouch)
        now += 5
        controller.onEvent(QuickMenuEvent.PetTapped(anchor))

        // Fails if the PetTapped tail of the gesture is allowed through: the card reopens and this
        // reads true, which is exactly the behaviour reported from the device.
        assertEquals(false, controller.isOpen)
    }

    @Test
    fun `a genuine later tap still reopens the card`() {
        val controller = controller()
        controller.onEvent(QuickMenuEvent.PetTapped(anchor))
        now += 5
        controller.onEvent(QuickMenuEvent.OutsideTouch)

        // Well past the same-gesture window: a real second tap by a person.
        now += 5_000
        controller.onEvent(QuickMenuEvent.PetTapped(anchor))

        // Fails if the suppression were unbounded, which would make the pet un-tappable after any
        // outside dismissal — a worse bug than the one being fixed.
        assertEquals(true, controller.isOpen)
    }
}
