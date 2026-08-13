package com.petmephone.spike.imeviability

import android.os.Build

/**
 * Captured automatically so two devices' findings can be told apart without the maintainer
 * labelling them by hand.
 */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val apiLevel: Int,
) {
    companion object {
        fun capture(): DeviceInfo = DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidRelease = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
        )
    }
}
