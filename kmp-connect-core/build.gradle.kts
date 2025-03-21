plugins {
    `maven-publish`
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.android.library)
}

group = "com.kansson.kmp"
version = "1.0"

kotlin {
    explicitApi()
    jvmToolchain(21)

    jvm()
    androidTarget {
        publishLibraryVariants("release")
    }
    iosArm64()
    iosX64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.kermit)
        }
    }
}

android {
    namespace = "com.kansson.kmp.connect.core"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
}
