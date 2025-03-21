@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    explicitApi()
    jvmToolchain(21)

    jvm {
        binaries {
            executable {
                mainClass = "com.kansson.kmp.connect.protocgen.MainKt"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.kmpConnectCore)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.protobuf.kotlin)
            implementation(libs.poet.kotlin)
            implementation(libs.ktor.client.core)
            implementation(libs.kermit)
        }
    }
}
