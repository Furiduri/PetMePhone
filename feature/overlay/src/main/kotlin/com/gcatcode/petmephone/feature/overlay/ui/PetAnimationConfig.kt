package com.gcatcode.petmephone.feature.overlay.ui

import com.gcatcode.petmephone.core.domain.config.ConfigField
import com.gcatcode.petmephone.core.domain.config.ConfigGroup

/**
 * Tunable values for the pet's animation clock and its reactive state. Provided via Hilt in
 * `OverlayModule` — never a literal inside the clock composable or `PetOverlayStateHolder`
 * itself (the balance-values-are-injected-config rule).
 */
data class PetAnimationConfig(
    /**
     * How long a frame is held for a character that declares no `frameDurationMillis` of its own.
     * Same unit as the declaration, so an undeclared character simply runs at the app's default
     * pace and its animation's length still follows from its frame count.
     */
    val frameIntervalMillis: Long,
    /**
     * Floor on a declared frame duration. A manifest asking for 1 ms a frame would otherwise drive
     * the clock faster than the display can show, spending CPU on frames nobody sees.
     */
    val minFrameIntervalMillis: Long,
    /** `WhileSubscribed` timeout for [PetOverlayStateHolder]'s `sheets`/`petState` sharing. */
    val stateSharingTimeoutMillis: Long,
) {
    companion object {
        /** This config's override group. No staleness notion (design decision 3, 2a). */
        val GROUP = ConfigGroup(id = "pet_animation", currentVersion = null)

        /** Range for [frameIntervalMillis]; floored at one 60 Hz frame, same floor as [MIN_FRAME_INTERVAL_MILLIS]. */
        val FRAME_INTERVAL_MILLIS = ConfigField.LongField("config_override.pet_animation.frame_interval_millis", GROUP, 150L, 16L, 2_000L)

        /** Range for [minFrameIntervalMillis]; the floor itself never drops below one 60 Hz frame. */
        val MIN_FRAME_INTERVAL_MILLIS = ConfigField.LongField("config_override.pet_animation.min_frame_interval_millis", GROUP, 16L, 16L, 1_000L)

        /** Range for [stateSharingTimeoutMillis]; see that field's KDoc for the semantics. */
        val STATE_SHARING_TIMEOUT_MILLIS = ConfigField.LongField("config_override.pet_animation.state_sharing_timeout_millis", GROUP, 5_000L, 0L, 60_000L)

        /** Registry consumed by #92's tuning panel and by the resolution/registry-invariant tests. */
        val ALL: List<ConfigField<*>> = listOf(FRAME_INTERVAL_MILLIS, MIN_FRAME_INTERVAL_MILLIS, STATE_SHARING_TIMEOUT_MILLIS)
    }
}
