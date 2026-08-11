package com.gcatcode.petmephone.feature.overlay.position

import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionFraction
import com.gcatcode.petmephone.core.domain.overlay.OverlayPositionRepository
import com.gcatcode.petmephone.feature.overlay.di.OverlayApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Write-at-rest, cancellable (`[POS-3]` `[POS-4]`). Scoped to `@OverlayApplicationScope` — the
 * existing `@Singleton` process-lifetime scope — rather than the service's own scope (design
 * decision 7): a window torn down one tick after the snap settles must still persist where the
 * user left the pet. Cancellation is explicit ([cancelPending], called on drag start), never a
 * lifecycle side effect of the service being destroyed.
 *
 * [writeAtRest] is the only call site in this module that invokes
 * [OverlayPositionRepository.save]; nothing here is reachable from `ACTION_MOVE` or an intermediate
 * snap-animation frame.
 */
@Singleton
class PositionWriter @Inject constructor(
    private val repository: OverlayPositionRepository,
    @OverlayApplicationScope private val scope: CoroutineScope,
) {

    private var pendingWrite: Job? = null

    /**
     * Cancels any in-flight write from a previous gesture. Called when a new drag starts, so a
     * write superseded mid-flight never lands (`[POS-4]`).
     */
    fun cancelPending() {
        pendingWrite?.cancel()
        pendingWrite = null
    }

    /**
     * Persists the resting position of a just-completed gesture. Cancels any prior pending write
     * first, then launches exactly one new write — one `DataStore.edit` per completed gesture
     * (`[POS-3]`).
     */
    fun writeAtRest(fraction: OverlayPositionFraction) {
        cancelPending()
        pendingWrite = scope.launch { repository.save(fraction) }
    }
}
