plugins {
    id("com.petmephone.android.library")
    id("com.petmephone.android.compose")
}

android {
    namespace = "com.gcatcode.petmephone.core.designsystem"
}

dependencies {
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.bundles.compose.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
