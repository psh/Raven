plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
    // The tests' NavKeys are @Serializable, as an app's are.
    alias(libs.plugins.kotlin.serialization)
}

// Navigation 3 over compose-raven: navigation3-runtime as on Android, with its Scene layer and
// NavDisplay adapted from AndroidX 1.2.1's navigation3-ui, which needs Compose UI.

kotlin {
    sourceSets.nativeMain.dependencies {
        api(project(":compose-raven-foundation"))
        api(libs.navigation3.runtime)
        implementation(project(":raven-native"))
    }
    // foundation's test harness, until it moves to compose-raven-test
    sourceSets.nativeTest { kotlin.srcDir(project(":compose-raven-foundation").file("src/testHarness/kotlin")) }
    sourceSets.all { languageSettings.optIn("raven.compose.ui.InternalComposeRavenApi") }
}
