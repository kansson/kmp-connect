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
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.protobuf.kotlin)
            implementation(libs.poet.kotlin)
            implementation(libs.kermit)
        }
    }
}
