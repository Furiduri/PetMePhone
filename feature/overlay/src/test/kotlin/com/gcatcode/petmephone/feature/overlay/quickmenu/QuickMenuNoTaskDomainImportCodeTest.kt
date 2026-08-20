package com.gcatcode.petmephone.feature.overlay.quickmenu

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * `quick-menu-text-input`'s "submission is out of scope" requirement, and the orchestrator's hard
 * constraint: `CreateOneOffTask` or any `:core:domain/task` type must not appear anywhere in the
 * quick-menu package. Submit's callback is `(String) -> Unit`, wired to a no-op logging lambda in
 * `PetOverlayService`; #100 owns actual submission wiring.
 *
 * Same file-scanning approach as this package's other structural gates.
 */
class QuickMenuNoTaskDomainImportCodeTest {

    private fun resolveQuickMenuSourceDir(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val candidate = File(dir, "src/main/kotlin/com/gcatcode/petmephone/feature/overlay/quickmenu")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return candidate
        }
        return File(dir, "src/main/kotlin/com/gcatcode/petmephone/feature/overlay/quickmenu")
    }

    private fun quickMenuSourceFiles(): List<File> {
        val quickMenuSourceDir = resolveQuickMenuSourceDir()
        check(quickMenuSourceDir.isDirectory) {
            "expected ${quickMenuSourceDir.absolutePath} to exist — did the package move?"
        }
        return quickMenuSourceDir.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .toList()
    }

    private fun countOccurrences(needle: String): Int =
        quickMenuSourceFiles().sumOf { file -> file.readText().split(needle).size - 1 }

    @Test
    fun `no core-domain task import or CreateOneOffTask reference exists in the quick-menu package`() {
        assertEquals(0, countOccurrences("core.domain.task"))
        assertEquals(0, countOccurrences("CreateOneOffTask"))
    }
}
