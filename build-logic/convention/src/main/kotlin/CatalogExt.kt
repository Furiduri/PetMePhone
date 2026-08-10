package com.petmephone

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Runtime accessor for the `libs` version catalog. Convention plugin source has no typesafe
 * catalog accessors, so this reads the catalog by name at configuration time.
 */
val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")
