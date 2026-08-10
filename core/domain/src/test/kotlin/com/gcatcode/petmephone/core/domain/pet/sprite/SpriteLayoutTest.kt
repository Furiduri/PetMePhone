package com.gcatcode.petmephone.core.domain.pet.sprite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpriteLayoutTest {

    private val grid = SpriteGrid(cellSizePx = 64, columns = 6)

    @Test
    fun `cellLeftPx for the first and last frame`() {
        val layout = SpriteLayout(grid, frameCount = 6)

        assertEquals(0, layout.cellLeftPx(frame = 0))
        assertEquals(64 * 5, layout.cellLeftPx(frame = 5))
    }

    @Test
    fun `frameCount above the grid's column count is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpriteLayout(grid, frameCount = 7)
        }
    }

    @Test
    fun `frameCount of zero is a valid, ordinary empty animation`() {
        val layout = SpriteLayout(grid, frameCount = 0)

        assertEquals(0, layout.frameCount)
    }

    @Test
    fun `frameCount reads back exactly what was constructed`() {
        val layout = SpriteLayout(grid, frameCount = 4)

        assertEquals(4, layout.frameCount)
    }
}
