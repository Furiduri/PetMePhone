plugins {
    `kotlin-dsl`
}

group = "com.petmephone.buildlogic"

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.compose.compiler.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    implementation(libs.hilt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = "com.petmephone.jvm.library"
            implementationClass = "com.petmephone.JvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "com.petmephone.android.library"
            implementationClass = "com.petmephone.AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "com.petmephone.android.application"
            implementationClass = "com.petmephone.AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = "com.petmephone.android.compose"
            implementationClass = "com.petmephone.AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "com.petmephone.android.hilt"
            implementationClass = "com.petmephone.AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "com.petmephone.android.room"
            implementationClass = "com.petmephone.AndroidRoomConventionPlugin"
        }
        register("androidHiltWork") {
            id = "com.petmephone.android.hilt.work"
            implementationClass = "com.petmephone.AndroidHiltWorkConventionPlugin"
        }
    }
}
