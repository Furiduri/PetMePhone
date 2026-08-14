package com.gcatcode.petmephone.feature.overlay.quickmenu

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.MotionEvent
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
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuState
import com.gcatcode.petmephone.core.domain.overlay.ScreenInsets
import com.gcatcode.petmephone.core.domain.overlay.reduce
import com.gcatcode.petmephone.core.domain.overlay.resolveBack
import com.gcatcode.petmephone.feature.overlay.service.QuickMenuWindowParams
import com.gcatcode.petmephone.feature.overlay.ui.ComposeOverlayHost

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
    private val cardContent: @Composable (QuickMenuContent) -> Unit = { _ -> Box(Modifier) },
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    private var state: QuickMenuState = QuickMenuState.Closed
    private var view: ComposeOverlayHost? = null
    // Nullable rather than a sentinel: `nowMs() - Long.MIN_VALUE` overflows to a negative number,
    // which made the same-gesture check true forever and suppressed every tap.
    private var closedByOutsideTouchAtMs: Long? = null

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
        val host = ComposeOverlayHost(context.applicationContext, content = { cardContent(content) })
        val params = QuickMenuWindowParams.create(placement, cardWidthPx)

        runCatching { windowManager.addView(host, params) }
            .onSuccess {
                view = host
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
        val host = view ?: return
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
