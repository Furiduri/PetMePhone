package com.gcatcode.petmephone.spike

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * SPIKE — issue #9 only. Delete once the foreground service question is decided.
 *
 * Answers one question empirically: does a `TYPE_APPLICATION_OVERLAY` window survive screen-off,
 * Doze and memory pressure when its hosting process runs NO foreground service? Issue #9's option 3
 * depends on the answer, and no documentation settles it.
 *
 * The window is a plain coloured square, not a pet. It exists to be watched, not looked at.
 *
 * The heartbeat is the actual instrument. A window disappearing tells you nothing about why; a
 * heartbeat that stops tells you the process died, and one that keeps ticking while the window is
 * gone tells you the window was removed while the process lived. Those are different outcomes with
 * different consequences for the decision.
 */
class OverlaySpikeService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var beats = 0

    private val heartbeat = object : Runnable {
        override fun run() {
            beats++
            Log.i(TAG, "heartbeat=$beats pid=${Process.myPid()} attached=${overlay?.isAttachedToWindow}")
            handler.postDelayed(this, HEARTBEAT_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate pid=${Process.myPid()} sdk=${Build.VERSION.SDK_INT} — NO foreground service is started")

        val windowManager = getSystemService(WindowManager::class.java)
        val view = View(this).apply { setBackgroundColor(Color.MAGENTA) }
        val params = WindowManager.LayoutParams(
            SIZE_PX,
            SIZE_PX,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        runCatching { windowManager.addView(view, params) }
            .onSuccess {
                overlay = view
                Log.i(TAG, "overlay added")
            }
            .onFailure { Log.e(TAG, "overlay NOT added: ${it.javaClass.simpleName}: ${it.message}") }

        handler.post(heartbeat)
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy pid=${Process.myPid()} beats=$beats")
        handler.removeCallbacks(heartbeat)
        overlay?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        overlay = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val TAG = "OverlaySpike"
        const val HEARTBEAT_MILLIS = 5_000L
        const val SIZE_PX = 220
    }
}
