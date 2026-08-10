package com.gcatcode.petmephone.core.domain.overlay

import kotlinx.coroutines.flow.Flow

/**
 * Persisted overlay window position. `PetOverlayService` collects [position] and mirrors it into
 * `LayoutParams`; it never holds an in-memory field as the authoritative value (issue #13's
 * statelessness rule). Writing a new position is the drag issue's concern and is deliberately not
 * part of this interface yet.
 */
interface OverlayPositionRepository {
    /**
     * Emits `null` when no position has ever been persisted, rather than a stand-in coordinate.
     *
     * A default pair of pixels would make "the user has never moved the pet" and "the user parked
     * the pet at exactly those pixels" the same value, and the placement rule for the two is not
     * the same: an unplaced pet belongs in its resting corner, wherever that lands on this screen,
     * while a placed one belongs exactly where it was left. Only the caller knows the screen, so
     * only the caller can resolve the first case — and it can only do that if it can tell them
     * apart. This is the project's absence rule applied to coordinates.
     */
    val position: Flow<OverlayPosition?>
}
