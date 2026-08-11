package com.gcatcode.petmephone.feature.overlay.input

import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.gcatcode.petmephone.core.domain.overlay.DragStateRepository
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionFraction
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionRepository
import com.gcatcode.petmephone.feature.overlay.position.PositionWriter
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
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

    private class FakeOverlayPositionRepository : OverlayPositionRepository {
        var savedFraction: OverlayPositionFraction? = null
        var saveCount = 0
        override val position: kotlinx.coroutines.flow.Flow<OverlayPositionFraction?> = flowOf(null)
        override val normalizations: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun save(position: OverlayPositionFraction) {
            savedFraction = position
            saveCount++
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
        positionWriter: PositionWriter = PositionWriter(
            FakeOverlayPositionRepository(),
            CoroutineScope(UnconfinedTestDispatcher()),
        ),
    ): PetTouchController {
        return PetTouchController(
            context = RuntimeEnvironment.getApplication(),
            windowManager = windowManager,
            view = view,
            params = params,
            renderSizePx = RENDER_SIZE_PX,
            dragStateRepository = dragStateRepository,
            positionWriter = positionWriter,
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

    @Test
    fun `snap settle writes exactly one fraction, never during ACTION_MOVE or the snap animation`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val params = params(x = 100, y = 300)
        val frameScheduler = FakeFrameScheduler()
        val fakeRepository = FakeOverlayPositionRepository()
        val positionWriter = PositionWriter(fakeRepository, CoroutineScope(UnconfinedTestDispatcher()))
        val view = View(RuntimeEnvironment.getApplication())
        val controller = newController(
            params,
            windowManager,
            frameScheduler = frameScheduler,
            view = view,
            positionWriter = positionWriter,
        )

        controller.onTouch(view, downEvent(500f, 500f))
        assertEquals(0, fakeRepository.saveCount) // DOWN never writes

        controller.onTouch(view, moveEvent(500f, 550f)) // past slop
        assertEquals(0, fakeRepository.saveCount) // ACTION_MOVE never writes

        frameScheduler.runScheduledFrame()
        assertEquals(0, fakeRepository.saveCount) // an intermediate animation frame never writes

        controller.onTouch(view, upEvent(500f, 550f))

        assertEquals(1, fakeRepository.saveCount)
        assertEquals(0f, fakeRepository.savedFraction?.x)
    }

    /**
     * Regression: a frame callback still in flight at release used to overwrite the clamp.
     *
     * `ACTION_MOVE` stores an unclamped `pendingY` and schedules a frame; `snap()` clamps
     * `params.y` into `0..maxY`. Because `snap()` did not cancel the scheduled frame, that
     * callback fired afterwards and restored the unclamped value, so a drag above the top edge
     * persisted a negative fraction. `validOrNull` then rejected the whole pair on the next read
     * and the pet jumped to its resting corner — observed on a real device as "I drop it top-left
     * and it flies to the bottom-right".
     *
     * Every other test in this class drains the frame queue before releasing, which is exactly why
     * none of them could catch this. This one deliberately does not.
     */
    @Test
    fun `a frame callback in flight at release cannot resurrect an unclamped y`() {
        val windowManager = mockk<WindowManager>(relaxed = true)
        val params = params(x = 300, y = 40)
        val frameScheduler = FakeFrameScheduler()
        val fakeRepository = FakeOverlayPositionRepository()
        val controller = newController(
            params,
            windowManager,
            frameScheduler = frameScheduler,
            positionWriter = PositionWriter(fakeRepository, CoroutineScope(UnconfinedTestDispatcher())),
        )
        val view = View(RuntimeEnvironment.getApplication())

        // Drag well above the top edge: pendingY becomes negative.
        controller.onTouch(view, downEvent(400f, 500f))
        controller.onTouch(view, moveEvent(400f, 400f))

        // Release WITHOUT draining the frame queue, the way a real device can.
        controller.onTouch(view, upEvent(400f, 400f))

        // Any callback still scheduled must not be able to undo the clamp.
        frameScheduler.runScheduledFrame()

        assertTrue("params.y was left negative: ${params.y}", params.y >= 0)
        val saved = fakeRepository.savedFraction
        assertTrue("no position was persisted", saved != null)
        assertTrue("persisted a negative y fraction: ${saved?.y}", (saved?.y ?: -1f) >= 0f)
    }
}
