package com.gcatcode.petmephone.core.domain.balance

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `balance-configuration` spec, "A grep for balance literals outside BalanceConfig finds none":
 * greps every Kotlin source file under the repository root for [BalanceConfig]'s default numeric
 * literals, outside `BalanceConfig.kt` itself and its tests. Balance values are injected, never
 * referenced globally.
 */
class NoBalanceLiteralOutsideConfigTest {

    /**
     * Only literals that are distinctive enough not to false-positive against unrelated code
     * (`recurringHungerRatio = 3`, `recurringHungerCap = 4`). `dailyTaskGoal = 10`, `0.6`, and `1`
     * are too common outside balance code to grep reliably and are covered instead by
     * [BalanceConfigTest]'s default-value assertions.
     */
    private val exemptFiles = setOf("BalanceConfig.kt")
    private val exemptDirSegments = listOf("/test/", "\\test\\", "/androidTest/", "\\androidTest\\")

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }

    private fun kotlinSourceFiles(): List<File> = repoRoot().walkTopDown()
        .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
        .filter { it.isFile && it.extension == "kt" }
        .filterNot { it.name in exemptFiles }
        .filterNot { file -> exemptDirSegments.any { file.path.contains(it) } }
        .toList()

    @Test
    fun `recurring hunger literals appear nowhere outside BalanceConfig`() {
        val suspiciousPatterns = listOf(
            Regex("""recurringHungerRatio\s*=\s*3\b"""),
            Regex("""recurringHungerCap\s*=\s*4\b"""),
        )

        for (file in kotlinSourceFiles()) {
            val text = file.readText()
            for (pattern in suspiciousPatterns) {
                assertTrue(
                    "${file.path} contains a balance literal that should live only in BalanceConfig.kt: $pattern",
                    !pattern.containsMatchIn(text),
                )
            }
        }
    }
}
