package com.gcatcode.petmephone.core.data.config

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural enforcement of "no whole-config write exists; writing one field never touches
 * another" (`config-override-store` spec), the same `File.walkTopDown()` shape as
 * `QuickMenuNoPersistenceCodeTest` in `:feature:overlay`. Vigilance alone cannot hold this — a
 * source scan does.
 */
class ConfigStoreNoBulkWriteCodeTest {

    private fun resolveSourceDir(moduleDir: String, packagePath: String): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val candidate = File(dir, "$moduleDir/src/main/kotlin/$packagePath")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return candidate
        }
        return File(dir, "$moduleDir/src/main/kotlin/$packagePath")
    }

    private fun kotlinFiles(dir: File): List<File> {
        check(dir.isDirectory) { "expected ${dir.absolutePath} to exist — did the package move?" }
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun `dataStore edit appears exactly once across core-data's config package`() {
        val configDataDir = resolveSourceDir("core/data", "com/gcatcode/petmephone/core/data/config")
        val occurrences = kotlinFiles(configDataDir).sumOf { file ->
            Regex("""dataStore\.edit""").findAll(file.readText()).count()
        }

        assertEquals("expected exactly one dataStore.edit call site", 1, occurrences)
    }

    @Test
    fun `no bulk-write primitive appears in either config package`() {
        val forbidden = listOf("putAll", "preferences.clear()", "Preferences.Pair")
        val dirs = listOf(
            resolveSourceDir("core/data", "com/gcatcode/petmephone/core/data/config"),
            resolveSourceDir("core/domain", "com/gcatcode/petmephone/core/domain/config"),
        )

        val offendingHits = mutableListOf<String>()
        dirs.forEach { dir ->
            kotlinFiles(dir).forEach { file ->
                val text = file.readText()
                forbidden.forEach { term ->
                    if (text.contains(term)) offendingHits += "${file.path}: $term"
                }
            }
        }

        assertTrue("found forbidden bulk-write reference(s):\n${offendingHits.joinToString("\n")}", offendingHits.isEmpty())
    }

    @Test
    fun `no config constructor call appears inside a write path`() {
        val writeFile = File(
            resolveSourceDir("core/data", "com/gcatcode/petmephone/core/data/config"),
            "PreferencesConfigOverrideStore.kt",
        )
        check(writeFile.isFile) { "expected ${writeFile.absolutePath} to exist" }
        val text = writeFile.readText()

        assertTrue("BalanceConfig( must never appear in a write path", !text.contains("BalanceConfig("))
        assertTrue("PetAnimationConfig( must never appear in a write path", !text.contains("PetAnimationConfig("))
    }

    @Test
    fun `the domain store interface's set signature accepts no collection, vararg or whole config`() {
        val interfaceFile = File(
            resolveSourceDir("core/domain", "com/gcatcode/petmephone/core/domain/config"),
            "ConfigOverrideStore.kt",
        )
        check(interfaceFile.isFile) { "expected ${interfaceFile.absolutePath} to exist" }
        val text = interfaceFile.readText()
        val setSignatures = Regex("""suspend fun <[^(]*>\s*(set|save)\([^)]*\)""").findAll(text).map { it.value }.toList()

        assertTrue("expected at least one set/save signature to scan", setSignatures.isNotEmpty())
        setSignatures.forEach { signature ->
            assertTrue("$signature must not accept a List<", !signature.contains("List<"))
            assertTrue("$signature must not accept a Map<", !signature.contains("Map<"))
            assertTrue("$signature must not accept a vararg", !signature.contains("vararg"))
            assertTrue("$signature must not accept a whole config", !signature.contains("Config)"))
        }
    }
}
