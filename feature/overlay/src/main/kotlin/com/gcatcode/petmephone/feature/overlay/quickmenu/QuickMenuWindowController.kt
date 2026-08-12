package com.gcatcode.petmephone.feature.overlay.quickmenu

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuEvent
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuPlacement
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuState
import com.gcatcode.petmephone.core.domain.overlay.ScreenInsets
import com.gcatcode.petmephone.core.domain.overlay.reduce
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
 * The card renders a placeholder [Box] in this change. `QuickMenuCard` (PR 6) replaces
 * [cardContent] with the real Compose UI, metrics, and the launch button; [launchApp] already
 * exists here so the threat-matrix's explicit-intent requirement is met and tested before that UI
 * lands, per task 5.7/5.8.
 *
 * Deliberately non-focusable (design decision 6) and back-gesture-free (design decision 7): no
 * back-dispatcher attachment or back-key interception of any kind exists anywhere in this
 * package — enforced structurally by [NoBackGestureCodeTest], not just by omission here.
 */
internal class QuickMenuWindowController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val cardWidthPx: Int,
    private val cardHeightPx: Int,
    private val gapPx: Int,
    private val screenBoundsPx: () -> Pair<Int, Int>,
    private val screenInsets: () -> ScreenInsets,
    private val cardContent: @Composable () -> Unit = { Box(Modifier) },
) {

    private var state: QuickMenuState = QuickMenuState.Closed
    private var view: ComposeOverlayHost? = null

    /** The only entry point. Owns add/remove; no other call site may touch [windowManager] for
     *  this window. */
    fun onEvent(event: QuickMenuEvent) {
        val previous = state
        val next = reduce(previous, event)
        state = next

        val opened = previous is QuickMenuState.Closed && next is QuickMenuState.Open
        val closed = previous is QuickMenuState.Open && next is QuickMenuState.Closed

        if (opened) openWindow(next as QuickMenuState.Open)
        if (closed) closeWindow()
    }

    /** Service teardown. Removes the window if it is still open, leaving no view field set. */
    fun destroy() {
        if (state is QuickMenuState.Open) closeWindow()
        state = QuickMenuState.Closed
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
        val position = QuickMenuPlacement.place(
            anchor = open.anchor,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            cardWidthPx = cardWidthPx,
            cardHeightPx = cardHeightPx,
            insets = screenInsets(),
            gapPx = gapPx,
        )
        val host = ComposeOverlayHost(context.applicationContext, content = cardContent)
        val params = QuickMenuWindowParams.create(position, cardWidthPx, cardHeightPx)

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
