package com.gcatcode.petmephone.feature.overlay.ui

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
)
