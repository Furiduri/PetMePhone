package com.petmephone

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Wires Hilt + its KSP processors into a module. Declares no bindings — those live in
 * `:core:data` per the dependency-injection spec.
 */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("dagger.hilt.android.plugin")

            dependencies.add("implementation", libs.findLibrary("hilt-android").get())
            dependencies.add("ksp", libs.findLibrary("hilt-android-compiler").get())
            dependencies.add("implementation", libs.findLibrary("androidx-hilt-work").get())
            dependencies.add("ksp", libs.findLibrary("androidx-hilt-compiler").get())
        }
    }
}
