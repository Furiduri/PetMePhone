package com.gcatcode.petmephone.feature.overlay.quickmenu

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Replaces [NoBackGestureCodeTest] — deleted in this change. That test was a structural gate
 * asserting four back-related references were *absent* from the quick-menu package, because the
 * card window was non-focusable and therefore could not receive a back key at all; a wired
 * dispatcher or handler with nothing able to deliver to it would have been dead code presented as
 * a feature. This change makes the window focusable (design decision 1), so that premise no
 * longer holds, and the old assertion would now fail for the *right* reason forever — a gate that
 * fails permanently on purpose is not a gate, it is a landmine.
 *
 * This test inverts the earlier one rather than dropping it, reusing its file-scanning approach:
 * back handling still may not drift in unnoticed, it is just pointed the other way. It now guards
 * against a *second* back-handling mechanism (a raw `KEYCODE_BACK` interception, or more than one
 * `BackHandler`), which is the failure mode design decision 7 calls out as the actual risk once
 * the window can receive a real press: two independent handlers reintroduce an ordering bug
 * between themselves.
 *
 * PR 4 obligation, paid here: Phase 3's version of this file bounded the `BackHandler` count at
 * "at most one" because the count was honestly zero before the container existed — asserting
 * `== 1` then would have been a fabricated pass. `QuickMenuCard` (Phase 4) now hosts the package's
 * one and only `BackHandler`, so the bound is tightened to exactly one here, as recorded in that
 * file's own kdoc and in PR 3's body as PR 4's carried debt.
 *

 * `setViewTreeOnBackPressedDispatcherOwner` is called exactly once in the whole module (design
 * decision 8, added in Phase 2), but it lives in `ComposeOverlayHost.kt` under the sibling `ui/`
 * package, not `quickmenu/` — the same file this test's predecessor already excluded by scanning
 * only the `quickmenu` source tree (see Phase 2's apply-progress note). That call therefore never
 * appears in this file's scan, in this phase or any later one; its own "exactly once" property is
 * asserted directly by `ComposeOverlayHostTest`, not here. This file only asserts it stays absent
 * *within* the quick-menu package, which is the scope this test actually owns.
 */
class QuickMenuBackWiringCodeTest {

    /**
     * The Gradle test task's working directory is the module directory (`feature/overlay`), but
     * fall back to searching upward in case the runner differs — same resolution as
     * `NoGenericRejectionStringTest` and the predecessor this file replaces.
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
    fun `no raw KEYCODE_BACK interception exists in the quick-menu package`() {
        // The dispatcher (decision 8) is the one mechanism; a raw key interception would miss
        // gesture back on some configurations, exactly the silent failure slice 3-B refused.
        assertEquals(0, countOccurrences("KEYCODE_BACK"))
    }

    @Test
    fun `no second OnBackPressedDispatcherOwner wiring exists in the quick-menu package`() {
        // ComposeOverlayHost.kt's own setViewTreeOnBackPressedDispatcherOwner call lives outside
        // this package (see class kdoc) and therefore never counts here; this asserts the
        // quick-menu package itself never grows a second, competing dispatcher-owner attachment.
        assertEquals(0, countOccurrences("setViewTreeOnBackPressedDispatcherOwner"))
    }

    @Test
    fun `exactly one BackHandler call exists in the quick-menu package`() {
        // Tightened from "at most one" (Phase 3) now that QuickMenuCard hosts the package's one
        // and only BackHandler (design decision 8). A second handler would reintroduce the
        // ordering bug between two independent handlers that decision 7 calls out.
        //
        // Counts the invocation ("BackHandler(") rather than the bare identifier, so the single
        // `import androidx.activity.compose.BackHandler` line does not double the count.
        assertEquals(1, countOccurrences("BackHandler("))
    }
}
