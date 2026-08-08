plugins {
    id("com.petmephone.android.application")
    id("com.petmephone.android.compose")
    id("com.petmephone.android.hilt")
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
}
