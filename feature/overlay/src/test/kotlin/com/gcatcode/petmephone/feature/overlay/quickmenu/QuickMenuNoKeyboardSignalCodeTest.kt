package com.gcatcode.petmephone.feature.overlay.quickmenu

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * `quick-menu-text-input`'s "content selection reads no keyboard-visibility or inset signal"
 * requirement, held structurally: `WindowInsets.ime` is never delivered to this window class, and
 * `getWindowVisibleDisplayFrame` measured unreliably across runs (design.md, measured facts 3–4).
 * The shown content must be a pure function of explicit user actions only — no logic anywhere in
 * the quick-menu package (including its `ui/` sub-package) may read either signal.
 *
 * Same file-scanning approach as `QuickMenuBackWiringCodeTest`/`QuickMenuNoPersistenceCodeTest`,
 * scanning the whole `quickmenu` source tree, which includes `ui/` where the container and its
 * contents live.
 */
class QuickMenuNoKeyboardSignalCodeTest {

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
    fun `no WindowInsets ime reference exists in the quick-menu package`() {
        assertEquals(0, countOccurrences("WindowInsets.ime") + countOccurrences("imePadding"))
    }

    @Test
    fun `no getWindowVisibleDisplayFrame reference exists in the quick-menu package`() {
        assertEquals(0, countOccurrences("getWindowVisibleDisplayFrame"))
    }
}
