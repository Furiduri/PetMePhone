package com.gcatcode.petmephone.feature.overlay.service

import android.app.AppOpsManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.gcatcode.petmephone.core.domain.overlay.OverlayPosition
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionRepository
import com.gcatcode.petmephone.core.domain.permission.OverlayPermissionChecker
import com.gcatcode.petmephone.feature.overlay.ui.ComposeOverlayHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

    private var serviceScope: CoroutineScope? = null
    private var positionCollectionJob: Job? = null

    // Framework plumbing to the current window only — see the class kdoc's statelessness rule.
    private var overlayView: ComposeOverlayHost? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    private var appOpsListener: AppOpsManager.OnOpChangedListener? = null

    override fun onCreate() {
        super.onCreate()
        // Must run before any I/O and within 5s of service start, or the app crashes with
        // ForegroundServiceDidNotStartInTimeException. The type is the parameterised constant
        // FOREGROUND_SERVICE_TYPE, matching the manifest's specialUse declaration, per the type
        // decision recorded in issue #9.
        val notification = OverlayNotification.build(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(OverlayNotification.NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE)
        } else {
            startForeground(OverlayNotification.NOTIFICATION_ID, notification)
        }
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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
                positionRepository.position.collect { position -> applyPosition(position) }
            }
        }

        // START_STICKY redelivers a null Intent; onStartCommand already reconstructs everything
        // above from persisted/live state alone, with no Intent extras required.
        return START_STICKY
    }

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
        val view = ComposeOverlayHost(applicationContext, content = { OverlayPlaceholder() })
        val params = OverlayWindowParams.create(position)

        runCatching { windowManager.addView(view, params) }
            .onSuccess {
                overlayView = view
                overlayParams = params
            }
            .onFailure { error ->
                Log.e(TAG, "addView failed: ${error.javaClass.simpleName}: ${error.message}")
                stopSelf()
            }
    }

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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val params = overlayParams
        val view = overlayView
        if (params != null && view != null) {
            val (width, height) = screenBoundsPx()
            OverlayWindowParams.clampToBounds(params, width, height)
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    @Suppress("DEPRECATION")
    private fun screenBoundsPx(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = android.util.DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }

    // Swiping the app out of recents must not kill the pet — the whole point of a floating
    // companion is that it survives that. Deliberately does nothing.
    override fun onTaskRemoved(rootIntent: Intent?) = Unit

    override fun onDestroy() {
        unregisterRevocationWatcher()
        positionCollectionJob = null
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

/**
 * Minimal placeholder content proving the composition actually renders (#14). Replaced by the
 * real pet composable once one exists; deliberately not a full-screen opaque background so the
 * overlay's own bounds stay visually distinguishable.
 */
@Composable
private fun OverlayPlaceholder() {
    Surface(color = Color.Transparent) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Magenta),
        )
    }
}
