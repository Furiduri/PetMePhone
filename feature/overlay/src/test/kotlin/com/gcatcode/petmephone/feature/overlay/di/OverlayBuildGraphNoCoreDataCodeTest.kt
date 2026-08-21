package com.gcatcode.petmephone.feature.overlay.di

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural enforcement of design decision 5 — `:feature:overlay`'s *main-source* dependencies
 * carry no `:core:data` edge. [PetAnimationConfigSource] reaches persistence only through the
 * `:core:domain` `ConfigOverrideStore` interface; Hilt resolves the implementation in the app
 * graph. Only a `testImplementation(project(":core:data"))` entry is allowed, and this test asserts
 * it exists — the seam is proven, not merely untested.
 */
class OverlayBuildGraphNoCoreDataCodeTest {

    private fun resolveBuildFile(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val candidate = File(dir, "feature/overlay/build.gradle.kts")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return candidate
        }
        return File(dir, "feature/overlay/build.gradle.kts")
    }

    @Test
    fun `no main-source core-data dependency exists, only a testImplementation one`() {
        val buildFile = resolveBuildFile()
        check(buildFile.isFile) { "expected ${buildFile.absolutePath} to exist" }

        val mainSourceLines = buildFile.readLines().filter { line ->
            line.contains("project(\":core:data\")") &&
                (line.trimStart().startsWith("implementation(") || line.trimStart().startsWith("api("))
        }
        val testLines = buildFile.readLines().filter { line ->
            line.contains("project(\":core:data\")") && line.trimStart().startsWith("testImplementation(")
        }

        assertTrue(
            "no main-source implementation(project(\":core:data\")) line must exist",
            mainSourceLines.isEmpty(),
        )
        assertFalse(
            "expected a testImplementation(project(\":core:data\")) line in feature/overlay/build.gradle.kts",
            testLines.isEmpty(),
        )
    }
}
