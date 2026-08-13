// `:spike:ime-viability` — a standalone Android application, deliberately independent of the
// shared convention plugins (see design.md decision 13). It carries its own `applicationId`, is
// never included in `:app`'s dependency graph, and is never part of a release variant: it exists
// solely to be installed and run by the maintainer on real hardware to answer whether in-overlay
// text entry is viable. It shares no code with the quick-menu card — not the controller, not the
// placement math, not `MetricReading`.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.petmephone.spike.imeviability"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.petmephone.spike.imeviability"
        // Same floor as the rest of the app (`ProjectConfig.minSdk`), duplicated as a literal
        // here rather than importing `ProjectConfig` because this module is deliberately outside
        // the shared convention-plugin graph (decision 13) — it has no other reason to depend on
        // build-logic at all, and adding one just for this constant would undercut the isolation
        // the module exists to have.
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
