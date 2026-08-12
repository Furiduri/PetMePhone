package com.gcatcode.petmephone.core.data.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DragStateRepositoryImplTest {

    @Test
    fun `isDragging is false before any drag starts`() {
        val repository = DragStateRepositoryImpl()

        assertFalse(repository.isDragging.value)
    }

    @Test
    fun `isDragging reports true only between drag start and drag end`() {
        val repository = DragStateRepositoryImpl()

        repository.set(true)
        assertEquals(true, repository.isDragging.value)

        repository.set(false)
        assertEquals(false, repository.isDragging.value)
    }

    @Test
    fun `set never touches any persistence dependency - repository has none`() {
        // Structural proof: DragStateRepositoryImpl's constructor takes no DataStore, no DAO, no
        // repository dependency at all — only a MutableStateFlow. Compiling this test with a
        // no-arg constructor call is itself the assertion.
        val repository = DragStateRepositoryImpl()

        repository.set(true)

        assertEquals(true, repository.isDragging.value)
    }
}
