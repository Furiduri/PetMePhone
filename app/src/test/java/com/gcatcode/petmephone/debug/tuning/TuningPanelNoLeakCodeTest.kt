package com.gcatcode.petmephone.debug.tuning

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The in-repo mirror of the CI artifact check (design.md's threat matrix). No file under
 * `app/src/main`, `core/`, or `feature/` mentions the debug panel's package or its marker — the
 * only mechanical guard against a leak, since a test file itself can reference debug-only code
 * (design's Correction section).
 */
class TuningPanelNoLeakCodeTest {

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }

    private fun kotlinFiles(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun `no file under app-src-main, core, or feature contains the panel's package or marker`() {
        val root = repoRoot()
        val forbiddenDirs = listOf(
            File(root, "app/src/main"),
            File(root, "core"),
            File(root, "feature"),
        )
        val forbidden = listOf("debug.tuning", TUNING_PANEL_MARKER)

        val offendingHits = mutableListOf<String>()
        forbiddenDirs.forEach { dir ->
            kotlinFiles(dir).forEach { file ->
                val text = file.readText()
                forbidden.forEach { term ->
                    if (text.contains(term)) offendingHits += "${file.path}: $term"
                }
            }
        }

        assertTrue("found tuning-panel leak(s):\n${offendingHits.joinToString("\n")}", offendingHits.isEmpty())
    }
}
