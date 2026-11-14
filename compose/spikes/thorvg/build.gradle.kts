// Spike: ThorVG (C API, CPU engine) rendering Lottie frames for compose-raven, through cinterop.
// Needs ThorVG installed (brew install thorvg), or THORVG_PREFIX pointing at an install.
// Run: ./gradlew :spikes:thorvg:allTests
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

val thorvgPrefix = System.getenv("THORVG_PREFIX") ?: "/opt/homebrew/opt/thorvg"

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("test").cinterops.create("thorvg") {
            includeDirs("$thorvgPrefix/include/thorvg-1")
        }
        binaries.all { linkerOpts("-L$thorvgPrefix/lib", "-Wl,-rpath,$thorvgPrefix/lib") }
    }
    sourceSets.nativeTest {
        dependencies {
            implementation(project(":compose-raven-test"))
            implementation(project(":compose-raven-foundation"))
            implementation(project(":compose-raven-native"))
        }
    }
    sourceSets.all { languageSettings.optIn("raven.compose.ui.InternalComposeRavenApi") }
}
