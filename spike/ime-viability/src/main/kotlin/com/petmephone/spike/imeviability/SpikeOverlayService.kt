package com.petmephone.spike.imeviability

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * A plain [FrameLayout] with one addition: it reports every `onWindowFocusChanged` callback the
 * framework sends it, so "did this window ever actually receive focus" is a real, observed signal
 * rather than an assumption baked into the window flags alone.
 */
private class FocusTrackingContainer(
    context: Context,
    private val onFocusChanged: (hasFocus: Boolean) -> Unit,
) : FrameLayout(context) {
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        onFocusChanged(hasWindowFocus)
    }
}

/**
 * Owns the measurement window's whole lifecycle for one run: adds it (after a short delay so the
 * maintainer can switch to the app under test, e.g. one playing a video), tracks the automatic
 * signals a program can observe, and removes it again. Runs as a foreground service only while a
 * run is in progress, mirroring `PetOverlayService`'s stateless-plumbing pattern — but this class
 * shares no code with it.
 */
class SpikeOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var mode: SpikeMode? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingAdd: Runnable? = null

    private var imeInsetCallbackFired = false
    private var keyboardAppeared = false
    private var keyboardCoversField = false
    private var everReceivedWindowFocus = false

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
        imeInsetCallbackFired = false
        keyboardAppeared = false
        keyboardCoversField = false
        everReceivedWindowFocus = false

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
        if (runMode == SpikeMode.FULL_IME) {
            val field = EditText(applicationContext).apply {
                hint = "Spike text field"
                requestFocus()
            }
            container.addView(field)
            ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
                imeInsetCallbackFired = true
                val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                keyboardAppeared = imeInsets.bottom > 0
                if (keyboardAppeared) {
                    val fieldBottom = IntArray(2).also { field.getLocationOnScreen(it) }[1] + field.height
                    val screenHeight = view.rootView.height
                    keyboardCoversField = fieldBottom > (screenHeight - imeInsets.bottom)
                }
                insets
            }
        }

        val params = SpikeWindowParams.create()

        runCatching { wm.addView(container, params) }
            .onSuccess { overlayView = container }
            .onFailure { error ->
                Log.e(TAG, "addView failed: ${error.javaClass.simpleName}: ${error.message}")
                stopSelf()
            }

        if (runMode == SpikeMode.FULL_IME) {
            container.post {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(container.getChildAt(0), android.view.inputmethod.InputMethodManager.SHOW_FORCED)
            }
        }
    }

    /**
     * Removes the window and reports the run's automatic findings. `windowRemovedCleanly` is a
     * best-effort marker: `removeView` completing without an exception, not itself proof that
     * focus returned to the app underneath — that still needs the human confirmation collected by
     * the activity.
     */
    private fun finishRun() {
        pendingAdd?.let(mainHandler::removeCallbacks)
        pendingAdd = null

        val wm = windowManager
        val view = overlayView
        var removedCleanly = true
        if (wm != null && view != null) {
            removedCleanly = runCatching { wm.removeView(view) }.isSuccess
        }
        overlayView = null

        val runMode = mode
        if (runMode != null) {
            val result = Intent(ACTION_RUN_FINISHED).apply {
                setPackage(packageName)
                putExtra(EXTRA_MODE, runMode.name)
                putExtra(EXTRA_KEYBOARD_APPEARED, keyboardAppeared)
                putExtra(EXTRA_KEYBOARD_COVERS_FIELD, keyboardCoversField)
                putExtra(EXTRA_IME_CALLBACK_FIRED, imeInsetCallbackFired)
                putExtra(EXTRA_REMOVED_CLEANLY, removedCleanly)
                putExtra(EXTRA_EVER_RECEIVED_FOCUS, everReceivedWindowFocus)
            }
            sendBroadcast(result)
        }
        mode = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        pendingAdd?.let(mainHandler::removeCallbacks)
        overlayView?.let { view -> runCatching { windowManager?.removeView(view) } }
        overlayView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
        const val ACTION_START = "com.petmephone.spike.imeviability.action.START"
        const val ACTION_FINISH = "com.petmephone.spike.imeviability.action.FINISH"
        const val ACTION_RUN_FINISHED = "com.petmephone.spike.imeviability.action.RUN_FINISHED"

        const val EXTRA_MODE = "mode"
        const val EXTRA_KEYBOARD_APPEARED = "keyboardAppeared"
        const val EXTRA_KEYBOARD_COVERS_FIELD = "keyboardCoversField"
        const val EXTRA_IME_CALLBACK_FIRED = "imeCallbackFired"
        const val EXTRA_REMOVED_CLEANLY = "removedCleanly"
        const val EXTRA_EVER_RECEIVED_FOCUS = "everReceivedFocus"

        private const val TAG = "SpikeOverlayService"
        private const val CHANNEL_ID = "spike_run"
        private const val NOTIFICATION_ID = 1
        private const val START_DELAY_MILLIS = 3000L

        fun startIntent(context: Context, mode: SpikeMode): Intent =
            Intent(context, SpikeOverlayService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MODE, mode.name)

        fun finishIntent(context: Context): Intent =
            Intent(context, SpikeOverlayService::class.java).setAction(ACTION_FINISH)
    }
}
