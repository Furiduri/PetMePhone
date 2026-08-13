package com.gcatcode.petmephone.feature.overlay.quickmenu

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Structural enforcement of design decision 5b: the remembered [com.gcatcode.petmephone.core.domain.overlay.QuickMenuContent]
 * "SHALL NOT be written to persistent storage" (`quick-menu-text-input` — "The card reopens on the
 * content it was last left on"). No persistence exists to remove for this requirement (task 3.9);
 * this test holds that fact structurally, the same way [QuickMenuBackWiringCodeTest] holds the
 * back-wiring shape, so a later restoration "fix" cannot quietly grow a disk write to survive
 * process death — which decision 5b explicitly says is *not* required, and would outlive its own
 * meaning (a card reopened days later on a forgotten input is worse than the dashboard).
 */
class QuickMenuNoPersistenceCodeTest {

    private val forbiddenReferences = listOf(
        "DataStore",
        "SharedPreferences",
        "Room",
        "FileOutputStream",
        "FileWriter",
        "openFileOutput",
    )

    /**
     * The Gradle test task's working directory is the module directory (`feature/overlay`), but
     * fall back to searching upward in case the runner differs — same resolution as
     * [QuickMenuBackWiringCodeTest].
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
    fun `no persisted-storage reference exists in the quick-menu package`() {
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
            "found forbidden persisted-storage reference(s) in the quick-menu package:\n" +
                offendingHits.joinToString("\n"),
            offendingHits.isNotEmpty(),
        )
    }
}
