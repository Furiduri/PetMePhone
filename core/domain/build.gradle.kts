plugins {
    id("com.petmephone.jvm.library")
}

dependencies {
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
