package com.gcatcode.petmephone.feature.overlay.sprite

import org.junit.Ignore
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Manually-triggered generator for `feature/overlay/src/main/assets/pet/idle_default.png`, the
 * one built-in IDLE sheet (`design.md`, "Programmer art"). `@Ignore`d so CI never runs it and no
 * generator code ships in `main` — only `java.awt`/`ImageIO`, JVM-only, test source set only.
 *
 * Row 0 (IDLE) gets exactly 4 drawn frames and 2 fully-transparent trailing cells, so the shipped
 * asset exercises [TransparentCellScanner]'s trailing-transparent clamp on every real launch, not
 * only in synthetic test fixtures.
 */
@Ignore("Manually-triggered asset generator, never run by CI — see class kdoc")
class ProgrammerArtGenerator {

    @Test
    fun `generate idle_default png`() {
        val cellSizePx = 32
        val columns = 6
        val rows = 6
        val idleOpaqueFrames = 4

        val image = BufferedImage(cellSizePx * columns, cellSizePx * rows, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        // A simple two-tone "blob" per IDLE frame, distinct enough per frame to visibly animate.
        val bodyColors = listOf(
            Color(90, 170, 90, 255),
            Color(100, 180, 100, 255),
            Color(90, 170, 90, 255),
            Color(80, 160, 80, 255),
        )
        for (frame in 0 until idleOpaqueFrames) {
            val left = frame * cellSizePx
            graphics.color = bodyColors[frame % bodyColors.size]
            graphics.fillOval(left + 4, 4, cellSizePx - 8, cellSizePx - 8)
            graphics.color = Color.BLACK
            graphics.fillOval(left + cellSizePx / 2 - 3, cellSizePx / 2 - 4, 3, 3)
        }
        // Frames [idleOpaqueFrames, columns) stay fully transparent — the trailing clamp.
        graphics.dispose()

        // Gradle's unit-test working directory is this module's root (`feature/overlay`).
        val outputFile = File("src/main/assets/pet/idle_default.png")
        outputFile.parentFile?.mkdirs()
        ImageIO.write(image, "png", outputFile)
    }
}
