package com.gcatcode.petmephone.feature.overlay.character

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Greps every `strings.xml` under this module's `main` resources for a generic "invalid image"
 * catch-all phrase (character-import spec: "A generic 'invalid image' message MUST NOT exist"),
 * and confirms one distinct string per [com.gcatcode.petmephone.core.domain.character.CharacterImportRejection]
 * case exists instead.
 */
class NoGenericRejectionStringTest {

    private val genericPhrases = listOf("invalid image", "invalid file", "something went wrong")

    private fun stringsXmlFiles(): List<File> {
        val resDir = resolveMainResDir()
        return resDir.walkTopDown().filter { it.isFile && it.name == "strings.xml" }.toList()
    }

    /**
     * The Gradle test task's working directory is the module directory (`feature/overlay`), but
     * fall back to searching upward in case the runner differs.
     */
    private fun resolveMainResDir(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val candidate = File(dir, "src/main/res")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return candidate
        }
        return File(dir, "src/main/res")
    }

    @Test
    fun `no strings xml contains a generic invalid-image catch-all`() {
        val files = stringsXmlFiles()
        assertTrue("Expected at least one strings.xml under src/main/res", files.isNotEmpty())
        for (file in files) {
            val text = file.readText().lowercase()
            for (phrase in genericPhrases) {
                assertFalse(
                    "${file.path} contains the generic phrase \"$phrase\"",
                    text.contains(phrase),
                )
            }
        }
    }

    @Test
    fun `every rejection case has its own distinct string resource`() {
        val text = stringsXmlFiles().joinToString("\n") { it.readText() }
        val expectedNames = listOf(
            "import_rejection_not_png",
            "import_rejection_too_large",
            "import_rejection_oversized",
            "import_rejection_not_divisible",
            "import_rejection_undecodable",
            "import_rejection_empty_sheet",
            "import_rejection_cap_reached",
        )
        for (name in expectedNames) {
            assertTrue("Missing string resource $name", text.contains("name=\"$name\""))
        }
        assertEquals(expectedNames.size, expectedNames.distinct().size)
    }
}
