package com.gcatcode.petmephone.core.data.local.task

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * `balance-configuration`/`task-persistence` spec: no `@Query`/SQL string in `:core:data` ever
 * embeds a `BalanceConfig` default literal — those values are always passed in as a bound
 * parameter, never written into the query text itself (task 2.23).
 */
class NoBalanceLiteralInSqlTest {

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }

    private fun coreDataMainSourceFiles(): List<File> {
        val root = File(repoRoot(), "core/data/src/main")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun `no Query string in core-data embeds a balance literal`() {
        val suspiciousPatterns = listOf(
            Regex("""@Query\("[^"]*\b10\b[^"]*"\)"""),
            Regex("""@Query\("[^"]*\b0\.6\b[^"]*"\)"""),
            Regex("""@Query\("[^"]*\b3\b[^"]*"\)"""),
            Regex("""@Query\("[^"]*\b4\b[^"]*"\)"""),
        )

        for (file in coreDataMainSourceFiles()) {
            val text = file.readText()
            val queries = Regex("""@Query\("([^"]*)"\)""").findAll(text).map { it.groupValues[1] }
            for (query in queries) {
                for (pattern in suspiciousPatterns) {
                    assertFalse(
                        "${file.path} has a @Query that may embed a balance literal: $query",
                        pattern.containsMatchIn("""@Query("$query")"""),
                    )
                }
            }
        }
    }
}
