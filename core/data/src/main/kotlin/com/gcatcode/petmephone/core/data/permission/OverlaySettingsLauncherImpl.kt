package com.gcatcode.petmephone.core.data.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.gcatcode.petmephone.core.domain.permission.OverlaySettingsLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The only place `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` is constructed. `FLAG_ACTIVITY_NEW_TASK`
 * is required because this is launched from the application context, which has no task of its own.
 * `runCatching` mirrors the pre-existing call site this replaces (`MainActivity`'s own launch,
 * before this interface existed): a device without a Settings activity for this action must not
 * crash the caller.
 */
class OverlaySettingsLauncherImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : OverlaySettingsLauncher {

    override fun launchOverlaySettings() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.fromParts("package", context.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
