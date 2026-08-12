package com.gcatcode.petmephone.core.domain.pet.sprite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpriteLayoutTest {

    private val grid = SpriteGrid(cellSizePx = 64, columns = 6, rows = 1)

    @Test
    fun `cellLeftPx for the first and last frame`() {
        val layout = SpriteLayout(grid, frameCount = 6)

        assertEquals(0, layout.cellLeftPx(frame = 0))
        assertEquals(64 * 5, layout.cellLeftPx(frame = 5))
    }

    @Test
    fun `frameCount above the grid's total cell count is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpriteLayout(grid, frameCount = 7)
        }
    }

    @Test
    fun `cellLeftPx and cellTopPx wrap into the next row, left to right then top to bottom`() {
        val multiRowGrid = SpriteGrid(cellSizePx = 64, columns = 6, rows = 6)
        val layout = SpriteLayout(multiRowGrid, frameCount = 36)

        // Frame 6 is the first cell of row 1 (0-indexed), not a continuation of row 0.
        assertEquals(0, layout.cellLeftPx(frame = 6))
        assertEquals(64, layout.cellTopPx(frame = 6))
        // Frame 35 is the last cell of the last row.
        assertEquals(64 * 5, layout.cellLeftPx(frame = 35))
        assertEquals(64 * 5, layout.cellTopPx(frame = 35))
    }

    @Test
    fun `a declared 6x6 grid can hold all 36 frames`() {
        val multiRowGrid = SpriteGrid(cellSizePx = 64, columns = 6, rows = 6)

        val layout = SpriteLayout(multiRowGrid, frameCount = 36)

        assertEquals(36, layout.frameCount)
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
