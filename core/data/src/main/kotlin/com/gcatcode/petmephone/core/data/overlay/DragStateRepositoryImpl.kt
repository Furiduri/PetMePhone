package com.gcatcode.petmephone.core.data.overlay

import com.gcatcode.petmephone.core.domain.overlay.DragStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory only implementation of [DragStateRepository]. Never touches DataStore or any other
 * persistent store — whether the pet is currently dragged has no meaning after the process dies.
 */
@Singleton
class DragStateRepositoryImpl @Inject constructor() : DragStateRepository {

    private val _isDragging = MutableStateFlow(false)
    override val isDragging: StateFlow<Boolean> = _isDragging

    override fun set(dragging: Boolean) {
        _isDragging.value = dragging
    }
}
