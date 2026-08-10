package com.gcatcode.petmephone.core.data.permission

import android.content.Context
import android.provider.Settings
import com.gcatcode.petmephone.core.domain.permission.OverlayPermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The only place `Settings.canDrawOverlays` is called. Deliberately does no caching: this is a
 * cheap synchronous `AppOpsManager` query, and the grant can be revoked outside the app's process
 * with no callback, so every call re-queries the system live.
 */
class OverlayPermissionCheckerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : OverlayPermissionChecker {

    override fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)
}
