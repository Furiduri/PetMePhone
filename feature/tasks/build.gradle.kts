plugins {
    id("com.petmephone.android.library")
    id("com.petmephone.android.compose")
    id("com.petmephone.android.hilt")
}

android {
    namespace = "com.gcatcode.petmephone.feature.tasks"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.bundles.compose.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
