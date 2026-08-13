package com.petmephone.spike.imeviability

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * A vertical [LinearLayout] with one addition: it reports every `onWindowFocusChanged` callback the
 * framework sends it, so "did this window ever actually receive focus" is a real, observed signal
 * rather than an assumption baked into the window flags alone.
 */
private class FocusTrackingContainer(
    context: Context,
    private val onFocusChanged: (hasFocus: Boolean) -> Unit,
) : LinearLayout(context) {
    init {
        // Vertical, not a FrameLayout: the run banner and the text field must stack, not overlap.
        orientation = VERTICAL
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        onFocusChanged(hasWindowFocus)
    }
}

/**
 * Everything one run measured, handed to the activity whole.
 *
 * It travels through the companion object rather than through `Intent` extras because the samples
 * are structured readings, and flattening them into primitive extras is exactly the step where the
 * previous instrument lost the distinction between "no reading" and "a reading of zero". Service
 * and activity live in the same process, so there is no marshalling boundary to justify the loss.
 */
data class SpikeRunResult(
    val mode: SpikeMode,
    val startYPx: Int?,
    val samples: List<KeyboardGeometrySample>,
    val geometrySignal: KeyboardGeometrySignal,
    val controlImeInsetDispatchFired: Boolean,
    val windowEverReceivedFocus: Boolean,
    val windowRemovedCleanly: Boolean,
    /**
     * True when the layout-driven sampling hit its cap and stopped recording. Without this a
     * truncated series would be indistinguishable from a series that simply stopped changing.
     */
    val layoutDrivenSampleCapReached: Boolean,
    val petFollow: PetFollowRecord,
)

/**
 * Owns the measurement window's whole lifecycle for one run: adds it (after a short delay so the
 * maintainer can switch to the app under test, e.g. one playing a video), samples the window's own
 * geometry at four defined moments, and removes it again. Runs as a foreground service only while a
 * run is in progress, mirroring `PetOverlayService`'s stateless-plumbing pattern — but this class
 * shares no code with it.
 */
class SpikeOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: ViewGroup? = null
    private var textField: EditText? = null
    private var mode: SpikeMode? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingAdd: Runnable? = null

    private val samples = mutableListOf<KeyboardGeometrySample>()
    private var windowAddedAtMillis: Long? = null
    private var startYPx: Int? = null
    private var originalYPx: Int? = null

    /** CONTROL ONLY: whether an inset dispatch ever reached this window, and its last ime() bottom. */
    private var controlImeInsetDispatchFired = false
    private var controlLastImeInsetBottomPx: Int? = null

    private var everReceivedWindowFocus = false

    /** Layout-driven sampling state. See [MAX_LAYOUT_DRIVEN_SAMPLES]. */
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var layoutDrivenSampleCount = 0
    private var layoutDrivenSampleCapReached = false

    /** Pet-follow state. All of it stays null/empty in every mode that adds no pet window. */
    private var petView: View? = null
    private var petAttachedOriginalYPx: Int? = null
    private var petReportedOriginalYPx: Int? = null
    private var petCurrentYPx: Int? = null
    private val petMoves = mutableListOf<PetFollowMove>()

    /**
     * Derives the keyboard displacement from the card's own content position and debounces it.
     * Recreated per run so nothing leaks between runs.
     */
    private var displacementTracker = ContentDisplacementTracker()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val requestedMode = intent.getStringExtra(EXTRA_MODE)?.let(SpikeMode::valueOf)
                if (requestedMode != null) startRun(requestedMode)
            }
            ACTION_FINISH -> finishRun()
        }
        return START_NOT_STICKY
    }

    private fun startRun(requestedMode: SpikeMode) {
        mode = requestedMode
        activeMode = requestedMode
        lastRunResult = null
        samples.clear()
        windowAddedAtMillis = null
        startYPx = null
        originalYPx = null
        controlImeInsetDispatchFired = false
        controlLastImeInsetBottomPx = null
        everReceivedWindowFocus = false
        layoutDrivenSampleCount = 0
        layoutDrivenSampleCapReached = false
        petAttachedOriginalYPx = null
        petReportedOriginalYPx = null
        petCurrentYPx = null
        petMoves.clear()
        displacementTracker = ContentDisplacementTracker()

        startForeground(
            NOTIFICATION_ID,
            buildNotification(requestedMode),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        // Gives the maintainer time to switch to the app under test (e.g. start a video) before
        // the window takes focus, so the delay itself is never mistaken for the measured cost.
        val addWindow = Runnable { addOverlayWindow(requestedMode) }
        pendingAdd = addWindow
        mainHandler.postDelayed(addWindow, START_DELAY_MILLIS)
    }

    private fun addOverlayWindow(runMode: SpikeMode) {
        val wm = windowManager ?: return

        val container = FocusTrackingContainer(applicationContext) { hasFocus ->
            if (hasFocus) everReceivedWindowFocus = true
        }

        // Every mode must LOOK like a run is in progress. Focus-only has no text field by
        // definition, but an empty container wraps to zero height and renders nothing at all — the
        // operator then has no way to tell the window is up, and no way to trust that the run
        // measured anything. This banner is the run's only visible evidence in that mode.
        container.addView(
            TextView(applicationContext).apply {
                text = getString(bannerStringFor(runMode), runMode.label)
                setBackgroundColor(BANNER_BACKGROUND)
                setTextColor(BANNER_TEXT)
                setPadding(BANNER_PADDING, BANNER_PADDING, BANNER_PADDING, BANNER_PADDING)
            },
        )

        var editText: EditText? = null
        if (runMode.raisesKeyboard) {
            val field = EditText(applicationContext).apply {
                hint = getString(R.string.spike_field_hint)
            }
            container.addView(field)
            editText = field
            textField = field

            // The inset listener stays attached as a CONTROL: issue #18 predicted that a
            // TYPE_APPLICATION_OVERLAY window is never told about the IME, the 2026-08-12 run
            // confirmed it, and a second device run should confirm it again rather than assume it.
            // Nothing else in this class reads these two fields to decide anything.
            ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
                controlImeInsetDispatchFired = true
                controlLastImeInsetBottomPx = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                insets
            }
        }

        // The starting y is derived from the measured window bounds, not from a literal, and is
        // null when the bounds could not be read at all rather than silently becoming zero (which
        // would put the card at the very top — the one position that makes the whole comparison
        // meaningless).
        val boundsHeight = runCatching { wm.currentWindowMetrics.bounds.height() }.getOrNull()
        val resolvedStartY = boundsHeight?.let(SpikeWindowParams::lowStartY)
        startYPx = resolvedStartY
        originalYPx = resolvedStartY

        val params = SpikeWindowParams.create(runMode, resolvedStartY ?: FALLBACK_START_Y_PX)

        val added = runCatching { wm.addView(container, params) }
            .onSuccess {
                overlayView = container
                windowAddedAtMillis = SystemClock.elapsedRealtime()
            }
            .onFailure { error ->
                Log.e(TAG, "addView failed: ${error.javaClass.simpleName}: ${error.message}")
                stopSelf()
            }
            .isSuccess
        if (!added) return

        attachLayoutListener(container)
        if (runMode.addsPetWindow) addPetWindow(boundsHeight)

        if (!runMode.raisesKeyboard) {
            container.post { takeSample(SamplePoint.BEFORE_FOCUS) }
            return
        }

        val field = editText ?: return

        field.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                if (runMode.repositionsOnFocus) moveWindowTo(SpikeWindowParams.ANCHOR_TOP_Y_PX)
                field.post { takeSample(SamplePoint.AFTER_FOCUS_GAINED) }
            } else if (runMode.repositionsOnFocus) {
                originalYPx?.let(::moveWindowTo)
            }
        }

        container.post {
            // Sampled before focus is requested, so there is a genuine baseline to compare the
            // keyboard-up frame against. Requesting focus in the constructor, as the previous
            // instrument did, destroyed that baseline before it could be read.
            takeSample(SamplePoint.BEFORE_FOCUS)
            field.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(field, InputMethodManager.SHOW_FORCED)
            mainHandler.postDelayed(
                { takeSample(SamplePoint.AFTER_SHOW_SOFT_INPUT) },
                IME_SETTLE_MILLIS,
            )
            mainHandler.postDelayed(
                { takeSample(SamplePoint.AFTER_LATE_SETTLE) },
                LATE_SETTLE_MILLIS,
            )
        }
    }

    /**
     * Takes a sample every time the overlay root lays out, so the moment the card's content actually
     * moves is observed rather than guessed at by a timer.
     *
     * ROUND 3: the cap now lives in [recordSample] and bounds the RECORD only. In round 2 the cap
     * sat here and short-circuited this listener, so once it was reached the pet stopped being
     * measured, moved and restored altogether — the run ended with the pet stranded because a limit
     * on the sample list had silently become a limit on the behaviour under test.
     */
    private fun attachLayoutListener(container: View) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            takeSample(SamplePoint.ON_LAYOUT_CHANGE)
        }
        layoutListener = listener
        container.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun detachLayoutListener() {
        val listener = layoutListener ?: return
        val view = overlayView
        if (view != null && view.viewTreeObserver.isAlive) {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
        layoutListener = null
    }

    /**
     * Adds the second window: a small, non-focusable, solid-colour block emulating the production
     * pet. It is placed deep inside the region a keyboard occupies, because a pet that starts above
     * the keyboard is never stranded and a run from there would prove nothing.
     */
    private fun addPetWindow(boundsHeightPx: Int?) {
        val wm = windowManager ?: return

        val resolvedY = boundsHeightPx?.let(SpikeWindowParams::petStartY)
        // Reported as null when the bounds could not be read, so a run that fell back to a literal
        // is visible as such in the findings instead of passing as a measured placement.
        petReportedOriginalYPx = resolvedY
        val attachedY = resolvedY ?: FALLBACK_PET_START_Y_PX
        petAttachedOriginalYPx = attachedY
        petCurrentYPx = attachedY

        val block = View(applicationContext).apply { setBackgroundColor(PET_BLOCK_COLOUR) }
        val params = SpikeWindowParams.createPetWindow(attachedY)

        runCatching { wm.addView(block, params) }
            .onSuccess { petView = block }
            .onFailure { error ->
                Log.e(TAG, "pet addView failed: ${error.javaClass.simpleName}: ${error.message}")
                petAttachedOriginalYPx = null
                petCurrentYPx = null
            }
    }

    /**
     * The card window is the measuring instrument, and the pet window moves by exactly what the card
     * measured — but the measurement is now the card's own CONTENT displacement, not its visible
     * display frame.
     *
     * ROUND 3, DO NOT RESTORE THE OLD DERIVATION. Round 2 ran the same mode twice on the same device
     * minutes apart. At 10:25:40 `visibleDisplayFrame` stayed `[0,130,1220,2660]` across all eleven
     * samples while `fieldBoundsOnScreen` moved from top 2510 to top 1577 at +3991ms: the keyboard
     * was up, the resize did happen, and the frame simply did not report it. At 10:26:58 the same
     * frame did shrink. `getWindowVisibleDisplayFrame` is therefore not a deterministic
     * keyboard-height source on this window class. The content displacement was 933px in BOTH runs.
     *
     * Nothing is derived that was not measured. An unreadable content top moves nothing, and a
     * displacement seen only once moves nothing either — see [ContentDisplacementTracker], where the
     * two-sample agreement rule lives. That rule is a debounce on acting, not a plausibility filter:
     * no value is rejected for being large and none is clamped.
     */
    private fun followKeyboardWithPet(sample: KeyboardGeometrySample) {
        if (petView == null) return
        val agreedDisplacement = displacementTracker.observe(sample) ?: return

        if (agreedDisplacement <= 0) {
            restorePet(sample.elapsedMillisSinceWindowAdded)
            return
        }

        val originalY = petAttachedOriginalYPx ?: return
        movePetTo(
            targetY = (originalY - agreedDisplacement).coerceAtLeast(0),
            measuredDisplacementPx = agreedDisplacement,
            elapsedMillis = sample.elapsedMillisSinceWindowAdded,
        )
    }

    /** Returns the pet to its starting y, but only if it was ever moved away from it. */
    private fun restorePet(elapsedMillis: Long) {
        val originalY = petAttachedOriginalYPx ?: return
        if (petCurrentYPx == originalY) return
        movePetTo(targetY = originalY, measuredDisplacementPx = 0, elapsedMillis = elapsedMillis)
    }

    private fun movePetTo(targetY: Int, measuredDisplacementPx: Int, elapsedMillis: Long) {
        if (petCurrentYPx == targetY) return
        val wm = windowManager ?: return
        val view = petView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.y = targetY
        val moved = runCatching { wm.updateViewLayout(view, params) }
            .onFailure { error -> Log.e(TAG, "pet updateViewLayout failed: ${error.message}") }
            .isSuccess
        if (!moved) return
        petCurrentYPx = targetY
        displacementTracker.markLastObservationCausedMove()
        // Bounds the RECORD only, and only after the move has already happened: the move list must
        // not grow without limit on a device whose content oscillates, but a full list must never
        // stop the pet from moving or from being restored.
        if (petMoves.size < MAX_RECORDED_PET_MOVES) {
            petMoves += PetFollowMove(
                elapsedMillisSinceWindowAdded = elapsedMillis,
                measuredDisplacementPx = measuredDisplacementPx,
                movedToYPx = targetY,
            )
        }
    }

    /** Assembles the pet-follow record from what was actually observed, and nothing else. */
    private fun buildPetFollowRecord(runMode: SpikeMode): PetFollowRecord {
        if (!runMode.addsPetWindow) return PetFollowRecord.NOT_APPLICABLE
        val observations = displacementTracker.observations()
        val maxAgreed = displacementTracker.maxAgreedDisplacementPx()
        val outcome = when {
            !displacementTracker.contentTopEverReadable() -> PetFollowOutcome.ContentTopNeverReadable
            maxAgreed != null -> PetFollowOutcome.DisplacementAgreed(maxAgreed)
            observations.none { it.displacementPx > 0 } -> PetFollowOutcome.NoDisplacementEverObserved
            else -> PetFollowOutcome.DisplacementsObservedButNoneAgreed
        }
        // Null, not false: the pet never moved, so restoration never applied. A `false` would claim
        // a restoration was attempted and failed.
        val restored = if (petMoves.isEmpty()) {
            null
        } else {
            petCurrentYPx != null && petCurrentYPx == petAttachedOriginalYPx
        }
        return PetFollowRecord(
            originalYPx = petReportedOriginalYPx,
            outcome = outcome,
            moves = petMoves.toList(),
            restoredToOriginalY = restored,
            observations = observations,
            observationsTruncated = displacementTracker.observationsTruncated(),
            baselineResets = displacementTracker.baselineResets(),
        )
    }

    /** Repositions the attached window. Records nothing by itself; the next sample observes it. */
    private fun moveWindowTo(y: Int) {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.y = y
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { error -> Log.e(TAG, "updateViewLayout failed: ${error.message}") }
    }

    private fun takeSample(point: SamplePoint) {
        val root: View = overlayView ?: return
        val addedAt = windowAddedAtMillis ?: return
        val sample = KeyboardGeometrySample.capture(
            point = point,
            elapsedMillisSinceWindowAdded = SystemClock.elapsedRealtime() - addedAt,
            rootView = root,
            field = textField,
            windowManager = windowManager,
            controlImeInsetBottomPx = controlLastImeInsetBottomPx,
        )
        recordSample(sample)
        // Every sample is also a reading of the card's content top, so the pet follows from all of
        // them — timed and layout-driven alike — rather than only from whichever moment was guessed
        // at. This runs unconditionally, INCLUDING after the record cap is reached.
        followKeyboardWithPet(sample)
    }

    /**
     * Appends a sample to the recorded series, subject to the layout-driven cap.
     *
     * The cap bounds only what is WRITTEN DOWN. A layout listener fires as often as the framework
     * decides to, so an uncapped series grows without bound and buries the interesting transition —
     * but the follow behaviour under test must keep running regardless, which is why the cap lives
     * here and not in the caller.
     */
    private fun recordSample(sample: KeyboardGeometrySample) {
        if (sample.point == SamplePoint.ON_LAYOUT_CHANGE) {
            if (layoutDrivenSampleCount >= MAX_LAYOUT_DRIVEN_SAMPLES) {
                layoutDrivenSampleCapReached = true
                return
            }
            layoutDrivenSampleCount++
        }
        samples += sample
    }

    /**
     * A configuration change invalidates the displacement baseline: it was established against the
     * previous geometry, and comparing a portrait baseline against landscape content tops turns an
     * orientation delta into a fake keyboard height. Round 2 had no reset, and the maintainer saw
     * exactly that — the pet moved on rotation, and after rotating back it stopped responding to the
     * keyboard entirely.
     *
     * A second, independent reset path lives in [ContentDisplacementTracker], which also resets when
     * the measured window bounds change orientation between two samples. Both are recorded in the
     * findings with the elapsed time and the cause, so which one fired is observable rather than
     * assumed.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        displacementTracker.requestBaselineReset("service onConfigurationChanged")
    }

    /**
     * Takes the final sample, removes the window and publishes the run's automatic findings.
     * `windowRemovedCleanly` is a best-effort marker: `removeView` completing without an exception,
     * not itself proof that focus returned to the app underneath — that still needs the human
     * confirmation collected by the activity.
     */
    private fun finishRun() {
        activeMode = null
        pendingAdd?.let(mainHandler::removeCallbacks)
        pendingAdd = null
        mainHandler.removeCallbacksAndMessages(null)

        takeSample(SamplePoint.AFTER_DISMISSAL)

        val wm = windowManager
        val view = overlayView
        var removedCleanly = true
        detachLayoutListener()
        if (wm != null && view != null) {
            removedCleanly = runCatching { wm.removeView(view) }.isSuccess
        }
        petView?.let { pet ->
            val petRemoved = runCatching { wm?.removeView(pet) }.isSuccess
            removedCleanly = removedCleanly && petRemoved
        }
        petView = null
        overlayView = null
        textField = null

        val runMode = mode
        if (runMode != null) {
            lastRunResult = SpikeRunResult(
                mode = runMode,
                startYPx = startYPx,
                samples = samples.toList(),
                geometrySignal = KeyboardGeometrySignal.from(samples),
                controlImeInsetDispatchFired = controlImeInsetDispatchFired,
                windowEverReceivedFocus = everReceivedWindowFocus,
                windowRemovedCleanly = removedCleanly,
                layoutDrivenSampleCapReached = layoutDrivenSampleCapReached,
                petFollow = buildPetFollowRecord(runMode),
            )
            val result = Intent(ACTION_RUN_FINISHED).apply {
                setPackage(packageName)
                putExtra(EXTRA_MODE, runMode.name)
            }
            sendBroadcast(result)
        }
        mode = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        pendingAdd?.let(mainHandler::removeCallbacks)
        detachLayoutListener()
        overlayView?.let { view -> runCatching { windowManager?.removeView(view) } }
        petView?.let { pet -> runCatching { windowManager?.removeView(pet) } }
        petView = null
        overlayView = null
        textField = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun bannerStringFor(runMode: SpikeMode): Int = when (runMode) {
        SpikeMode.FOCUS_ONLY -> R.string.spike_banner_focus_only
        SpikeMode.FULL_IME -> R.string.spike_banner_full_ime
        SpikeMode.TWO_WINDOW_PET_FOLLOW -> R.string.spike_banner_two_window
        else -> R.string.spike_banner_strategy
    }

    private fun buildNotification(runMode: SpikeMode): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "IME viability spike",
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }

        val finishIntent = Intent(this, SpikeOverlayService::class.java).setAction(ACTION_FINISH)
        val finishPendingIntent = PendingIntent.getService(
            this,
            0,
            finishIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("IME spike running: ${runMode.label}")
            .setContentText("Tap Finish in the app to end this run.")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .addAction(0, "Finish", finishPendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        /**
         * The mode of the run currently in progress, or null when none is. The activity reads this
         * on every resume rather than trusting its own remembered state: the measurement REQUIRES
         * leaving the app for the one under test and coming back, and a composable's `remember`
         * does not survive that. Without this, the operator returns to an idle-looking screen while
         * the overlay is still up, can never reach Finish, and no findings are ever written.
         */
        @Volatile
        var activeMode: SpikeMode? = null
            private set

        /**
         * The last finished run's automatic findings, or null if none has finished since this
         * process started. Null means "nothing was published", which the activity records as an
         * absent measurement rather than as an empty one.
         */
        @Volatile
        var lastRunResult: SpikeRunResult? = null
            private set

        const val ACTION_START = "com.petmephone.spike.imeviability.action.START"
        const val ACTION_FINISH = "com.petmephone.spike.imeviability.action.FINISH"
        const val ACTION_RUN_FINISHED = "com.petmephone.spike.imeviability.action.RUN_FINISHED"

        const val EXTRA_MODE = "mode"

        private const val TAG = "SpikeOverlayService"
        private const val CHANNEL_ID = "spike_run"
        private const val NOTIFICATION_ID = 1
        private const val START_DELAY_MILLIS = 3000L

        /** How long the keyboard is given to finish animating before the keyboard-up sample. */
        private const val IME_SETTLE_MILLIS = 900L

        /**
         * A second, deliberately late sample.
         *
         * This is a MEASUREMENT WINDOW, not a tuned value: round 1 saw the frame reduction only at
         * +13609ms and never at +938ms, which leaves "the resize is slower than 938ms" and "the
         * sample was taken too early" indistinguishable. Sampling again this far out separates
         * them. Nothing in production should ever adopt this number as a delay.
         */
        private const val LATE_SETTLE_MILLIS = 2500L

        /**
         * Upper bound on RECORDED layout-driven samples in one run. A global-layout listener fires
         * as often as the framework decides to, so without a cap the sample list grows without bound
         * and the interesting transition is buried. Reaching the cap is recorded in the findings, so
         * a truncated series is never mistaken for one that stopped changing.
         *
         * It bounds recording only. The measuring, debouncing, moving and restoring all continue
         * after it is reached — see [recordSample].
         */
        private const val MAX_LAYOUT_DRIVEN_SAMPLES = 20

        /** Upper bound on RECORDED pet moves. Like the sample cap, it never stops a move. */
        private const val MAX_RECORDED_PET_MOVES = 60

        /**
         * Used only when the window bounds could not be read at all. Like the card's fallback it is
         * still a LOW position, and the findings report the pet's original y as "not measured" so
         * the fallback is visible rather than passing as a measurement.
         */
        private const val FALLBACK_PET_START_Y_PX = 1800

        /** Opaque and loud: the pet's position must be unambiguous on a screenshot. */
        private const val PET_BLOCK_COLOUR = 0xFFE0651A.toInt()

        /**
         * Used only when the window bounds could not be read at all. It is still a LOW position, so
         * a run that falls back is not silently converted into a top-anchored run that would prove
         * nothing; the findings record the start y as "not measured" so the fallback is visible.
         */
        private const val FALLBACK_START_Y_PX = 1200

        private const val BANNER_BACKGROUND = 0xCC1B1A17.toInt()
        private const val BANNER_TEXT = 0xFFFFFFFF.toInt()
        private const val BANNER_PADDING = 32

        fun startIntent(context: Context, mode: SpikeMode): Intent =
            Intent(context, SpikeOverlayService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MODE, mode.name)

        fun finishIntent(context: Context): Intent =
            Intent(context, SpikeOverlayService::class.java).setAction(ACTION_FINISH)
    }
}
