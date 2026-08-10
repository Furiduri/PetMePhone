package com.gcatcode.petmephone.core.domain.pet.sprite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpriteLayoutTest {

    private val grid = SpriteGrid(cellSizePx = 64, columns = 6)

    @Test
    fun `cellLeftPx and cellTopPx for row 0 first and last frame`() {
        val layout = SpriteLayout(grid, frameCounts = List(6) { 6 })

        assertEquals(0, layout.cellLeftPx(PetSpriteRow.IDLE, frame = 0))
        assertEquals(64 * 5, layout.cellLeftPx(PetSpriteRow.IDLE, frame = 5))
        assertEquals(0, layout.cellTopPx(PetSpriteRow.IDLE))
    }

    @Test
    fun `cellLeftPx and cellTopPx for row 5 first and last frame`() {
        val layout = SpriteLayout(grid, frameCounts = List(6) { 6 })

        assertEquals(0, layout.cellLeftPx(PetSpriteRow.TYPING, frame = 0))
        assertEquals(64 * 5, layout.cellLeftPx(PetSpriteRow.TYPING, frame = 5))
        assertEquals(64 * 5, layout.cellTopPx(PetSpriteRow.TYPING))
    }

    @Test
    fun `frameCounts must be exactly six entries`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpriteLayout(grid, frameCounts = List(5) { 6 })
        }
    }

    @Test
    fun `idleFrameCount reads row 0's entry`() {
        val layout = SpriteLayout(grid, frameCounts = listOf(4, 6, 6, 6, 6, 6))

        assertEquals(4, layout.idleFrameCount)
    }
}
