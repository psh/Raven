// Verify-first spike (ticket 0097): Navigation 3's back stack and saveable-state decorator under
// compose-raven's host, with the kotlinx-serialization plugin. Headless tests only.
// Run: ./gradlew :spikes:navigation3:allTests
plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // foundation's test harness, until it moves to compose-raven-test
    sourceSets.nativeTest {
        kotlin.srcDir(project(":compose-raven-foundation").file("src/testHarness/kotlin"))
        dependencies {
            implementation(project(":compose-raven-foundation"))
            implementation(project(":compose-raven-native"))
            implementation("androidx.navigation3:navigation3-runtime:1.2.1")
        }
    }
    sourceSets.all { languageSettings.optIn("raven.compose.ui.InternalComposeRavenApi") }
}
