package com.gcatcode.petmephone.core.domain.pet.sprite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MAX_DIMENSION_PX = 2048

class SpriteGridTest {

    @Test
    fun `exact multiples produce a valid grid`() {
        val result = SpriteGrid.of(widthPx = 384, heightPx = 384, maxDimensionPx = MAX_DIMENSION_PX)

        assertTrue(result is SpriteGridResult.Valid)
        val grid = (result as SpriteGridResult.Valid).grid
        assertEquals(64, grid.cellSizePx)
        assertEquals(6, grid.columns)
    }

    @Test
    fun `height not divisible by six is rejected as not divisible`() {
        val result = SpriteGrid.of(widthPx = 384, heightPx = 385, maxDimensionPx = MAX_DIMENSION_PX)

        assertEquals(SpriteGridResult.Invalid(SpriteSheetFailure.NotDivisible), result)
    }

    @Test
    fun `width not divisible by cell size is rejected as not divisible`() {
        val result = SpriteGrid.of(widthPx = 385, heightPx = 384, maxDimensionPx = MAX_DIMENSION_PX)

        assertEquals(SpriteGridResult.Invalid(SpriteSheetFailure.NotDivisible), result)
    }

    @Test
    fun `exactly at the size bound is valid`() {
        // 2046 = 6 * 341, the largest height <= 2048 that divides evenly by six rows, so a
        // square sheet at this size exercises both the header size bound and clean divisibility.
        val result = SpriteGrid.of(widthPx = 2046, heightPx = 2046, maxDimensionPx = MAX_DIMENSION_PX)

        assertTrue(result is SpriteGridResult.Valid)
    }

    @Test
    fun `over the bound on either axis is rejected as oversized`() {
        val overWidth = SpriteGrid.of(widthPx = 2049, heightPx = 384, maxDimensionPx = MAX_DIMENSION_PX)
        val overHeight = SpriteGrid.of(widthPx = 384, heightPx = 2049, maxDimensionPx = MAX_DIMENSION_PX)

        assertEquals(SpriteGridResult.Invalid(SpriteSheetFailure.Oversized), overWidth)
        assertEquals(SpriteGridResult.Invalid(SpriteSheetFailure.Oversized), overHeight)
    }

    @Test
    fun `oversized and non-divisible together fails as oversized, proving check order`() {
        // height=2050 is both > 2048 and not a multiple of 6.
        val result = SpriteGrid.of(widthPx = 2050, heightPx = 2050, maxDimensionPx = MAX_DIMENSION_PX)

        assertEquals(SpriteGridResult.Invalid(SpriteSheetFailure.Oversized), result)
    }
}
