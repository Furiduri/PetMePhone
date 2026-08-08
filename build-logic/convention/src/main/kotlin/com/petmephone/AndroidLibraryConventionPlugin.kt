package com.petmephone

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Shared Android library convention. Deliberately does NOT set `namespace` — that stays
 * module-specific, declared in the consuming module's own script.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                compileSdk {
                    version = release(ProjectConfig.compileSdk)
                }
                defaultConfig {
                    minSdk = ProjectConfig.minSdk
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                // AGP 9.3's built-in Kotlin compilation reads the JVM target from compileOptions,
                // not from a separate Kotlin Gradle plugin toolchain block.
                compileOptions {
                    sourceCompatibility = JavaVersion.toVersion(ProjectConfig.jvmToolchain)
                    targetCompatibility = JavaVersion.toVersion(ProjectConfig.jvmToolchain)
                }
                resourcePrefix = target.path.removePrefix(":").replace(":", "_") + "_"
            }
        }
    }
}
