package com.gcatcode.petmephone.feature.overlay.input

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import com.gcatcode.petmephone.core.domain.overlay.DragStateRepository
import com.gcatcode.petmephone.core.domain.overlay.OverlayPosition
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionFraction
import com.gcatcode.petmephone.core.domain.overlay.ScreenEdge
import com.gcatcode.petmephone.core.domain.overlay.exceedsSlop
import com.gcatcode.petmephone.core.domain.overlay.nearestEdge
import com.gcatcode.petmephone.feature.overlay.position.PositionWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Single [View.OnTouchListener], attached once to [com.gcatcode.petmephone.feature.overlay.ui.ComposeOverlayHost]
 * by the owning service (design decision 5 — never Compose `pointerInput`/`detectDragGestures`,
 * because the window moves under the finger and feeds Compose-local coordinates back into
 * themselves; `MotionEvent.rawX/rawY` are screen-absolute and immune to that feedback loop).
 *
 * `updateViewLayout` is reachable from exactly one call site: [frameCallback], scheduled at most
 * once per rendered frame via [frameScheduler]. `ACTION_MOVE` never calls it directly.
 */
class PetTouchController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val view: View,
    private val params: WindowManager.LayoutParams,
    private val renderSizePx: Int,
    private val dragStateRepository: DragStateRepository,
    private val positionWriter: PositionWriter,
    private val frameScheduler: FrameScheduler,
    private val snapAnimator: SnapAnimator,
    private val scope: CoroutineScope,
    private val onTap: OverlayTapListener,
    private val screenWidthPx: () -> Int,
    private val screenHeightPx: () -> Int,
    private val navigationBarInsetBottomPx: () -> Int,
    private val onSettled: (xPx: Int, yPx: Int) -> Unit = { _, _ -> },
) : View.OnTouchListener {

    private var downRawX = 0f
    private var downRawY = 0f
    private var downParamsX = 0
    private var downParamsY = 0
    private var pastSlop = false

    private var pendingX = 0
    private var pendingY = 0
    private var frameScheduled = false

    private var snapJob: Job? = null

    private val frameCallback: () -> Unit = {
        frameScheduled = false
        params.x = pendingX
        params.y = pendingY
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapJob?.cancel()
                downRawX = event.rawX
                downRawY = event.rawY
                downParamsX = params.x
                downParamsY = params.y
                pastSlop = false
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY

                if (!pastSlop && exceedsSlop(dx, dy, ViewConfiguration.get(context).scaledTouchSlop)) {
                    pastSlop = true
                    dragStateRepository.set(true)
                    // A new gesture supersedes any write still in flight from a previous one
                    // (`[POS-4]`) — cancelled at the exact moment a drag becomes real, never later.
                    positionWriter.cancelPending()
                }

                if (pastSlop) {
                    pendingX = (downParamsX + dx).toInt()
                    pendingY = (downParamsY + dy).toInt()
                    scheduleFrame()
                }
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pastSlop) {
                    snap()
                } else {
                    onTap.onTap(OverlayAnchor(xPx = params.x, yPx = params.y, sizePx = renderSizePx))
                }
                true
            }

            else -> false
        }
    }

    private fun scheduleFrame() {
        if (frameScheduled) return
        frameScheduled = true
        frameScheduler.postFrameCallback(frameCallback)
    }

    /**
     * Horizontal spring snap to the nearest edge on release, per `[DRAG-5]`. `y` is frozen at the
     * release value, clamped only when it would overlap the navigation bar (`[DRAG-7]`) — the
     * clamp is a no-op in the ordinary case, preserving the exact release coordinate.
     */
    private fun snap() {
        val width = screenWidthPx()
        val height = screenHeightPx()
        val edge = nearestEdge(params.x, width, renderSizePx)
        val targetX = when (edge) {
            ScreenEdge.LEFT -> 0
            ScreenEdge.RIGHT -> (width - renderSizePx).coerceAtLeast(0)
        }

        val maxY = (height - navigationBarInsetBottomPx() - renderSizePx).coerceAtLeast(0)
        params.y = params.y.coerceIn(0, maxY)
        runCatching { windowManager.updateViewLayout(view, params) }

        snapJob = scope.launch {
            snapAnimator.animate(fromX = params.x.toFloat(), toX = targetX.toFloat()) { x ->
                params.x = x.toInt()
                runCatching { windowManager.updateViewLayout(view, params) }
            }
            dragStateRepository.set(false)
            // Write-at-rest: exactly once per completed gesture, after the snap animation
            // finishes and the final resting position is known (`[POS-3]`) — never from
            // ACTION_MOVE or an intermediate animation frame.
            val fraction = OverlayPositionFraction.ofPixels(
                position = OverlayPosition(params.x, params.y),
                widthPx = width,
                heightPx = height,
                renderSizePx = renderSizePx,
            )
            positionWriter.writeAtRest(fraction)
            onSettled(params.x, params.y)
        }
    }

    /**
     * Cancels any pending frame callback and any running snap animation. Wired from the owning
     * service's `onDestroy` (`[DRAG-8]`) so a destroyed window never receives a callback holding a
     * dead view.
     */
    fun cancel() {
        frameScheduler.removeFrameCallback(frameCallback)
        frameScheduled = false
        snapJob?.cancel()
        snapJob = null
    }
}
