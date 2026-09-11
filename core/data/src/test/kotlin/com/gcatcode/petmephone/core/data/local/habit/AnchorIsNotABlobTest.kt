package com.gcatcode.petmephone.core.data.local.habit

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #128's dominant risk: the anchor flattened into a serialized string.
 *
 * A blob passes every round-trip test ever written — that is precisely why it is dangerous. It
 * fails only at the two things a test of behaviour does not ask about: the database cannot query
 * the anchor, and the reference to another habit stops being a foreign key. So this reads the
 * entity declaration instead.
 */
class AnchorIsNotABlobTest {

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }

    private fun anchorEntitySource(): String =
        File(
            repoRoot(),
            "core/data/src/main/kotlin/com/gcatcode/petmephone/core/data/local/habit/HabitAnchorEntity.kt",
        ).readText()

    @Test
    fun `the anchor entity declares a foreign key on its habit reference`() {
        val source = anchorEntitySource()

        assertTrue(
            "anchorHabitId must be a real foreign key; without one, deleting the referenced habit " +
                "silently orphans every habit stacked onto it",
            Regex("""childColumns\s*=\s*\["anchorHabitId"]""").containsMatchIn(source),
        )
    }

    @Test
    fun `each anchor variant keeps its own typed column`() {
        val source = anchorEntitySource()

        listOf("anchorHabitId", "daySegment", "clockTime").forEach { column ->
            assertTrue(
                "$column must remain a column of its own; folding the variants into one string " +
                    "makes the anchor opaque to the database",
                Regex("""val\s+$column\s*:""").containsMatchIn(source),
            )
        }
    }

    @Test
    fun `no column holds a serialized anchor payload`() {
        val source = anchorEntitySource()

        val suspicious = listOf("payload", "json", "serialized", "blob", "encoded")
        val found = suspicious.filter {
            Regex("""val\s+\w*$it\w*\s*:""", RegexOption.IGNORE_CASE).containsMatchIn(source)
        }

        assertTrue("the anchor must not be stored as a serialized payload: $found", found.isEmpty())
    }
}
