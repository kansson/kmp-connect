plugins {
    application
    alias(libs.plugins.kotlin.multiplatform)
}

application {
    mainClass = "com.kansson.kmp.connect.protocgen.MainKt"
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
