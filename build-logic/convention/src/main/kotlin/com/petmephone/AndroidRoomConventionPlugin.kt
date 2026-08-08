package com.petmephone

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Room convention. Currently has one consumer (`:core:data`) — extracted anyway for symmetry
 * with `.hilt` and to own the KSP arguments in one place; revisitable per design.md.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")

            dependencies.add("implementation", libs.findLibrary("androidx-room-runtime").get())
            dependencies.add("implementation", libs.findLibrary("androidx-room-ktx").get())
            dependencies.add("ksp", libs.findLibrary("androidx-room-compiler").get())

            extensions.configure<KspExtension> {
                arg("room.schemaLocation", "$projectDir/schemas")
                arg("room.incremental", "true")
            }
        }
    }
}
