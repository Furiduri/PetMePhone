package com.gcatcode.petmephone.core.data.local.task

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #98: "No scoring path treats a minimum completion as worth less."
 *
 * A test over behaviour cannot prove this one, because the defect it guards has not been written
 * yet — the day someone adds `AND completionKind = 'FULL'` to a counting query, every existing
 * assertion still passes and the minimum quietly becomes worth nothing. So this reads the SQL
 * itself, in the shape of the existing [NoBalanceLiteralInSqlTest].
 *
 * Exactly one query may name the column: the write that records it.
 */
class CompletionKindNotScoredTest {

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

    private fun allQueries(): List<String> = coreDataMainSourceFiles().flatMap { file ->
        Regex("""@Query\("([^"]*)"\)""").findAll(file.readText()).map { it.groupValues[1] }.toList()
    }

    @Test
    fun `no counting or aggregating query reads the completion kind`() {
        val scoringShapes = Regex("""(?i)\b(COUNT|SUM|AVG|MIN|MAX|TOTAL)\s*\(""")

        val offenders = allQueries().filter { query ->
            query.contains("completionKind", ignoreCase = true) && scoringShapes.containsMatchIn(query)
        }

        assertTrue(
            "a metric query reads completionKind, which makes a minimum completion worth less " +
                "than a full one: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `no query filters rows by the completion kind`() {
        // Filtering is the subtler form of the same defect: excluding minimum completions from a
        // counted set scores them at zero without any arithmetic naming them.
        //
        // Only the WHERE clause counts. The completion write names the column in its SET clause and
        // filters by id, which is the one legitimate use — an earlier version of this test flagged
        // that write and was wrong to.
        val offenders = allQueries().filter { query ->
            val whereMatch = Regex("""(?i)\bWHERE\b""").find(query)
            val whereClause = whereMatch?.let { query.substring(it.range.last + 1) }
            whereClause != null && whereClause.contains("completionKind", ignoreCase = true)
        }

        assertTrue(
            "a query filters on completionKind, which excludes minimum completions from whatever " +
                "it feeds: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `only the completion write names the column at all`() {
        val naming = allQueries().filter { it.contains("completionKind", ignoreCase = true) }

        assertTrue(
            "exactly one query — the completion write — may name completionKind, found: $naming",
            naming.size == 1,
        )
        assertTrue(
            "the only query naming completionKind must be the write that records it",
            naming.single().trim().startsWith("UPDATE", ignoreCase = true),
        )
    }
}
