package com.petmephone

import com.android.build.api.dsl.LibraryExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * Room convention. Currently has one consumer (`:core:data`) — extracted anyway for symmetry
 * with `.hilt` and to own the KSP arguments in one place; revisitable per design.md.
 *
 * Also wires `room-testing` (`MigrationTestHelper`) even though no migration exists yet
 * (`task-persistence` spec, design.md decision 11): the plugin already owns `room.schemaLocation`,
 * so the test-side schema directory is the same fact.
 *
 * `MigrationTestHelper`'s Android target loads schemas via `Instrumentation`'s
 * `Context.getAssets()` regardless of which constructor overload is used — confirmed against Room
 * 2.8.4's actual bytecode, which differs from this plugin's original assumption that the
 * driver-based `File` constructor argument would be read directly off the filesystem (the risk
 * design.md's decision 11 flagged). The schema directory is therefore wired into the `test`
 * source set's `assets`, the standard Room migration-test setup, in addition to the
 * `room.schemaDirectory` system property (kept for any consumer that does read the raw path).
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")

            val schemaDirectory = "$projectDir/schemas"

            dependencies.add("implementation", libs.findLibrary("androidx-room-runtime").get())
            dependencies.add("implementation", libs.findLibrary("androidx-room-ktx").get())
            dependencies.add("ksp", libs.findLibrary("androidx-room-compiler").get())
            dependencies.add("testImplementation", libs.findLibrary("androidx-room-testing").get())

            extensions.configure<KspExtension> {
                arg("room.schemaLocation", schemaDirectory)
                arg("room.incremental", "true")
            }

            extensions.configure<LibraryExtension> {
                sourceSets.getByName("test") {
                    assets.srcDirs(schemaDirectory)
                }
            }

            tasks.withType<Test>().configureEach {
                systemProperty("room.schemaDirectory", schemaDirectory)
            }
        }
    }
}
