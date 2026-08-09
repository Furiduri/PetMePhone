plugins {
    id("com.petmephone.jvm.library")
}

dependencies {
    // api, not implementation: Flow is exposed in repository interface signatures consumed by
    // every module depending on :core:domain (design.md's api-for-Flow-interfaces rule).
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
