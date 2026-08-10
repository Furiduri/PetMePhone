package com.petmephone

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * The single application-module convention. `namespace` stays module-specific (there is only
 * one consumer, but the rule is consistent with the library plugin).
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                compileSdk {
                    version = release(ProjectConfig.compileSdk)
                }
                defaultConfig {
                    applicationId = "com.gcatcode.petmephone"
                    minSdk = ProjectConfig.minSdk
                    targetSdk = ProjectConfig.targetSdk
                    versionCode = 1
                    versionName = "1.0"
                    // CustomTestRunner, not AndroidJUnitRunner: Hilt's @HiltAndroidTest needs
                    // HiltTestApplication substituted in place of the production Application.
                    // This lives here rather than in the module script because the runner is a
                    // shared build value, and two owners for one value is what the single-owner
                    // requirement exists to prevent.
                    testInstrumentationRunner = "com.gcatcode.petmephone.CustomTestRunner"
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.toVersion(ProjectConfig.jvmToolchain)
                    targetCompatibility = JavaVersion.toVersion(ProjectConfig.jvmToolchain)
                }
                // Robolectric needs Android resources on the unit-test classpath, same as
                // AndroidLibraryConventionPlugin. `:app` is the only application module, so this
                // mirrors the library plugin's rule rather than sharing a base class with it.
                testOptions {
                    unitTests.isIncludeAndroidResources = true
                }
            }

            // Same rationale as AndroidLibraryConventionPlugin: isIncludeAndroidResources = true
            // makes AGP add the merged-resources jar to the unit-test classpath, which the
            // JUnit Platform launcher then treats as a populated "test source" root.
            tasks.withType<Test>().configureEach {
                failOnNoDiscoveredTests.set(false)
            }
        }
    }
}
