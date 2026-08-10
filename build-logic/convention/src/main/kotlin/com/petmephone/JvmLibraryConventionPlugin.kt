package com.petmephone

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Pure-Kotlin module convention. Deliberately does not touch AGP and shares no base class with
 * the Android plugin family — sharing would couple pure-Kotlin compilation to AGP for nothing.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(ProjectConfig.jvmToolchain)
            }

            dependencies.add("testImplementation", libs.findLibrary("junit").get())
        }
    }
}
