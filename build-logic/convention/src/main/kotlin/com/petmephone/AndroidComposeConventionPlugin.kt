package com.petmephone

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Shared Compose convention for both application and library hosts. Contributes no
 * Activity-specific artifact — the overlay hosts Compose from a Service, not an Activity.
 *
 * Question B is closed: on AGP 9.3.1, `CommonExtension` has zero type parameters (verified
 * against jar bytecode). Configure it by plain property access; do not re-introduce a star
 * projection or an `androidComponents` fallback.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val ext: CommonExtension = project.extensions.getByType(CommonExtension::class.java)
            ext.buildFeatures.compose = true

            dependencies.add("implementation", dependencies.platform(libs.findLibrary("androidx-compose-bom").get()))
            dependencies.add("implementation", libs.findLibrary("androidx-compose-ui").get())
            dependencies.add("implementation", libs.findLibrary("androidx-compose-ui-graphics").get())
            dependencies.add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
            dependencies.add("implementation", libs.findLibrary("androidx-compose-material3").get())
            dependencies.add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
        }
    }
}
