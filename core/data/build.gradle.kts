plugins {
    id("com.petmephone.android.library")
    id("com.petmephone.android.hilt")
    id("com.petmephone.android.room")
}

android {
    namespace = "com.gcatcode.petmephone.core.data"
}

dependencies {
    implementation(project(":core:domain"))

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
