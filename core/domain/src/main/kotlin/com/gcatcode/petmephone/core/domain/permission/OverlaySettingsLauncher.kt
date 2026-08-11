package com.gcatcode.petmephone.core.domain.permission

/**
 * The only interface allowed to start the `SYSTEM_ALERT_WINDOW` special-access Settings screen.
 * UI code depends on this, never on `android.provider.Settings`/`Intent` directly, so "the screen
 * never constructs a direct Settings intent" is a structural property any caller can verify by
 * grepping its own source file for `Intent(Settings`.
 *
 * Lives in `:core:domain` with no Android import, per the dependency-injection spec; the
 * Android-backed implementation lives in `:core:data`.
 */
interface OverlaySettingsLauncher {
    fun launchOverlaySettings()
}
