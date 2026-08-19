package com.gcatcode.petmephone.feature.overlay.quickmenu

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gcatcode.petmephone.core.domain.overlay.BackOutcome
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuContent
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuEvent
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuPlacement
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuPlacementResult
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuState
import com.gcatcode.petmephone.core.domain.overlay.ScreenInsets
import com.gcatcode.petmephone.core.domain.overlay.VerticalAnchor
import com.gcatcode.petmephone.core.domain.overlay.reduce
import com.gcatcode.petmephone.core.domain.overlay.resolveBack
import com.gcatcode.petmephone.feature.overlay.service.QuickMenuWindowParams
import com.gcatcode.petmephone.feature.overlay.ui.ComposeOverlayHost

/**
 * Where the card actually ended up, in screen coordinates, after the window manager placed it.
 *
 * The pet is positioned against these rather than against anything the keyboard reports. The
 * horizontal edges matter as much as the top: once the pet is level with the card instead of
 * stacked above it, the two can collide sideways — measured at 104px of overlap on the
 * maintainer's device, the card ending at 2511 and the pet starting at 2407.
 */
internal data class CardBounds(val topPx: Int, val leftPx: Int, val rightPx: Int)



/**
 * Owns the quick-menu card window's whole lifecycle — add, remove, and the dismissal state
 * machine — and nothing else (design decision 12, mirroring the `PetTouchController` extraction
 * precedent: constructed by [com.gcatcode.petmephone.feature.overlay.service.PetOverlayService],
 * not `@Inject`ed, because it needs the live window and a per-open anchor).
 *
 * [onEvent] is the only entry point. It is a thin driver around the pure [reduce]: the reducer
 * decides the *next* [QuickMenuState], this class reacts to the *transition* by adding or
 * removing the window. No business logic about dismissability lives here — that is entirely
 * `:core:domain`'s [QuickMenuState] (design decision 9).
 *
 * The card renders a placeholder [Box] in this change. `QuickMenuCard` (Phase 4) replaces
 * [cardContent] with the real Compose UI, metrics, and the launch button; [launchApp] already
 * exists here so the threat-matrix's explicit-intent requirement is met and tested before that UI
 * lands.
 *
 * The card is now focusable (design decision 1) and can receive a real back press. [content]
 * holds which of [QuickMenuContent]'s three cases is showing; it is a field on **this** class, not
 * on [QuickMenuState] or the service (design decision 4 — widening `Open(anchor)` would turn the
 * reducer's "every event from `Open` yields `Closed`" reachability guarantee into a claim about
 * product state, and the service is disqualified by decision 5a). It **survives** every dismissal
 * path — [closeWindow] never resets it — and is only reset to [QuickMenuContent.Dashboard] by
 * [destroy] or by constructing a fresh controller (decision 5b: nothing here is ever persisted to
 * disk). [onEvent] applies [resolveBack] to [content] on [QuickMenuEvent.BackPressed] before
 * touching [reduce]: `Instructions` swaps back to `TaskInput` and `TaskInput` back to `Dashboard`, neither closing the
 * window; only `Dashboard` forwards the event into [reduce], which closes the card (design
 * decision 7). One
 * `OnBackPressedDispatcherOwner` and, once the container lands in Phase 4, exactly one
 * `BackHandler` are the only back-related wiring this package carries — enforced structurally by
 * `QuickMenuBackWiringCodeTest`, the inversion of the retired `NoBackGestureCodeTest` (design's
 * "two contradicted tests" section).
 */
