plugins {
    id("com.petmephone.android.application")
    id("com.petmephone.android.compose")
    id("com.petmephone.android.hilt")
    id("com.petmephone.android.hilt.work")
}

android {
    namespace = "com.gcatcode.petmephone"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:overlay"))
    implementation(project(":feature:tasks"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    // Declared here, never in the `.compose` convention plugin, so the shared plugin keeps its
    // "no Activity-specific artifacts" rule. `:app` is the only module that hosts screens.
    implementation(libs.androidx.activity.compose)

    // Robolectric-backed unit test proving `PetMePhoneApplication`'s real, un-substituted
    // `Configuration.Provider` wiring (dependency-injection spec, "Single WorkManager instance
    // at cold start") — the one scenario `androidTest` cannot reach, since `CustomTestRunner`
    // substitutes `HiltTestApplication` for the production `Application` there.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    // Navigation-host test: Robolectric's native-graphics mode runs `createComposeRule` under the
    // plain JVM test task, so proving every destination is reachable needs no instrumentation.
    testImplementation(libs.bundles.compose.test)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.mockk)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Placeholder @HiltWorker test support (task 3.11-3.13) — the worker itself is
    // androidTest-only, per the recorded assumption; it never ships in `main`.
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.work.testing)
    kspAndroidTest(libs.hilt.android.compiler)
    kspAndroidTest(libs.androidx.hilt.compiler)
}
