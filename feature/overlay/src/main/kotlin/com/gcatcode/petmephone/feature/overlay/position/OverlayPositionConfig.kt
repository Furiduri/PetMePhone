package com.gcatcode.petmephone.feature.overlay.position

/**
 * Injected, never a literal inside [com.gcatcode.petmephone.feature.overlay.service.PetOverlayService]
 * (`[POS-6]`). Bounds how long the service will suspend on the first position read before falling
 * back to the computed resting corner — a stored value and a timeout collapse to the same
 * "no fabricated coordinate" branch either way.
 */
data class OverlayPositionConfig(val firstReadTimeoutMillis: Long)
