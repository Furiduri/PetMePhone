package com.petmephone

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Wires Hilt + its KSP processors into a module. Declares no bindings — those live in
 * `:core:data` per the dependency-injection spec.
 *
 * Deliberately does NOT add `hilt-work`/`androidx.hilt:hilt-compiler`/`work-runtime-ktx` — only
 * `:app` hosts workers, so that WorkManager-specific wiring lives in `com.petmephone.android.work`
 * instead (PR 3, #6 carried-forward item 1).
 */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("dagger.hilt.android.plugin")

            dependencies.add("implementation", libs.findLibrary("hilt-android").get())
            dependencies.add("ksp", libs.findLibrary("hilt-android-compiler").get())
        }
    }
}
