plugins {
    id("raven.native-target")
    id("raven.compose-resources")
    alias(libs.plugins.kotlin.compose)
}

// A check module, not a port: Compose's composeResources/ through the raven.compose-resources plugin.

kotlin {
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.executable {
            entryPoint = "raven.sample.resources.main"
            baseName = "resources"
        }
    }
    sourceSets.nativeMain.dependencies { implementation(project(":compose-raven-foundation")) }
    sourceSets.nativeTest {
        dependencies {
            implementation(project(":compose-raven-test"))
            implementation(project(":compose-raven-native"))
        }
    }
}
