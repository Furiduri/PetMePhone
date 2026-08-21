package com.gcatcode.petmephone.debug.tuning

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Design decision 1b: the CI file and the source are two owners of one value; this test makes
 * them one. Pins the activity's package prefix (token T1), the marker literal (token T2), and
 * that both literals appear in `.github/workflows/ci.yml`.
 */
class TuningPanelMarkerTest {

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }

    @Test
    fun `TuningPanelActivity's FQCN starts with the debug tuning package`() {
        assertTrue(
            TuningPanelActivity::class.java.name.startsWith("com.gcatcode.petmephone.debug.tuning."),
        )
    }

    @Test
    fun `the marker const equals the exact literal`() {
        assertEquals("PETMEPHONE_DEBUG_TUNING_PANEL", TUNING_PANEL_MARKER)
    }

    @Test
    fun `both tokens appear in the ci workflow file`() {
        val ciFile = File(repoRoot(), ".github/workflows/ci.yml")
        check(ciFile.isFile) { "expected ${ciFile.absolutePath} to exist" }
        val text = ciFile.readText()

        assertTrue("ci.yml is missing the package token", text.contains("com/gcatcode/petmephone/debug/tuning"))
        assertTrue("ci.yml is missing the marker token", text.contains(TUNING_PANEL_MARKER))
    }
}
