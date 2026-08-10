package com.gcatcode.petmephone.core.domain.pet.sprite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MAX_DIMENSION_PX = 2048

class SpriteGridTest {

    @Test
    fun `an exact multiple of height produces a valid grid`() {
        val result = SpriteGrid.of(widthPx = 384, heightPx = 64, maxDimensionPx = MAX_DIMENSION_PX)

        assertTrue(result is SpriteGridResult.Valid)
        val grid = (result as SpriteGridResult.Valid).grid
        assertEquals(64, grid.cellSizePx)
        assertEquals(6, grid.columns)
    }

    @Test
    fun `width not divisible by height is rejected as not divisible`() {
        val result = SpriteGrid.of(widthPx = 385, heightPx = 64, maxDimensionPx = MAX_DIMENSION_PX)

        assertEquals(SpriteGridResult.Invalid(SpriteSheetFailure.NotDivisible), result)
    }

    @Test
    fun `zero height is rejected as not divisible`() {
        val result = SpriteGrid.of(widthPx = 64, heightPx = 0, maxDimensionPx = MAX_DIMENSION_PX)

        assertEquals(SpriteGridResult.Invalid(SpriteSheetFailure.NotDivisible), result)
    }

    @Test
    fun `exactly at the size bound is valid`() {
        // 2046x341, the migrated real asset's dimensions: 2046 / 341 = 6 whole frames.
        val result = SpriteGrid.of(widthPx = 2046, heightPx = 341, maxDimensionPx = MAX_DIMENSION_PX)

        assertTrue(result is SpriteGridResult.Valid)
        val grid = (result as SpriteGridResult.Valid).grid
        assertEquals(341, grid.cellSizePx)
        assertEquals(6, grid.columns)
    }

    @Test
    fun `over the bound on either axis is rejected as oversized`() {
        val overWidth = SpriteGrid.of(widthPx = 2049, heightPx = 64, maxDimensionPx = MAX_DIMENSION_PX)
        val overHeight = SpriteGrid.of(widthPx = 64, heightPx = 2049, maxDimensionPx = MAX_DIMENSION_PX)

        assertEquals(SpriteGridResult.Invalid(SpriteSheetFailure.Oversized), overWidth)
        assertEquals(SpriteGridResult.Invalid(SpriteSheetFailure.Oversized), overHeight)
    }

    @Test
    fun `oversized and non-divisible together fails as oversized, proving check order`() {
        // height=2050 is both > 2048 and does not divide width=2051 evenly.
        val result = SpriteGrid.of(widthPx = 2051, heightPx = 2050, maxDimensionPx = MAX_DIMENSION_PX)

        assertEquals(SpriteGridResult.Invalid(SpriteSheetFailure.Oversized), result)
    }
}
