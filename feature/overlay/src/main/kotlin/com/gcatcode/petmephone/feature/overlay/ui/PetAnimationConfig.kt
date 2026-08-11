package com.gcatcode.petmephone.feature.overlay.ui

/**
 * Tunable values for the pet's animation clock and its reactive state. Provided via Hilt in
 * `OverlayModule` — never a literal inside the clock composable or `PetOverlayStateHolder`
 * itself (the balance-values-are-injected-config rule).
 */
data class PetAnimationConfig(
    val frameIntervalMillis: Long,
    /** `WhileSubscribed` timeout for [PetOverlayStateHolder]'s `sheets`/`petState` sharing. */
    val stateSharingTimeoutMillis: Long,
)
