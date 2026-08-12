package com.gcatcode.petmephone.core.domain.pet.state

/**
 * Highest-priority provider in this slice: while dragging is active, the resolver always reports
 * [PetState.DRAGGING] regardless of any other state a future slice may add.
 *
 * `evaluate` reads only its [PetSnapshot] argument, per [PetStateProvider]'s purity rule — the
 * `DragStateRepository` value is read by the caller that builds the snapshot, never by this class.
 */
class DraggingStateProvider : PetStateProvider {
    override val priority: Int = PRIORITY

    override fun evaluate(snapshot: PetSnapshot): PetState? =
        if (snapshot.isDragging) PetState.DRAGGING else null

    companion object {
        const val PRIORITY = 100
    }
}