internal class QuickMenuWindowController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val cardWidthPx: Int,
    private val maxCardHeightPx: Int,
    private val gapPx: Int,
    private val screenBoundsPx: () -> Pair<Int, Int>,
    private val screenInsets: () -> ScreenInsets,
    private val cardContent: @Composable (QuickMenuContent, (Boolean) -> Unit) -> Unit =
        { _, _ -> Box(Modifier) },
    private val onCardBoundsChanged: (CardBounds?) -> Unit = {},
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    private var state: QuickMenuState = QuickMenuState.Closed
    private var view: ComposeOverlayHost? = null
    // Nullable rather than a sentinel: `nowMs() - Long.MIN_VALUE` overflows to a negative number,
    // which made the same-gesture check true forever and suppressed every tap.
    private var closedByOutsideTouchAtMs: Long? = null

    /**
     * Whether the task-input field currently holds focus, and the placement the window was opened
     * with so it can be put back.
     *
     * Focus is the trigger, never keyboard visibility. the IME inset APIs are not delivered to this window class,
     * and the visible-frame query reported the resize on some device runs and not others minutes
     * apart on the same device; focus is a fact this app owns outright.
     */
    private var fieldFocused = false
    private var openPlacement: QuickMenuPlacementResult? = null

    /**
     * The params handed to `addView`, kept rather than read back from `host.layoutParams`.
     *
     * The view only carries them because the window manager attached them, so reading them back
     * makes this class depend on someone else's side effect — it returned null under a stubbed
     * window manager, and would return null for any addView that did not complete. The service
     * keeps its own reference for the pet window for the same reason.
     */
    private var cardParams: WindowManager.LayoutParams? = null

    /**
     * How the card notices the keyboard has gone away, without asking the system about it.
     *
     * While the field holds focus the card sits at `y = 0` with `BOTTOM` gravity, so its own
     * on-screen bottom edge IS the bottom of the frame it is laid out in. Measured on the
     * maintainer's device across 65 samples, that edge is binary and never once wobbled: 1727 with
     * the keyboard up, 2660 with it down — a 933px difference, the same keyboard height three
     * spike rounds measured on the same hardware.
     *
     * Reading our own laid-out view is not the signal that failed those rounds. What failed was
     * asking the platform whether the keyboard was showing. This is the same class of reading that
     * already places the pet correctly against the card.
     *
     * Focus alone cannot do this job: dismissing the keyboard does NOT clear the field's focus, so
     * a focus-loss trigger fires on some paths and never on others. Hence the two-part rule — the
     * bottom must be seen to SHRINK first, and only a later return to the largest value seen counts
     * as the keyboard leaving. Without the first half, the observation taken before the keyboard
     * has animated in would immediately look like "no keyboard" and undo the move.
     */
    private var focusedMaxBottomPx: Int? = null
    private var sawReducedBottom = false
    private var bottomWatcher: ViewTreeObserver.OnPreDrawListener? = null
    private var lastObservedBottomPx: Int? = null

    /** Which content the card shows, once open. Backed by [mutableStateOf] purely as the
     *  recomposition mechanism for the in-place swap on [QuickMenuEvent.BackPressed] — the field
     *  itself still lives on this controller, not in Compose (design decision 4); a `remember` in
     *  the composition would not survive the window removal every dismissal path performs. */
    internal var content: QuickMenuContent by mutableStateOf(QuickMenuContent.Dashboard)
        private set

    /** Whether the card window is currently shown. Read-only; [onEvent] is the only way to change it. */
    val isOpen: Boolean
        get() = state is QuickMenuState.Open

    /** Handed to the container (Phase 4) so activating the add-task control, or leaving the
     *  input, swaps [content] in place without opening or closing the window. */
    internal fun onContentChange(newContent: QuickMenuContent) {
        content = newContent
    }

    /** The only entry point. Owns add/remove; no other call site may touch [windowManager] for
     *  this window. */
    fun onEvent(event: QuickMenuEvent) {
        if (event is QuickMenuEvent.BackPressed && state is QuickMenuState.Open) {
            when (resolveBack(content)) {
                // Unwind the container by one step. The window stays open, so these never reach
                // `reduce` — only `CloseCard` does, below. One level per press, never two.
                BackOutcome.ShowTaskInput -> {
                    content = QuickMenuContent.TaskInput
                    return
                }
                BackOutcome.ShowDashboard -> {
                    content = QuickMenuContent.Dashboard
                    return
                }
                // Nothing left to unwind. Fall through to the normal dispatch below,
                // which forwards BackPressed into `reduce` and closes the card exactly like any
                // other dismissal event.
                BackOutcome.CloseCard -> Unit
            }
        }

        // One finger produces TWO events when the card is open and the pet is tapped: the card
        // window carries FLAG_WATCH_OUTSIDE_TOUCH, so the touch lands as ACTION_OUTSIDE and closes
        // the card, and then the pet's own tap listener reports PetTapped, which reopens it. Each
        // event is individually correct, which is why no unit test on `reduce` could catch this —
        // the defect only exists in their coincidence, and it looked to the user like tapping the
        // pet relaunched the card instead of closing it.
        //
        // The tail of that gesture is therefore ignored. The window is deliberately narrow: a
        // genuine second tap by a person cannot follow the first within it.
        val lastOutsideClose = closedByOutsideTouchAtMs
        if (event is QuickMenuEvent.PetTapped &&
            lastOutsideClose != null &&
            nowMs() - lastOutsideClose < SAME_GESTURE_WINDOW_MS
        ) {
            return
        }

        val previous = state
        val next = reduce(previous, event)
        state = next

        if (event is QuickMenuEvent.OutsideTouch && previous is QuickMenuState.Open) {
            closedByOutsideTouchAtMs = nowMs()
        }

        val opened = previous is QuickMenuState.Closed && next is QuickMenuState.Open
        val closed = previous is QuickMenuState.Open && next is QuickMenuState.Closed

        if (opened) openWindow(next as QuickMenuState.Open)
        if (closed) closeWindow()
    }

    /** Service teardown. Removes the window if it is still open, leaving no view field set, and
     *  resets [content] to [QuickMenuContent.Dashboard] — design decision 5b's destroyed
     *  boundary; the next open starts fresh, exactly like a brand-new controller after process
     *  death. */
    fun destroy() {
        if (state is QuickMenuState.Open) closeWindow()
        state = QuickMenuState.Closed
        content = QuickMenuContent.Dashboard
    }

    /**
     * Starts this app's own launcher `Activity` via [android.content.pm.PackageManager]'s
     * `getLaunchIntentForPackage`, never a component name assembled from external or stored data
     * (threat-matrix "process integration" row). This already returns an **explicit** `Intent`
     * (`PackageManager` resolves it against this app's own manifest, the same own-package name
     * every call site here uses, never external input) naming the launcher `Activity`;
     * `FLAG_ACTIVITY_NEW_TASK` is added because the caller is a `Service`, not an `Activity`.
     * Failure is caught and logged, never left to crash the service.
     */
    fun launchApp() {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent == null) {
            Log.e(TAG, "no launcher intent resolved for ${context.packageName}")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { error ->
                Log.e(TAG, "launch failed: ${error.javaClass.simpleName}: ${error.message}")
            }
    }


    /**
     * The task-input field gained or lost focus.
     *
     * While it holds focus the card is placed flush against the bottom of the frame actually in
     * effect — `y = 0`, `BOTTOM` gravity — which is the one request that is always satisfiable.
     * That matters because of what was measured on a Pixel_10 emulator: `ADJUST_RESIZE` shrinks
     * this window's parent frame by exactly the keyboard height (`[0,142][1080,2361]` becomes
     * `[0,142][1080,1541]`), and the card's bottom-anchored offset is re-applied against the new
     * bottom. With the pet high up that offset was 1137 against a frame only 1399 tall, so the
     * requested top landed at -6 and the window manager clamped the card against the TOP edge
     * (`frame=[54,142][789,552]`) — the "card jumps to the top" defect. An offset of zero cannot
     * overflow, so there is nothing left to clamp and the keyboard's own resize puts the card
     * directly above it, with no arithmetic of ours involved.
     *
     * Losing focus restores the placement the card was opened with.
     */
    internal fun onFieldFocusChanged(focused: Boolean) {
        if (fieldFocused == focused) return
        fieldFocused = focused
        applyFocusPlacement()
    }

    /**
     * Moves the card window only. The focus flags and `softInputMode` set at `addView` are never
     * touched here: design decision 2 forbids toggling focusability at runtime, and a position
     * update is not a flag update. [QuickMenuWindowParamsTest] holds the flags, and this method
     * mutates nothing but `gravity` and `y`.
     */
    private fun applyFocusPlacement() {
        val host = view ?: return
        val params = cardParams ?: return
        val placement = openPlacement

        if (fieldFocused) {
            params.gravity = Gravity.START or Gravity.BOTTOM
            params.y = 0
        } else if (placement != null) {
            params.gravity = Gravity.START or when (placement.verticalAnchor) {
                VerticalAnchor.TOP -> Gravity.TOP
                VerticalAnchor.BOTTOM -> Gravity.BOTTOM
                VerticalAnchor.CENTER -> Gravity.CENTER_VERTICAL
            }
            params.y = placement.yPx
        } else {
            // No opening placement to return to. Leaving the card where it is beats guessing a
            // position, and the pet is told to go home rather than follow a card we cannot vouch for.
            onCardBoundsChanged(null)
            return
        }

        runCatching { windowManager.updateViewLayout(host, params) }
            .onFailure { error ->
                Log.e(TAG, "updateViewLayout failed: ${error.javaClass.simpleName}: ${error.message}")
                onCardBoundsChanged(null)
                return
            }

        if (!fieldFocused) {
            detachBottomWatcher(host)
            onCardBoundsChanged(null)
            return
        }
        attachBottomWatcher(host)
        // Read after a layout pass, never immediately: the window manager has not repositioned the
        // view yet at this point, so reading now would report the pre-move top.
        host.post { observeCardBounds(host) }
    }

    /**
     * The card's laid-out top edge on screen, or `null` when it could not be read.
     *
     * Null is a distinct outcome, never a zero: a zero here is a real coordinate meaning the very
     * top of the screen, and this project has already published one false conclusion from a
     * geometry field that defaulted instead of reporting absence.
     */

    /**
     * Watches the card's own laid-out bounds while it is parked at the bottom. Every reposition
     * the window manager performs — including the one the keyboard's resize causes — arrives here.
     */
    /**
     * Watches where the card actually is, on every draw.
     *
     * An `OnLayoutChangeListener` was tried first and is deaf to this: moving a window does not
     * re-lay-out its root view, so the listener fired once and never again. Measured on the
     * maintainer's device — the pet was positioned from a card top of 2144 (the pre-keyboard
     * position) and stayed there while the card moved to 1211 and back, and the "keyboard has
     * gone" state machine never received a second observation to advance on.
     *
     * A pre-draw listener fires whenever the window is redrawn, which a reposition always causes.
     * It is throttled on the observed value, so an unchanged position costs a comparison.
     */
    private fun attachBottomWatcher(host: ComposeOverlayHost) {
        if (bottomWatcher != null) return
        val watcher = ViewTreeObserver.OnPreDrawListener {
            observeCardBounds(host)
            true
        }
        bottomWatcher = watcher
        host.viewTreeObserver.addOnPreDrawListener(watcher)
    }

    private fun detachBottomWatcher(host: ComposeOverlayHost) {
        bottomWatcher?.let { watcher ->
            if (host.viewTreeObserver.isAlive) host.viewTreeObserver.removeOnPreDrawListener(watcher)
        }
        bottomWatcher = null
        focusedMaxBottomPx = null
        sawReducedBottom = false
        lastObservedBottomPx = null
    }

    /**
     * One observation of where the card actually ended up, and the only place the "keyboard has
     * gone" conclusion is drawn.
     *
     * An unreadable position is not a position: it neither advances the state machine nor moves
     * the pet. This project has already published one false conclusion from a geometry field that
     * defaulted instead of reporting absence.
     */
    private fun observeCardBounds(host: ComposeOverlayHost) {
        if (!fieldFocused) return
        val bounds = cardBoundsOrNull(host)
        val top = bounds?.topPx
        if (bounds == null || top == null) {
            onCardBoundsChanged(null)
            return
        }
        val bottom = top + host.height
        if (bottom == lastObservedBottomPx) return
        lastObservedBottomPx = bottom
        Log.d(TAG, "card observed: top=$top bottom=$bottom max=$focusedMaxBottomPx reduced=$sawReducedBottom")
        val maxSeen = focusedMaxBottomPx
        if (maxSeen == null || bottom > maxSeen) {
            focusedMaxBottomPx = bottom
        } else if (bottom < maxSeen) {
            sawReducedBottom = true
        }

        if (sawReducedBottom && bottom >= (focusedMaxBottomPx ?: bottom)) {
            // The frame grew back to its largest observed size: the keyboard is gone. Put the card
            // where it opened and send the pet home. Focus is deliberately left alone — the user
            // may still be typing-ready, and clearing it here would fight the platform.
            fieldFocused = false
            applyFocusPlacement()
            return
        }
        onCardBoundsChanged(bounds)
    }

    private fun cardBoundsOrNull(host: ComposeOverlayHost): CardBounds? {
        if (!host.isAttachedToWindow || host.height == 0 || host.width == 0) return null
        val location = IntArray(2)
        host.getLocationOnScreen(location)
        return CardBounds(
            topPx = location[1],
            leftPx = location[0],
            rightPx = location[0] + host.width,
        )
    }

    private fun openWindow(open: QuickMenuState.Open) {
        val (screenWidthPx, screenHeightPx) = screenBoundsPx()
        val placement = QuickMenuPlacement.place(
            anchor = open.anchor,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            cardWidthPx = cardWidthPx,
            maxCardHeightPx = maxCardHeightPx,
            insets = screenInsets(),
            gapPx = gapPx,
        )
        // Reads `content` at render time, not at open time: the field survives every dismissal
        // path (design decision 5), so `openWindow` renders whatever was active at the last
        // dismissal, or `Dashboard` on a fresh controller or right after `destroy()`.
        val host = ComposeOverlayHost(context.applicationContext, content = { cardContent(content, ::onFieldFocusChanged) })
        openPlacement = placement
        val params = QuickMenuWindowParams.create(placement, cardWidthPx)

        runCatching { windowManager.addView(host, params) }
            .onSuccess {
                view = host
                cardParams = params
                host.setOnTouchListener { _, motionEvent ->
                    if (motionEvent.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                        onEvent(QuickMenuEvent.OutsideTouch)
                        true
                    } else {
                        false
                    }
                }
            }
            .onFailure { error ->
                Log.e(TAG, "addView failed: ${error.javaClass.simpleName}: ${error.message}")
                // Roll the state back: the window never actually opened, so the reducer's
                // Open(anchor) would otherwise leave onEvent believing a window exists to remove.
                state = QuickMenuState.Closed
            }
    }

    private fun closeWindow() {
        // The pet goes home before the window disappears: a card that no longer exists cannot be
        // followed, and leaving the pet parked against a vanished card would strand it.
        fieldFocused = false
        openPlacement = null
        cardParams = null
        onCardBoundsChanged(null)
        val host = view ?: return
        detachBottomWatcher(host)
        host.destroy()
        runCatching { windowManager.removeView(host) }
        view = null
    }

    private companion object {
        const val TAG = "QuickMenuWindowController"
    }
}

/**
 * How long after an ACTION_OUTSIDE dismissal a PetTapped is treated as the same physical gesture
 * rather than as a new tap. Long enough to cover the dispatch of one touch across two windows,
 * short enough that a person cannot tap twice inside it.
 */
private const val SAME_GESTURE_WINDOW_MS = 250L
