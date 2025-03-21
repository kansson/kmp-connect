import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.publish)
}

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

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    pom {
        name = "kmp-connect"
        description = "Kotlin Multiplatform implementation for Connect RPC."
        url = "https://github.com/kansson/kmp-connect"
        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/mit"
            }
        }
        developers {
            developer {
                id = "kansson"
                name = "Isak Hansson"
            }
        }
        scm {
            connection = "scm:git:https://github.com/kansson/kmp-connect.git"
            developerConnection = "scm:git:ssh://git@github.com/kansson/kmp-connect.git"
            url = "https://github.com/kansson/kmp-connect"
        }
    }
}
