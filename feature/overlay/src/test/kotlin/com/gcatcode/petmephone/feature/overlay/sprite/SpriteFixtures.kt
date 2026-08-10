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

    /** A single-row sheet: [columns] cells of [cellSizePx], with [opaqueFrames] opaque frames from
     * the left and the rest fully transparent — one animation, one row, per the current contract. */
    fun validSheetBytes(cellSizePx: Int = 8, columns: Int = 6, opaqueFrames: Int = 6): ByteArray {
        val width = cellSizePx * columns
        val height = cellSizePx
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (frame in 0 until opaqueFrames) {
            fillCell(image, cellSizePx, left = frame * cellSizePx, color = Color(255, 0, 0, 255))
        }
        return encodePng(image)
    }

    /** Header dimensions that violate `width % height != 0` (not divisible). */
    fun nonDivisibleHeaderBytes(): ByteArray {
        val image = BufferedImage(49, 8, BufferedImage.TYPE_INT_ARGB)
        return encodePng(image)
    }

    /** Bytes that are not a valid PNG at all — too short/garbage for any decoder to find bounds. */
    fun corruptBytes(): ByteArray = ByteArray(0)

    /** An all-transparent sheet (empty animation, zero usable frames). */
    fun emptySheetBytes(cellSizePx: Int = 8, columns: Int = 6): ByteArray =
        validSheetBytes(cellSizePx = cellSizePx, columns = columns, opaqueFrames = 0)

    private fun fillCell(image: BufferedImage, cellSizePx: Int, left: Int, color: Color) {
        val graphics = image.createGraphics()
        graphics.color = color
        graphics.fillRect(left, 0, cellSizePx, cellSizePx)
        graphics.dispose()
    }

    private fun encodePng(image: BufferedImage): ByteArray {
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }
}
