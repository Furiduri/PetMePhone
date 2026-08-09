package com.petmephone

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Wires `androidx.hilt:hilt-work` (`HiltWorkerFactory`) and its dedicated KSP compiler into a
 * module. Split out of `com.petmephone.android.hilt` because only `:app` hosts workers — applying
 * `hilt-work` to every Hilt-enabled module was imprecise (PR 3, #6 carried-forward item 1).
 *
 * Requires `com.petmephone.android.hilt` to already be applied (for `hilt-android` and its
 * KSP-generated Dagger components); this plugin does not apply KSP or the Hilt Gradle plugin
 * itself.
 */
class AndroidHiltWorkConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // work-runtime-ktx is declared explicitly, not inherited. hilt-work's own floor is
            // work-runtime 2.3.4, which predates FLAG_IMMUTABLE and crashes on API 31+.
            dependencies.add("implementation", libs.findLibrary("androidx-work-runtime-ktx").get())
            dependencies.add("implementation", libs.findLibrary("androidx-hilt-work").get())
            dependencies.add("ksp", libs.findLibrary("androidx-hilt-compiler").get())
        }
    }
}
