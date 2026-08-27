package com.gcatcode.petmephone.debug.tuning

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `debug-tuning-panel` spec: "No runtime check gating the panel's visibility or behaviour on a
 * debug/release distinction SHALL exist anywhere in this change" — the source-set is the only
 * gate, never a `BuildConfig.DEBUG` read.
 */
class TuningPanelNoDebugFlagCodeTest {

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }

    @Test
    fun `BuildConfig DEBUG appears nowhere under app-src-debug`() {
        val debugDir = File(repoRoot(), "app/src/debug")
        check(debugDir.isDirectory) { "expected ${debugDir.absolutePath} to exist" }

        val offendingHits = debugDir.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .filter { it.readText().contains("BuildConfig.DEBUG") }
            .map { it.path }
            .toList()

        assertTrue("found BuildConfig.DEBUG under app/src/debug:\n${offendingHits.joinToString("\n")}", offendingHits.isEmpty())
    }
}
