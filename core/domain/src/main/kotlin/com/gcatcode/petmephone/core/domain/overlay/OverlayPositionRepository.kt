package com.gcatcode.petmephone.core.domain.overlay

import kotlinx.coroutines.flow.Flow

/**
 * Persisted overlay window position. `PetOverlayService` collects [position] and mirrors it into
 * `LayoutParams`; it never holds an in-memory field as the authoritative value (issue #13's
 * statelessness rule). Writing a new position is the drag issue's concern and is deliberately not
 * part of this interface yet.
 */
interface OverlayPositionRepository {
    val position: Flow<OverlayPosition>
}
