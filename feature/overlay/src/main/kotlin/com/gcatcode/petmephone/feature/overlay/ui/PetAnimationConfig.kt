package com.gcatcode.petmephone.feature.overlay.ui

/**
 * Tunable values for the IDLE animation clock. Provided via Hilt in `OverlayModule` — never a
 * literal inside the clock composable itself (the balance-values-are-injected-config rule).
 */
data class PetAnimationConfig(
    val frameIntervalMillis: Long,
)
