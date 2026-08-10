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
}
