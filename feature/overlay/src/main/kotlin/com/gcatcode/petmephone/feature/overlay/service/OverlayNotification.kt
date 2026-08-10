package com.gcatcode.petmephone.feature.overlay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * The foreground-service notification for the overlay. Importance `LOW`, no sound, no vibration —
 * per the cross-cutting rule, this app never designs a notification to pull the user back in. It
 * exists purely so the OS-mandated foreground-service notice is honest about what is running:
 * "the floating pet is on screen," nothing more.
 */
internal object OverlayNotification {
    const val CHANNEL_ID = "overlay_service"
    const val NOTIFICATION_ID = 1

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(CHANNEL_ID, "Floating pet", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown only while the floating pet overlay is on screen."
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context): Notification {
        ensureChannel(context)
        return Notification.Builder(context, CHANNEL_ID)
            .setContentTitle("Pet is on screen")
            .setContentText("Tap to open PetMePhone.")
            // Placeholder system icon; real iconography belongs to the design-system module and is
            // out of scope for this issue.
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }
}
