package com.gcatcode.petmephone.core.domain.overlay

import kotlinx.coroutines.flow.Flow

/**
 * Persisted overlay window position, stored as a fraction of the travel range
 * ([OverlayPositionFraction], design decision 3/4) — never pixels. `PetOverlayService` converts to
 * pixels against live bounds and mirrors the result into `LayoutParams`; it never holds an
 * in-memory field as the authoritative value (issue #13's statelessness rule).
 */
interface OverlayPositionRepository {
    /**
     * Emits `null` when no position has ever been persisted, rather than a stand-in coordinate.
     *
     * A default fraction would make "the user has never moved the pet" and "the user parked the
     * pet at exactly that fraction" the same value, and the placement rule for the two is not the
     * same: an unplaced pet belongs in its resting corner, wherever that lands on this screen,
     * while a placed one belongs exactly where it was left. Only the caller knows the screen, so
     * only the caller can resolve the first case — and it can only do that if it can tell them
     * apart. This is the project's absence rule applied to coordinates.
     */
    val position: Flow<OverlayPositionFraction?>

    /**
     * Persists the resting position of a completed drag gesture. Called at most once per gesture,
     * after the snap animation settles (`[POS-3]`) — never from `ACTION_MOVE` or an intermediate
     * animation frame.
     */
    suspend fun save(position: OverlayPositionFraction)
}
