package com.gcatcode.petmephone.core.domain.pet.sprite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MAX_DIMENSION_PX = 2048

class SpriteGridTest {

    @Test
    fun `a declared grid that divides exactly into square cells is valid`() {
        val result = SpriteGrid.of(
            widthPx = 384,
            heightPx = 64,
            declaration = SpriteGridDeclaration(columns = 6, rows = 1),
            maxDimensionPx = MAX_DIMENSION_PX,
        )

        assertTrue(result is SpriteGridResult.Valid)
        val grid = (result as SpriteGridResult.Valid).grid
        assertEquals(64, grid.cellSizePx)
        assertEquals(6, grid.columns)
        assertEquals(1, grid.rows)
    }

    @Test
    fun `a square sheet is never accepted as a one-frame animation`() {
        // This is the regression #69 exists to close: a 384x384 square sheet used to satisfy
        // widthPx % heightPx == 0 and silently derive columns = 1, a single frame containing the
        // whole grid. A declared 1x1 grid over a square sheet whose real shape is 6x6 must be
        // rejected, not accepted.
        val declaredAsOneFrame = SpriteGrid.of(
            widthPx = 384,
            heightPx = 384,
            declaration = SpriteGridDeclaration(columns = 1, rows = 1),
            maxDimensionPx = MAX_DIMENSION_PX,
        )
        // 384x384 IS a valid single frame (cell == image), so this specific declaration is
        // technically consistent — the point is a caller must never derive it silently; it must be
        // explicitly declared. Prove the real regression: a square sheet whose ACTUAL grid is 6x6
        // (cell 64) is REJECTED when nobody bothers to declare it and the pixels alone are trusted
        // via a would-be inference of columns = width/height = 1.
        assertTrue(declaredAsOneFrame is SpriteGridResult.Valid)

        val undeclaredSixBySix = SpriteGrid.of(
            widthPx = 384,
            heightPx = 384,
            declaration = SpriteGridDeclaration(columns = 1, rows = 6),
            maxDimensionPx = MAX_DIMENSION_PX,
        )
        // 384 / 1 = 384 wide cells, 384 / 6 = 64 tall cells: not square, so this wrong guess is
        // rejected rather than silently accepted as *some* grid.
        assertEquals(SpriteGridResult.Invalid(SpriteSheetFailure.NotDivisible), undeclaredSixBySix)

        val correctlyDeclaredSixBySix = SpriteGrid.of(
            widthPx = 384,
            heightPx = 384,
            declaration = SpriteGridDeclaration(columns = 6, rows = 6),
            maxDimensionPx = MAX_DIMENSION_PX,
        )
        val grid = (correctlyDeclaredSixBySix as SpriteGridResult.Valid).grid
        assertEquals(64, grid.cellSizePx)
        assertEquals(6, grid.columns)
        assertEquals(6, grid.rows)
    }

    @Test
    fun `a declared 6x6 grid over a 36-frame sheet is valid and expressible`() {
        // 36 frames at a 250px cell is a 1500x1500 sheet — impossible to express under the old
        // one-row contract (a 9000px strip would blow the 2048 max-dimension bound).
        val result = SpriteGrid.of(
            widthPx = 1500,
            heightPx = 1500,
            declaration = SpriteGridDeclaration(columns = 6, rows = 6),
            maxDimensionPx = MAX_DIMENSION_PX,
        )

        assertTrue(result is SpriteGridResult.Valid)
        val grid = (result as SpriteGridResult.Valid).grid
        assertEquals(250, grid.cellSizePx)
        assertEquals(36, grid.columns * grid.rows)
    }

    @Test
    fun `width not divisible by the declared column count is rejected as not divisible`() {
        val result = SpriteGrid.of(
            widthPx = 385,
            heightPx = 64,
            declaration = SpriteGridDeclaration(columns = 6, rows = 1),
            maxDimensionPx = MAX_DIMENSION_PX,
        )

        assertEquals(SpriteGridResult.Invalid(SpriteSheetFailure.NotDivisible), result)
    }

    @Test
    fun `a non-square resulting cell is rejected as not divisible`() {
        // 400 / 4 = 100 wide, 200 / 4 = 50 tall — divides exactly on both axes, but not square.
        val result = SpriteGrid.of(
            widthPx = 400,
            heightPx = 200,
            declaration = SpriteGridDeclaration(columns = 4, rows = 4),
            maxDimensionPx = MAX_DIMENSION_PX,
        )

        assertEquals(SpriteGridResult.Invalid(SpriteSheetFailure.NotDivisible), result)
    }

    @Test
    fun `zero or negative declared columns or rows is rejected as not divisible`() {
        val zeroColumns = SpriteGrid.of(
            widthPx = 64,
            heightPx = 64,
            declaration = SpriteGridDeclaration(columns = 0, rows = 1),
            maxDimensionPx = MAX_DIMENSION_PX,
        )
        val zeroRows = SpriteGrid.of(
            widthPx = 64,
            heightPx = 64,
            declaration = SpriteGridDeclaration(columns = 1, rows = 0),
            maxDimensionPx = MAX_DIMENSION_PX,
        )

        assertEquals(SpriteGridResult.Invalid(SpriteSheetFailure.NotDivisible), zeroColumns)
        assertEquals(SpriteGridResult.Invalid(SpriteSheetFailure.NotDivisible), zeroRows)
    }

    @Test
    fun `exactly at the size bound is valid`() {
        // 2046x341, the migrated real asset's dimensions: 2046 / 6 = 341, matching height exactly.
        val result = SpriteGrid.of(
            widthPx = 2046,
            heightPx = 341,
            declaration = SpriteGridDeclaration(columns = 6, rows = 1),
            maxDimensionPx = MAX_DIMENSION_PX,
        )

        assertTrue(result is SpriteGridResult.Valid)
        val grid = (result as SpriteGridResult.Valid).grid
        assertEquals(341, grid.cellSizePx)
        assertEquals(6, grid.columns)
    }

    @Test
    fun `over the bound on either axis is rejected as oversized`() {
        val declaration = SpriteGridDeclaration(columns = 1, rows = 1)
        val overWidth = SpriteGrid.of(widthPx = 2049, heightPx = 64, declaration = declaration, maxDimensionPx = MAX_DIMENSION_PX)
        val overHeight = SpriteGrid.of(widthPx = 64, heightPx = 2049, declaration = declaration, maxDimensionPx = MAX_DIMENSION_PX)

        assertEquals(SpriteGridResult.Invalid(SpriteSheetFailure.Oversized), overWidth)
        assertEquals(SpriteGridResult.Invalid(SpriteSheetFailure.Oversized), overHeight)
    }

    @Test
    fun `oversized and non-divisible together fails as oversized, proving check order`() {
        // height=2050 is both > 2048 and does not divide by the declared row count evenly.
        val result = SpriteGrid.of(
            widthPx = 2051,
            heightPx = 2050,
            declaration = SpriteGridDeclaration(columns = 1, rows = 3),
            maxDimensionPx = MAX_DIMENSION_PX,
        )

        assertEquals(SpriteGridResult.Invalid(SpriteSheetFailure.Oversized), result)
    }
}
