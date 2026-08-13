package com.gcatcode.petmephone.feature.overlay.quickmenu

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Structural enforcement of design decision 7: back-gesture dismissal is deliberately not
 * implemented, because the card window is non-focusable and therefore receives no key events. A
 * wired dispatcher with nothing able to deliver to it would be dead code presented as a feature —
 * this test makes that impossible to reintroduce silently.
 *
 * Scans real source text under `feature/overlay/src/main`'s quick-menu package rather than
 * reflecting over compiled classes, so it fails the moment any of the four forbidden references
 * appears in a new or edited file, before the code would even need to run.
 */
class NoBackGestureCodeTest {

    private val forbiddenReferences = listOf(
        "OnBackPressedDispatcher",
        "setViewTreeOnBackPressedDispatcherOwner",
        "BackHandler",
        "KEYCODE_BACK",
    )

    /**
     * The Gradle test task's working directory is the module directory (`feature/overlay`), but
     * fall back to searching upward in case the runner differs — same resolution as
     * `NoGenericRejectionStringTest`.
     */
    private fun resolveQuickMenuSourceDir(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val candidate = File(dir, "src/main/kotlin/com/gcatcode/petmephone/feature/overlay/quickmenu")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return candidate
        }
        return File(dir, "src/main/kotlin/com/gcatcode/petmephone/feature/overlay/quickmenu")
    }

    @Test
    fun `no back-dispatcher or key-interception reference exists in the quick-menu package`() {
        val quickMenuSourceDir = resolveQuickMenuSourceDir()
        check(quickMenuSourceDir.isDirectory) {
            "expected ${quickMenuSourceDir.absolutePath} to exist — did the package move?"
        }

        val offendingHits = mutableListOf<String>()
        quickMenuSourceDir.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                forbiddenReferences.forEach { forbidden ->
                    if (text.contains(forbidden)) {
                        offendingHits += "${file.path}: $forbidden"
                    }
                }
            }

        assertFalse(
            "found forbidden back-gesture reference(s) in the quick-menu package:\n" +
                offendingHits.joinToString("\n"),
            offendingHits.isNotEmpty(),
        )
    }
}
