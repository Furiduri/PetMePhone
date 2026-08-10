package com.gcatcode.petmephone.core.domain.pet.state

/**
 * The present-tense input to [PetStateResolver]. Every field here must have a real producer in
 * the slice that adds it — a `false` with no producer would be a fabricated present-tense claim.
 * See `design.md` decision 1.
 *
 * [isDragging] is the only field with a producer in this slice ([DraggingStateProvider] reads it
 * from `DragStateRepository`, not from this class directly). No `Instant`, duration, or event
 * list belongs here: this snapshot is deliberately not time-aware.
 */
data class PetSnapshot(val isDragging: Boolean)
