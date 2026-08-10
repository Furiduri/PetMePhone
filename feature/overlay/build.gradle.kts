plugins {
    id("com.petmephone.android.library")
    id("com.petmephone.android.compose")
    id("com.petmephone.android.hilt")
}

android {
    namespace = "com.gcatcode.petmephone.feature.overlay"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.savedstate.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    // Needed only so PetOverlayServiceTest can run through a real (Robolectric-hosted)
    // @AndroidEntryPoint onCreate, which unconditionally calls Hilt's generated inject() before
    // any test code runs; HiltTestApplication supplies the component that requires, and
    // :core:data supplies the bindings that component aggregates. Test-only: production main
    // source still resolves the graph exclusively through :app, unchanged.
    testImplementation(libs.hilt.android.testing)
    testImplementation(project(":core:data"))
    kspTest(libs.hilt.android.compiler)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.bundles.compose.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
