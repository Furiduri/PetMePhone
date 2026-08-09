plugins {
    id("com.petmephone.android.application")
    id("com.petmephone.android.compose")
    id("com.petmephone.android.hilt")
    id("com.petmephone.android.hilt.work")
}

android {
    namespace = "com.gcatcode.petmephone"

    defaultConfig {
        // Hilt instrumented tests (@HiltAndroidTest) need HiltTestApplication in place of the
        // production Application class.
        testInstrumentationRunner = "com.gcatcode.petmephone.CustomTestRunner"
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:overlay"))
    implementation(project(":feature:tasks"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Placeholder @HiltWorker test support (task 3.11-3.13) — the worker itself is
    // androidTest-only, per the recorded assumption; it never ships in `main`.
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.work.testing)
    kspAndroidTest(libs.hilt.android.compiler)
    kspAndroidTest(libs.androidx.hilt.compiler)
}
