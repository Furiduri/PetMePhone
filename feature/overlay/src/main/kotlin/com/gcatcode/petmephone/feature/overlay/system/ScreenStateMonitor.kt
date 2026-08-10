package com.gcatcode.petmephone.feature.overlay.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import com.gcatcode.petmephone.feature.overlay.di.OverlayApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the screen-on/off signal the animation clock suspends on. Registers a runtime
 * [BroadcastReceiver] for `ACTION_SCREEN_ON`/`ACTION_SCREEN_OFF` — never a manifest receiver,
 * since those actions are never delivered to one (the rule recorded in `slice-1-foundation`).
 *
 * PR 0's spike (issue #36) confirmed this trigger source works reliably from a foreground-service
 * context with sub-200ms latency, so no `DisplayManager.DisplayListener` fallback is needed.
 */
@Singleton
class ScreenStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    @OverlayApplicationScope coroutineScope: CoroutineScope,
) {
    val screenOn: StateFlow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context, intent: Intent) {
                trySend(intent.action == Intent.ACTION_SCREEN_ON)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(receiver, filter)
        awaitClose { context.unregisterReceiver(receiver) }
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(),
        // Seeded from the real system state, never assumed true — the seed the design mandates.
        initialValue = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true,
    )
}
