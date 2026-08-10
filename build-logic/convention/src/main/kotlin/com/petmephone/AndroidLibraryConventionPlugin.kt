package com.petmephone

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

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
                // Robolectric needs Android resources on the unit-test classpath; without this,
                // any module wiring Robolectric (:core:data) fails confusingly on its first test.
                testOptions {
                    unitTests.isIncludeAndroidResources = true
                }
                // AGP 9.3's built-in Kotlin compilation reads the JVM target from compileOptions,
                // not from a separate Kotlin Gradle plugin toolchain block.
                compileOptions {
                    sourceCompatibility = JavaVersion.toVersion(ProjectConfig.jvmToolchain)
                    targetCompatibility = JavaVersion.toVersion(ProjectConfig.jvmToolchain)
                }
                resourcePrefix = target.path.removePrefix(":").replace(":", "_") + "_"
            }

            // isIncludeAndroidResources = true makes AGP add the merged-resources jar to the
            // unit-test classpath, which Gradle's JUnit Platform launcher then treats as a
            // populated "test source" root — failing modules with zero tests (:feature:overlay,
            // :feature:tasks) with "did not discover any tests to execute" even though this
            // slice's explicit invariant is "test infrastructure exists, zero tests run" (design.md).
            tasks.withType<Test>().configureEach {
                failOnNoDiscoveredTests.set(false)
            }
        }
    }
}
