package com.gcatcode.petmephone.core.domain.overlay

import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory drag flag. This is not persisted: whether the pet is currently being dragged has no
 * meaning after the process dies, so a `StateFlow` is the whole contract.
 *
 * Implementation lands in `:core:data` alongside its first writer (`PetTouchController`).
 */
interface DragStateRepository {
    val isDragging: StateFlow<Boolean>
    fun set(dragging: Boolean)
}
