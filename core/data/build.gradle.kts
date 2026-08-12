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
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // Needed only so BalanceConfigInjectionTest can build a real (Robolectric-hosted) Hilt
    // SingletonComponent and resolve BalanceConfig through it — HiltTestApplication supplies the
    // component, DataModule (this module's own main source) supplies the binding.
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)
}
