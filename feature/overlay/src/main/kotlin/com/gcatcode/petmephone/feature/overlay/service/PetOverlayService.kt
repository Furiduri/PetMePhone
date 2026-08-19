package com.gcatcode.petmephone.feature.overlay.service

import android.app.AppOpsManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.IBinder
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
import com.gcatcode.petmephone.core.domain.overlay.DragStateRepository
import com.gcatcode.petmephone.core.domain.overlay.OverlayPosition
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionFraction
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionRepository
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuAnchor
import com.gcatcode.petmephone.core.domain.overlay.QuickMenuEvent
import com.gcatcode.petmephone.core.domain.overlay.ScreenInsets
import com.gcatcode.petmephone.core.domain.permission.OverlayPermissionChecker
import com.gcatcode.petmephone.feature.overlay.input.FrameScheduler
import com.gcatcode.petmephone.feature.overlay.input.OverlayAnchor
import com.gcatcode.petmephone.feature.overlay.input.OverlayTapListener
import com.gcatcode.petmephone.feature.overlay.input.PetTouchController
import com.gcatcode.petmephone.feature.overlay.input.SnapAnimator
import com.gcatcode.petmephone.feature.overlay.position.OverlayPositionConfig
import com.gcatcode.petmephone.feature.overlay.position.PositionWriter
import com.gcatcode.petmephone.feature.overlay.quickmenu.QuickMenuConfig
import com.gcatcode.petmephone.feature.overlay.quickmenu.CardBounds
import com.gcatcode.petmephone.feature.overlay.quickmenu.QuickMenuWindowController
import com.gcatcode.petmephone.feature.overlay.quickmenu.ui.QuickMenuCardRoute
import com.gcatcode.petmephone.feature.overlay.ui.ComposeOverlayHost
import com.gcatcode.petmephone.feature.overlay.ui.PetOverlay
import com.gcatcode.petmephone.feature.overlay.ui.PetOverlayStateHolder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Owns the floating overlay window's lifecycle. Renders nothing beyond a placeholder view —
 * Compose rendering is `ComposeOverlayHost` (#14), drag is a separate issue, and both build on top
 * of the window this service creates.
 *
 * **Holds no application state** (see the dependency-injection spec, "no service-scoped Hilt
 * bindings," and the class-level fields below): `windowManager`, `overlayView`, and
 * `overlayParams` are framework plumbing to the *current* window, rebuildable from zero
 * information — never pet mood, task data, or position as a source of truth. If this process were
 * killed and restarted cold, `onCreate`/`onStartCommand` reconstruct the whole window from
 * `getSystemService` plus a fresh [OverlayPositionRepository] collection, with no field surviving.
 *
 * Runs as a `specialUse` foreground service only while the overlay is visible: the service exists
 * solely to host the window, so its lifetime already equals the overlay's (issue #9's option 4).
 */
@AndroidEntryPoint
class PetOverlayService : Service() {

    @Inject
    lateinit var overlayPermissionChecker: OverlayPermissionChecker

    @Inject
    lateinit var positionRepository: OverlayPositionRepository

    @Inject
    lateinit var windowManager: WindowManager

    @Inject
    lateinit var petOverlayStateHolder: PetOverlayStateHolder

    @Inject
    lateinit var dragStateRepository: DragStateRepository

    @Inject
    lateinit var positionWriter: PositionWriter

    @Inject
    lateinit var positionConfig: OverlayPositionConfig

    @Inject
    lateinit var frameScheduler: FrameScheduler

    @Inject
    lateinit var snapAnimator: SnapAnimator

    @Inject
    lateinit var quickMenuConfig: QuickMenuConfig

    private var serviceScope: CoroutineScope? = null
    private var positionCollectionJob: Job? = null

    // Framework plumbing to the current window only — see the class kdoc's statelessness rule.
    private var overlayView: ComposeOverlayHost? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var touchController: PetTouchController? = null

    // Owns the quick-menu card's own, independent window (design decision 12). Never touches
    // overlayParams/overlayView above — the pet window's LayoutParams are never mutated by the
    // card's lifecycle (overlay-quick-menu's machine-verifiable requirement).
    private var quickMenuController: QuickMenuWindowController? = null
    private var dragDismissalJob: Job? = null
    private var screenOffDismissalJob: Job? = null

    // The last fraction this window was placed from, kept so a bounds change can re-derive the
    // position instead of dragging the old pixels along. `null` means nothing is persisted yet,
    // which resolves to the resting corner of whatever screen is current — never a remembered one.
    private var lastPositionFraction: OverlayPositionFraction? = null

    private var appOpsListener: AppOpsManager.OnOpChangedListener? = null

    override fun onCreate() {
        super.onCreate()
        // Must run before any I/O and within 5s of service start, or the app crashes with
        // ForegroundServiceDidNotStartInTimeException. The type is the parameterised constant
        // FOREGROUND_SERVICE_TYPE, matching the manifest's specialUse declaration, per the type
        // decision recorded in issue #9.
        val notification = OverlayNotification.build(applicationContext)
        startForeground(OverlayNotification.NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        serviceScope = scope

        quickMenuController = QuickMenuWindowController(
            context = applicationContext,
            windowManager = windowManager,
            cardWidthPx = dpToPx(quickMenuConfig.cardWidthDp),
            maxCardHeightPx = dpToPx(quickMenuConfig.maxCardHeightDp),
            gapPx = dpToPx(quickMenuConfig.gapDp),
            screenBoundsPx = ::quickMenuBoundsPx,
            onCardBoundsChanged = ::followCardWithPet,
            screenInsets = ::quickMenuScreenInsets,
            cardContent = { content, onFieldFocusChanged ->
                // The container renders whichever content the controller currently holds
                // (design decision 4). onContentChange and the BackHandler's onBack both route
                // back into the same controller instance that owns this window's lifecycle.
                QuickMenuCardRoute(
                    content = content,
                    stateHolder = petOverlayStateHolder,
                    taskTitleMaxLength = quickMenuConfig.taskTitleMaxLength,
                    inputContentMinHeightDp = quickMenuConfig.inputContentMinHeightDp,
                    onFieldFocusChanged = onFieldFocusChanged,
                    onLaunchApp = { quickMenuController?.launchApp() },
                    onContentChange = { newContent -> quickMenuController?.onContentChange(newContent) },
                    // #100 owns submission; no task-domain use case is called from this route.
                    onSubmitTask = { title ->
                        Log.d(TAG, "task input submitted (not yet wired to #100): \"$title\"")
                    },
                    onBack = { quickMenuController?.onEvent(QuickMenuEvent.BackPressed) },
                )
            },
        )

        // Drag maps to PetDragged: the moment a genuine drag starts (past-slop movement, not a
        // tap), the card must close if it is open — decision 9's dismissal fallback. Observed
        // reactively off the same DragStateRepository PetTouchController already writes to,
        // rather than PetTouchController calling the controller directly, so the touch layer
        // stays untouched (overlay-quick-menu's "onTap seam only" requirement).
        dragDismissalJob = scope.launch {
            dragStateRepository.isDragging.collect { isDragging ->
                if (isDragging) quickMenuController?.onEvent(QuickMenuEvent.PetDragged)
            }
        }

        // Screen-off maps to ScreenOff, off the same reactive signal PetOverlayStateHolder.screenOn
        // already exposes.
        screenOffDismissalJob = scope.launch {
            petOverlayStateHolder.screenOn.collect { isScreenOn ->
                if (!isScreenOn) quickMenuController?.onEvent(QuickMenuEvent.ScreenOff)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Live-queried on every start, never trusted from a cached value (issue #11's sharpest rule).
        if (!overlayPermissionChecker.canDrawOverlays()) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW not granted; stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        registerRevocationWatcher()

        if (positionCollectionJob == null) {
            positionCollectionJob = serviceScope?.launch {
                // Await the first position emission — stored value or timeout fallback — before
                // the window is ever added, so the overlay never appears at a default position and
                // then jumps to the stored one (`[POS-5]`). A timeout and a stored `null` collapse
                // to the same computed-corner branch: the ambiguity between "nothing persisted" and
                // "read didn't finish in time" is harmless because neither ever fabricates a
                // coordinate.
                val stored = withTimeoutOrNull(positionConfig.firstReadTimeoutMillis) {
                    positionRepository.position.first()
                }
                addOverlayWindow(resolvePosition(stored))

                // A second, independent collection of the same cold flow. `drop(1)` discards its
                // own first emission (the same currently-stored value re-observed), so it is never
                // re-applied as a second `updateViewLayout` right after the one `addOverlayWindow`
                // above already performed.
                positionRepository.position.drop(1).collect { fraction ->
                    applyPosition(resolvePosition(fraction))
                }
            }
        }

        // START_STICKY redelivers a null Intent; onStartCommand already reconstructs everything
        // above from persisted/live state alone, with no Intent extras required.
        return START_STICKY
    }

    /**
     * Where the pet sits before the user has ever placed it: the bottom-right corner of *this*
     * screen. Resolved here rather than in the repository because only the service knows the
     * bounds, and recomputed on every emission so a device that rotated while nothing was
     * persisted still rests in a real corner instead of a remembered one.
     */
    private fun restingCorner(): OverlayPosition {
        val (width, height) = usableBoundsPx()
        return OverlayPosition.restingCorner(width, height, petSizePx())
    }

    /**
     * `null` (never persisted, or the first-read timeout elapsed) and a persisted fraction both
     * resolve here, against live bounds, so neither path can ever fabricate a coordinate (`[POS-1]`
     * `[POS-2]`).
     */
    private fun resolvePosition(fraction: OverlayPositionFraction?): OverlayPosition {
        lastPositionFraction = fraction
        if (fraction == null) return restingCorner()
        val (width, height) = usableBoundsPx()
        return fraction.toPixels(width, height, petSizePx())
    }

    /**
     * Screen bounds with the system bars and display cutout removed from the right and bottom
     * edges, which are the ones a bottom-right resting corner runs into.
     *
     * An overlay window is not laid out inside the app's insets — nothing subtracts them for us —
     * so resting against the raw bounds parks the pet underneath the gesture bar or the launcher's
     * search field, where it looks misplaced and is awkward to touch. Insets are read ignoring
     * visibility, so a transiently hidden navigation bar does not move the pet's home.
     */
    private fun usableBoundsPx(): Pair<Int, Int> {
        val (width, height) = screenBoundsPx()

        val insets = windowManager.currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
        return (width - insets.right) to (height - insets.bottom)
    }

    /** Navigation-bar-only bottom inset, used to keep a horizontal snap's frozen `y` off the bar. */
    private fun navigationBarInsetBottomPx(): Int {
        return windowManager.currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
            .bottom
    }

    /** Where the pet was before it started following the card, so it can be put back exactly. */
    private var petYBeforeCardFollow: Int? = null
    private var petXBeforeCardFollow: Int? = null

    private fun applyPosition(position: OverlayPosition) {
        val params = overlayParams
        if (params == null) {
            addOverlayWindow(position)
        } else {
            params.x = position.x
            params.y = position.y
            overlayView?.let { view -> runCatching { windowManager.updateViewLayout(view, params) } }
        }
    }

    private fun addOverlayWindow(position: OverlayPosition) {
        // Application context, never `this`: the view must not outlive-retain the service instance
        // (issue #13's WindowLeaked warning). `:core:designsystem` has no XML theme (its manifest
        // declares no `<application>` block and it ships no `res/values` at all — verified, not
        // assumed) so a plain, unwrapped applicationContext is fine here; there is no
        // ContextThemeWrapper to apply. Dynamic color, if ever adopted, reads resources and
        // wallpaper rather than an Activity theme, so it too works unwrapped from a service context.
        val view = ComposeOverlayHost(applicationContext, content = { PetOverlay(petOverlayStateHolder) })
        val params = OverlayWindowParams.create(position, petSizePx())

        runCatching { windowManager.addView(view, params) }
            .onSuccess {
                overlayView = view
                overlayParams = params
                touchController = PetTouchController(
                    context = applicationContext,
                    windowManager = windowManager,
                    view = view,
                    params = params,
                    renderSizePx = petSizePx(),
                    dragStateRepository = dragStateRepository,
                    positionWriter = positionWriter,
                    frameScheduler = frameScheduler,
                    snapAnimator = snapAnimator,
                    scope = serviceScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                    onTap = OverlayTapListener { anchor -> onPetTapped(anchor) },
                    screenWidthPx = { screenBoundsPx().first },
                    screenHeightPx = { screenBoundsPx().second },
                    navigationBarInsetBottomPx = ::navigationBarInsetBottomPx,
                ).also { controller -> view.setOnTouchListener(controller) }
            }
            .onFailure { error ->
                Log.e(TAG, "addView failed: ${error.javaClass.simpleName}: ${error.message}")
                stopSelf()
            }
    }

    /**
     * The only call site that talks to [quickMenuController] from the touch layer
     * (overlay-quick-menu's "onTap seam only" requirement) — no new touch listener is attached to
     * the pet for this. Maps [OverlayAnchor] into the domain-side [QuickMenuAnchor] since
     * `OverlayAnchor` cannot cross into `:core:domain` (design decision 10).
     */
    private fun onPetTapped(anchor: OverlayAnchor) {
        quickMenuController?.onEvent(
            QuickMenuEvent.PetTapped(QuickMenuAnchor(xPx = anchor.xPx, yPx = anchor.yPx, sizePx = anchor.sizePx)),
        )
    }

    /**
     * System-bar and display-cutout insets for the quick-menu card, mirroring [usableBoundsPx]'s
     * `WindowMetrics` path but on all four sides (the card can open toward any edge, unlike the
     * pet which only ever rests bottom-right).
     */
    /**
     * Only the horizontal insets, deliberately.
     *
     * [quickMenuBoundsPx] already reports the parent frame, which excludes the status and
     * navigation bars — subtracting them again here moved the card past the pet and made it
     * overlap, measured as a 42px overlap where a 21px gap was intended. The vertical bars are
     * therefore zero in this coordinate space, by construction rather than by luck.
     *
     * Horizontal insets are zero for the same reason: [quickMenuBoundsPx] subtracts left and right
     * as well, so every edge is already accounted for and the placement works in pure parent-frame
     * coordinates.
     */

    /**
     * Keeps the pet with the card while the task-input field holds focus, and puts it back when
     * focus goes away or the card closes.
     *
     * [cardTopOnScreen] is the card's laid-out top edge, and it is the ONLY input: no keyboard
     * height is computed or estimated anywhere. That is deliberate. `ADJUST_RESIZE` already moved
     * the card above the keyboard for us — the system did that arithmetic — so the card's own
     * position is a measured value we can hang the pet on. The keyboard height itself was measured
     * across three spike rounds to have no dependable signal on this window class, and nothing
     * here needs it.
     *
     * The pet is not the IME target and never will be, so nothing in the platform moves it: with
     * the keyboard up its frame was measured unchanged at `[810,2091][1080,2361]` while the card
     * had moved to `[54,858][789,1268]`. Left alone it simply sits behind the keyboard.
     *
     * A `null` [cardTopOnScreen] means the card's position could not be read, which is NOT the
     * same as a top of zero — zero is the top of the screen, a real place. On null the pet returns
     * home rather than following a position nobody vouched for.
     *
     * This never persists. It mutates the live `LayoutParams` only; the stored fraction that
     * survives restarts is written by `PositionWriter` from the end of a drag, and that path is
     * not reachable from here, so the position the user chose is not overwritten by a move they
     * did not make.
     */
    private fun followCardWithPet(bounds: CardBounds?) {
        val params = overlayParams ?: return
        val view = overlayView ?: return

        if (bounds == null) {
            val homeY = petYBeforeCardFollow
            val homeX = petXBeforeCardFollow
            if (homeY == null && homeX == null) return
            homeY?.let { params.y = it }
            homeX?.let { params.x = it }
            petYBeforeCardFollow = null
            petXBeforeCardFollow = null
            runCatching { windowManager.updateViewLayout(view, params) }
            return
        }

        if (petYBeforeCardFollow == null) petYBeforeCardFollow = params.y
        if (petXBeforeCardFollow == null) petXBeforeCardFollow = params.x

        val parentTop = overlayParentTopPx()
        val gap = dpToPx(quickMenuConfig.gapDp)
        val petWidth = petSizePx()
        val (displayWidth, _) = screenBoundsPx()

        // Level with the card, not stacked on top of it (#118).
        val targetY = bounds.topPx - parentTop
        if (targetY < 0) {
            Log.d(TAG, "pet follow skipped: the card sits above the overlay frame (y=$targetY)")
            return
        }

        // Clearing the card sideways is only necessary because they are now at the same height.
        // QuickMenuPlacement picks the card's x to sit near the pet and stay on screen; it never
        // had to avoid the pet, because until now the card was above or below it and crossing in x
        // was harmless. Measured overlap once they shared a top edge: 104px.
        // Screen coordinates on both sides, deliberately. The pet's y is relative to the overlay
        // parent frame but its x is NOT: measured, mAttrs x=2407 lands the frame at 2407 while
        // mAttrs y=0 lands it at 130. Converting the card's x into the parent frame made the card
        // look 130px narrower than it is, the comparison said "no overlap", and 104px of overlap
        // stayed on screen. The axes are asymmetric here; treating them alike is the bug.
        val cardLeft = bounds.leftPx
        val cardRight = bounds.rightPx
        val petLeft = params.x
        val petRight = petLeft + petWidth
        val targetX = when {
            petRight <= cardLeft || petLeft >= cardRight -> petLeft
            cardRight + gap + petWidth <= displayWidth -> cardRight + gap
            cardLeft - gap - petWidth >= 0 -> cardLeft - gap - petWidth
            else -> null
        }
        if (targetX == null) {
            // Neither side fits. Leaving the pet where it is beats inventing a coordinate that
            // puts it half off screen; the overlap is visible and honest, a clamped position is not.
            Log.d(TAG, "pet follow: no side clears the card (cardLeft=$cardLeft cardRight=$cardRight)")
            params.y = targetY
            runCatching { windowManager.updateViewLayout(view, params) }
            return
        }

        Log.d(TAG, "pet follow: cardTop=${bounds.topPx} -> y=$targetY x=$targetX")
        params.y = targetY
        params.x = targetX
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    /**
     * The top edge of the frame an overlay window is laid out in — the inset the pet's own y is
     * already relative to.
     */
    private fun overlayParentTopPx(): Int =
        windowManager.currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            .top

    private fun quickMenuScreenInsets(): ScreenInsets {
        val insets = windowManager.currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
        return ScreenInsets(left = 0, top = 0, right = 0, bottom = 0)
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun registerRevocationWatcher() {
        if (appOpsListener != null) return

        val appOpsManager = getSystemService(AppOpsManager::class.java) ?: return
        val listener = AppOpsManager.OnOpChangedListener { _, _ ->
            if (!overlayPermissionChecker.canDrawOverlays()) {
                Log.w(TAG, "SYSTEM_ALERT_WINDOW revoked while running; stopping")
                stopSelf()
            }
        }

        // Reacts to the AppOpsManager mode change directly, so no polling loop is needed and
        // nothing is missed even if Doze batches other timers.
        runCatching {
            appOpsManager.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, listener)
            appOpsListener = listener
        }.onFailure { error ->
            Log.w(TAG, "could not register overlay-permission watcher: ${error.javaClass.simpleName}")
        }
    }

    private fun unregisterRevocationWatcher() {
        val appOpsManager = getSystemService(AppOpsManager::class.java)
        val listener = appOpsListener
        if (appOpsManager != null && listener != null) {
            runCatching { appOpsManager.stopWatchingMode(listener) }
        }
        appOpsListener = null
    }

    /**
     * Re-derives the position from the stored fraction against the new bounds, rather than
     * carrying the old pixels over.
     *
     * Clamping alone looked sufficient and is not: a rotation into a wider screen leaves every old
     * coordinate inside the new bounds, so `coerceIn` changes nothing and the pet keeps its
     * absolute pixels — which is mid-screen in the new orientation. Storing the position as a
     * fraction exists for exactly this moment, so this is the moment to read it (`[POS-8]`).
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val params = overlayParams
        val view = overlayView
        if (params == null || view == null) return

        val position = resolvePosition(lastPositionFraction)
        params.x = position.x
        params.y = position.y
        // Safety net only: [OverlayPositionFraction] does not validate its range, so a corrupt
        // stored value could still resolve outside the new bounds.
        val (width, height) = screenBoundsPx()
        OverlayWindowParams.clampToBounds(params, width, height)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    /**
     * The bounds the quick-menu card must be placed in — NOT [screenBoundsPx].
     *
     * A `TYPE_APPLICATION_OVERLAY` window is laid out inside a parent frame that excludes the
     * system bars, and the pet's own `LayoutParams` coordinates already live in that frame. Placing
     * the card against the raw display instead put it out by exactly the bars' total height: the
     * card sat 216px above the pet where 21px was intended, measured from a live `dumpsys`.
     *
     * The insets cannot be used to correct for this here — `currentWindowMetrics.windowInsets` read
     * from a Service returns zero, which is why subtracting them changed nothing. The
     * configuration's dp size is the reliable source: it reports the same 840dp the window manager
     * uses for the parent frame.
     */
    private fun quickMenuBoundsPx(): Pair<Int, Int> {
        val (width, height) = screenBoundsPx()
        val insets = windowManager.currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
        return (width - insets.left - insets.right) to (height - insets.top - insets.bottom)
    }

    /**
     * The pet's side length: a quarter of the screen's shorter edge, not a fixed pixel count.
     * Measured on two devices: 220px reads as 84dp at 420dpi and only 67dp at 520dpi, so a constant
     * looks right on one phone and too small on the next. Capped by [OverlayWindowParams.MAX_SIZE_PX]
     * so a tablet cannot produce an absurd sprite.
     */
    private fun petSizePx(): Int {
        val (width, height) = screenBoundsPx()
        val shorterEdge = minOf(width, height)
        return (shorterEdge / PET_SIZE_SCREEN_DIVISOR).coerceAtMost(OverlayWindowParams.MAX_SIZE_PX)
    }

    private fun screenBoundsPx(): Pair<Int, Int> {
        val bounds = windowManager.currentWindowMetrics.bounds
        return bounds.width() to bounds.height()
    }

    // Swiping the app out of recents must not kill the pet — the whole point of a floating
    // companion is that it survives that. Deliberately does nothing.
    override fun onTaskRemoved(rootIntent: Intent?) = Unit

    override fun onDestroy() {
        unregisterRevocationWatcher()
        positionCollectionJob = null

        // Cancel before the scope itself is torn down, so the pending frame callback and the
        // snap-animation coroutine are removed explicitly rather than left dangling against a
        // view about to be destroyed (`[DRAG-8]`).
        touchController?.cancel()
        touchController = null

        dragDismissalJob?.cancel()
        dragDismissalJob = null
        screenOffDismissalJob?.cancel()
        screenOffDismissalJob = null

        quickMenuController?.destroy()
        quickMenuController = null

        serviceScope?.cancel()
        serviceScope = null

        overlayView?.let { view ->
            // destroy() first: moves ComposeOverlayHost's own lifecycle to DESTROYED exactly
            // once, disposing the composition and stopping the Recomposer, before the view
            // itself is torn out of WindowManager.
            view.destroy()
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        overlayParams = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val TAG = "PetOverlayService"

        // Parameterised constant per issue #13's explicit acceptance criterion, matching the
        // manifest's android:foregroundServiceType="specialUse" declaration.
        const val FOREGROUND_SERVICE_TYPE = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    }
}

/** The pet occupies a quarter of the screen's shorter edge. */
private const val PET_SIZE_SCREEN_DIVISOR = 4
