package com.gcatcode.petmephone

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.gcatcode.petmephone.core.domain.permission.OverlayPermissionChecker
import com.gcatcode.petmephone.core.domain.permission.OverlaySettingsLauncher
import com.gcatcode.petmephone.feature.overlay.service.PetOverlayService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Entry point, still deliberately without UI — the real screens arrive with the app shell (#40).
 *
 * What it does own is the only way to switch the pet on. Until this existed, the overlay could
 * only be started with `adb`, which meant it survived exactly one install: any grant that lapsed
 * left no way back, and reopening the app did nothing at all.
 *
 * Starting the service on every `onStart` is intentional and cheap. `PetOverlayService` is
 * idempotent — `onStartCommand` reconstructs everything from live and persisted state and adds no
 * second window when one already exists — so this is a resume, not a duplicate.
 *
 * A proper onboarding flow that explains the permission before asking for it is #12. This is the
 * minimum that makes the pet reachable, not that flow.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var overlayPermissionChecker: OverlayPermissionChecker

    @Inject
    lateinit var overlaySettingsLauncher: OverlaySettingsLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()

        // Queried live, never remembered: on some devices the grant is revoked by the system
        // itself, not by the user, so a cached answer would be wrong within seconds.
        if (overlayPermissionChecker.canDrawOverlays()) {
            startService(Intent(this, PetOverlayService::class.java))
            return
        }

        // No silent failure. Without the grant the pet cannot exist, and a blank screen that does
        // nothing is the worst possible way to say so.
        Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_LONG).show()
        overlaySettingsLauncher.launchOverlaySettings()
    }
}
