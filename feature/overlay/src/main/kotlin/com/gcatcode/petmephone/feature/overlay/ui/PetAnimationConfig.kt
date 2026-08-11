package com.gcatcode.petmephone.feature.overlay.ui

/**
 * Tunable values for the pet's animation clock and its reactive state. Provided via Hilt in
 * `OverlayModule` — never a literal inside the clock composable or `PetOverlayStateHolder`
 * itself (the balance-values-are-injected-config rule).
 */
data class PetAnimationConfig(
    /**
     * Per-frame fallback, used only for a character that declares no cycle duration of its own.
     * Per frame rather than per cycle because without a declaration there is no stated intent about
     * how long the loop should take, and holding the frame rate steady is the honest default.
     */
    val frameIntervalMillis: Long,
    /**
     * Floor on the interval derived from a declared cycle duration. A manifest asking for a 10 ms
     * cycle across 12 frames would otherwise mean a zero-delay loop spinning the frame clock as
     * fast as the CPU allows.
     */
    val minFrameIntervalMillis: Long,
    /** `WhileSubscribed` timeout for [PetOverlayStateHolder]'s `sheets`/`petState` sharing. */
    val stateSharingTimeoutMillis: Long,
)
