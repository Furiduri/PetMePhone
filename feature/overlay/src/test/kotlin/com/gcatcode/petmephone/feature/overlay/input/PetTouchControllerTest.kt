package com.gcatcode.petmephone.feature.overlay.input

import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.gcatcode.petmephone.core.domain.overlay.DragStateRepository
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val SLOP_PX = 24
private const val RENDER_SIZE_PX = 220
private const val SCREEN_WIDTH_PX = 1080
private const val SCREEN_HEIGHT_PX = 2400

// See ComposeOverlayHostTest: Robolectric 4.16.1 ships no SDK 37 shadows yet.
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PetTouchControllerTest {

    private class FakeFrameScheduler : FrameScheduler {
        var postCount = 0
        private var scheduled: (() -> Unit)? = null

        override fun postFrameCallback(callback: () -> Unit) {
            postCount++
            scheduled = callback
        }

        override fun removeFrameCallback(callback: () -> Unit) {
            if (scheduled === callback) scheduled = null
        }

        fun runScheduledFrame() {
            scheduled?.invoke()
        }

        fun hasScheduledFrame() = scheduled != null
    }

    private class FakeSnapAnimator : SnapAnimator {
        var invoked = false
        override suspend fun animate(fromX: Float, toX: Float, onUpdate: (Float) -> Unit) {
            invoked = true
            onUpdate(toX)
        }
    }

    private class FakeDragStateRepository : DragStateRepository {
        private val flow = MutableStateFlow(false)
        override val isDragging: StateFlow<Boolean> = flow
        override fun set(dragging: Boolean) {
            flow.value = dragging
        }
    }

    private fun params(x: Int = 100, y: Int = 100) = WindowManager.LayoutParams().apply {
        this.x = x
        this.y = y
    }

    private fun downEvent(x: Float, y: Float) =
        MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, x, y, 0)

    private fun moveEvent(x: Float, y: Float) =
        MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_MOVE, x, y, 0)

    private fun upEvent(x: Float, y: Float) =
        MotionEvent.obtain(0L, 20L, MotionEvent.ACTION_UP, x, y, 0)

    private fun newController(
        params: WindowManager.LayoutParams,
        windowManager: WindowManager,
        dragStateRepository: DragStateRepository = FakeDragStateRepository(),
        frameScheduler: FakeFrameScheduler = FakeFrameScheduler(),
        snapAnimator: SnapAnimator = FakeSnapAnimator(),
        view: View = View(RuntimeEnvironment.getApplication()),
        onTap: (OverlayAnchor) -> Unit = {},
        onSettled: (Int, Int) -> Unit = { _, _ -> },
    ): PetTouchController {
        return PetTouchController(
            context = RuntimeEnvironment.getApplication(),
            windowManager = windowManager,
            view = view,
            params = params,
            renderSizePx = RENDER_SIZE_PX,
            dragStateRepository = dragStateRepository,
            frameScheduler = frameScheduler,
            snapAnimator = snapAnimator,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            onTap = OverlayTapListener { anchor -> onTap(anchor) },
            screenWidthPx = { SCREEN_WIDTH_PX },
            screenHeightPx = { SCREEN_HEIGHT_PX },
            navigationBarInsetBottomPx = { 0 },
            onSettled = onSettled,
        )
    }

    @Test
    fun `sub-slop touch leaves params untouched and fires onTap exactly once`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val params = params(x = 100, y = 100)
        var tapCount = 0
        var tappedAnchor: OverlayAnchor? = null
        val view = View(RuntimeEnvironment.getApplication())
        val controller = newController(
            params,
            windowManager,
            view = view,
            onTap = { anchor -> tapCount++; tappedAnchor = anchor },
        )

        controller.onTouch(view, downEvent(500f, 500f))
        controller.onTouch(view, moveEvent(505f, 503f)) // well below SLOP_PX
        controller.onTouch(view, upEvent(505f, 503f))

        assertEquals(100, params.x)
        assertEquals(100, params.y)
        assertEquals(1, tapCount)
        assertEquals(100, tappedAnchor?.xPx)
        assertEquals(100, tappedAnchor?.yPx)
        verify(exactly = 0) { windowManager.updateViewLayout(any(), any()) }
    }

    @Test
    fun `past-slop movement follows the finger once the scheduled frame runs`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val params = params(x = 100, y = 100)
        val frameScheduler = FakeFrameScheduler()
        val dragStateRepository = FakeDragStateRepository()
        val view = View(RuntimeEnvironment.getApplication())
        val controller = newController(params, windowManager, dragStateRepository, frameScheduler, view = view)

        controller.onTouch(view, downEvent(500f, 500f))
        controller.onTouch(view, moveEvent(600f, 520f)) // dx=100, dy=20, well past SLOP_PX
        assertTrue(dragStateRepository.isDragging.value)
        assertEquals(100, params.x) // not applied yet - only at the scheduled frame

        frameScheduler.runScheduledFrame()

        assertEquals(200, params.x) // 100 + dx(100)
        assertEquals(120, params.y) // 100 + dy(20)
        verify(exactly = 1) { windowManager.updateViewLayout(view, params) }
    }

    @Test
    fun `rapid ACTION_MOVE events schedule at most one frame callback`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val params = params()
        val frameScheduler = FakeFrameScheduler()
        val view = View(RuntimeEnvironment.getApplication())
        val controller = newController(params, windowManager, frameScheduler = frameScheduler, view = view)

        controller.onTouch(view, downEvent(500f, 500f))
        // Simulate ACTION_MOVE arriving far above the display refresh rate, before any frame runs.
        repeat(20) { i -> controller.onTouch(view, moveEvent(500f + i, 500f + i)) }

        assertEquals(1, frameScheduler.postCount)

        frameScheduler.runScheduledFrame()
        verify(exactly = 1) { windowManager.updateViewLayout(view, params) }
    }

    @Test
    fun `cancel removes the pending frame callback and cancels the snap animation coroutine`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val params = params()
        val frameScheduler = FakeFrameScheduler()
        val view = View(RuntimeEnvironment.getApplication())
        val controller = newController(params, windowManager, frameScheduler = frameScheduler, view = view)

        controller.onTouch(view, downEvent(500f, 500f))
        controller.onTouch(view, moveEvent(600f, 500f))
        assertTrue(frameScheduler.hasScheduledFrame())

        controller.cancel()

        assertFalse(frameScheduler.hasScheduledFrame())
        // No crash from a callback holding a dead view: running a frame after cancel is a no-op
        // because the scheduler no longer holds the callback reference.
        frameScheduler.runScheduledFrame()
    }

    @Test
    fun `release after a drag closer to the left edge snaps left`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val params = params(x = 100, y = 300) // near the left edge of a 1080px-wide screen
        val frameScheduler = FakeFrameScheduler()
        val snapAnimator = FakeSnapAnimator()
        val dragStateRepository = FakeDragStateRepository()
        var settledX: Int? = null
        var settledY: Int? = null
        val view = View(RuntimeEnvironment.getApplication())
        val controller = newController(
            params,
            windowManager,
            dragStateRepository,
            frameScheduler,
            snapAnimator,
            view = view,
            onSettled = { x, y -> settledX = x; settledY = y },
        )

        controller.onTouch(view, downEvent(500f, 500f))
        controller.onTouch(view, moveEvent(500f, 500f + 50f)) // exceeds slop vertically only
        frameScheduler.runScheduledFrame()
        controller.onTouch(view, upEvent(500f, 550f))

        assertTrue(snapAnimator.invoked)
        assertEquals(0, params.x) // snapped to the left edge
        assertFalse(dragStateRepository.isDragging.value)
        assertEquals(0, settledX)
        assertEquals(350, settledY) // 300 + dy(50), vertical is frozen at the release value
    }

    @Test
    fun `release after a drag closer to the right edge snaps right`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val params = params(x = SCREEN_WIDTH_PX - RENDER_SIZE_PX - 50, y = 300)
        val frameScheduler = FakeFrameScheduler()
        val snapAnimator = FakeSnapAnimator()
        val controller = newController(params, windowManager, frameScheduler = frameScheduler, snapAnimator = snapAnimator)
        val view = View(RuntimeEnvironment.getApplication())

        controller.onTouch(view, downEvent(900f, 500f))
        controller.onTouch(view, moveEvent(940f, 500f)) // exceeds slop horizontally toward the right
        frameScheduler.runScheduledFrame()
        controller.onTouch(view, upEvent(940f, 500f))

        assertEquals(SCREEN_WIDTH_PX - RENDER_SIZE_PX, params.x)
    }

    @Test
    fun `vertical coordinate at snap completion equals the coordinate at release`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val params = params(x = 100, y = 200)
        val frameScheduler = FakeFrameScheduler()
        var settledY: Int? = null
        val controller = newController(
            params,
            windowManager,
            frameScheduler = frameScheduler,
            onSettled = { _, y -> settledY = y },
        )
        val view = View(RuntimeEnvironment.getApplication())

        controller.onTouch(view, downEvent(500f, 500f))
        controller.onTouch(view, moveEvent(500f, 620f)) // dy = 120, well past slop
        frameScheduler.runScheduledFrame()
        controller.onTouch(view, upEvent(500f, 620f))

        assertEquals(320, settledY) // 200 + dy(120), unchanged by the horizontal snap
    }
}
