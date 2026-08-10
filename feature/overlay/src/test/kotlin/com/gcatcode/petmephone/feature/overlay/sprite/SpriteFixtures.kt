package com.gcatcode.petmephone.feature.overlay.sprite

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Test-only PNG byte fixtures, generated with `java.awt`/`ImageIO` (JVM-only, never shipped in
 * `main`). Building fixtures in code rather than committing binary PNGs keeps them inspectable in
 * the diff and trivially reviewable — the one deviation from Task 24's literal "store PNG files"
 * wording, recorded in the apply-progress notes.
 */
object SpriteFixtures {

    /** 6 columns × 6 rows, cellSizePx = [cellSizePx]; row 0 has [idleOpaqueFrames] opaque frames,
     * the rest of row 0 and every other row fully transparent. */
    fun validSheetBytes(cellSizePx: Int = 8, columns: Int = 6, idleOpaqueFrames: Int = 6): ByteArray {
        val width = cellSizePx * columns
        val height = cellSizePx * 6
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (frame in 0 until idleOpaqueFrames) {
            fillCell(image, cellSizePx, left = frame * cellSizePx, top = 0, color = Color(255, 0, 0, 255))
        }
        return encodePng(image)
    }

    /** Header dimensions that violate `height % 6 != 0` (not divisible). */
    fun nonDivisibleHeaderBytes(): ByteArray {
        val image = BufferedImage(48, 49, BufferedImage.TYPE_INT_ARGB)
        return encodePng(image)
    }

    /** Bytes that are not a valid PNG at all — too short/garbage for any decoder to find bounds. */
    fun corruptBytes(): ByteArray = ByteArray(0)

    /** All-transparent row 0 (empty IDLE row), other rows irrelevant. */
    fun emptyIdleRowBytes(cellSizePx: Int = 8, columns: Int = 6): ByteArray =
        validSheetBytes(cellSizePx = cellSizePx, columns = columns, idleOpaqueFrames = 0)

    private fun fillCell(image: BufferedImage, cellSizePx: Int, left: Int, top: Int, color: Color) {
        val graphics = image.createGraphics()
        graphics.color = color
        graphics.fillRect(left, top, cellSizePx, cellSizePx)
        graphics.dispose()
    }

    private fun encodePng(image: BufferedImage): ByteArray {
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }
}
