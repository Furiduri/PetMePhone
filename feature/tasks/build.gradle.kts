plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.gcatcode.petmephone.feature.tasks"
    resourcePrefix = "feature_tasks_"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
