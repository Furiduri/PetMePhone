package com.gcatcode.petmephone.core.data.local.task

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `task-persistence` spec, "No DAO method updates createdDate" — a source-text scan, not a
 * reflection check, because `@Update`/`@Upsert` and a `createdDate`-assigning `@Query` string are
 * absences to prove, and the strongest proof is that the text simply never appears (design
 * decision 9, tasks 2.17/2.18).
 */
class TaskDaoNoUpdateOrUpsertTest {

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }

    /** Strips the file's own KDoc, which documents the `@Update`/`@Upsert` absence in prose and
     * would otherwise false-positive against this scan's own annotation check. */
    private fun taskDaoSourceWithoutComments(): String {
        val file = File(
            repoRoot(),
            "core/data/src/main/kotlin/com/gcatcode/petmephone/core/data/local/task/TaskDao.kt",
        )
        val raw = file.readText()
        return raw.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
    }

    private fun taskDaoSource(): String = taskDaoSourceWithoutComments()

    @Test
    fun `TaskDao declares no Update or Upsert annotation`() {
        val source = taskDaoSource()
        assertFalse("TaskDao must not declare @Update", source.contains("@Update"))
        assertFalse("TaskDao must not declare @Upsert", source.contains("@Upsert"))
    }

    @Test
    fun `no Query in TaskDao assigns createdDate`() {
        val source = taskDaoSource()
        val queryStrings = Regex("""@Query\("([^"]*)"\)""").findAll(source).map { it.groupValues[1] }
        for (query in queryStrings) {
            assertFalse(
                "A @Query in TaskDao assigns createdDate: $query",
                Regex("""(?i)set\s+.*createdDate\s*=""").containsMatchIn(query),
            )
        }
        assertTrue("Expected TaskDao to declare at least one @Query", queryStrings.toList().isNotEmpty())
    }
}
