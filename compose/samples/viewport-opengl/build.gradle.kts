plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.executable {
            entryPoint = "raven.sample.viewportopengl.main"
            baseName = "viewport-opengl"
        }
    }
    sourceSets.nativeMain.dependencies { implementation(project(":compose-raven-opengl")) }
    sourceSets.nativeTest {
        dependencies {
            implementation(project(":compose-raven-test"))
            implementation(project(":compose-raven-native"))
        }
    }
}
