package com.gcatcode.petmephone.core.data.config

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural enforcement of "absence never resolves to zero, and never to a partially-zeroed
 * config" (`config-override-store` spec). Absence must reach [com.gcatcode.petmephone.core.domain.config.resolve],
 * never a literal zero-substitution.
 */
class ConfigStoreNoZeroSubstitutionCodeTest {

    private val forbidden = listOf("?: 0", "?: 0L", "?: 0.0", ".orZero", "getOrDefault")

    private fun resolveSourceDir(moduleDir: String, packagePath: String): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val candidate = File(dir, "$moduleDir/src/main/kotlin/$packagePath")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return candidate
        }
        return File(dir, "$moduleDir/src/main/kotlin/$packagePath")
    }

    @Test
    fun `no zero-substitution pattern appears in either config package`() {
        val dirs = listOf(
            resolveSourceDir("core/data", "com/gcatcode/petmephone/core/data/config"),
            resolveSourceDir("core/domain", "com/gcatcode/petmephone/core/domain/config"),
        )

        val offendingHits = mutableListOf<String>()
        dirs.forEach { dir ->
            check(dir.isDirectory) { "expected ${dir.absolutePath} to exist — did the package move?" }
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                val text = file.readText()
                forbidden.forEach { term ->
                    if (text.contains(term)) offendingHits += "${file.path}: $term"
                }
            }
        }

        assertTrue(
            "found forbidden zero-substitution reference(s):\n${offendingHits.joinToString("\n")}",
            offendingHits.isEmpty(),
        )
    }
}
