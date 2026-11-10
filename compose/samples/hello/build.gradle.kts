plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.executable { entryPoint = "main" }
    }
    sourceSets.nativeMain.dependencies {
        implementation(project(":compose-raven-foundation"))
    }
}
