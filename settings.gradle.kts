pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PetMePhone"
include(":app", ":core:domain", ":core:data", ":core:designsystem", ":feature:overlay", ":feature:tasks")

// Standalone measuring instrument (design.md decision 13). Never a dependency of `:app`, never
// part of a release variant — see `spike/ime-viability/README.md`.
include(":spike:ime-viability")
project(":spike:ime-viability").projectDir = file("spike/ime-viability")
