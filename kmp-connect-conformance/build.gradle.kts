plugins {
    application
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
}

application {
    mainClass = "com.kansson.kmp.connect.conformance.MainKt"
}

kotlin {
    explicitApi()
    jvmToolchain(21)

    jvm {
        withJava()
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlinx.serialization.ExperimentalSerializationApi")
                optIn("kotlin.io.encoding.ExperimentalEncodingApi")
            }
        }

        commonMain {
            kotlin.srcDir("./build/generated/sources/bufgen")
            dependencies {
                implementation(projects.kmpConnectCore)
                implementation(libs.kotlinx.serialization.protobuf)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.java)
                implementation(libs.kermit)
            }
        }
    }
}
