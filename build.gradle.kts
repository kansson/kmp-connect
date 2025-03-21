import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.plugin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.detekt)
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

detekt {
    source.from(rootProject.rootDir)
    parallel = true
    config.from("detekt.yaml")
    buildUponDefaultConfig = true
}

tasks.withType<Detekt>().configureEach {
    exclude("**/build/**")
}
