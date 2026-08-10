package com.gcatcode.petmephone.core.domain.permission

/**
 * Live query over the `SYSTEM_ALERT_WINDOW` special app access. `SYSTEM_ALERT_WINDOW` can be
 * revoked from system Settings at any moment, outside the app's process, with no broadcast and no
 * callback — so this MUST be queried at every point of use (before every overlay `addView`, on
 * every service start, on every `onResume`) and its result MUST NEVER be persisted anywhere.
 *
 * Lives in `:core:domain` with no Android import, per the dependency-injection spec; the
 * Android-backed implementation lives in `:core:data`.
 */
interface OverlayPermissionChecker {
    fun canDrawOverlays(): Boolean
}
