// Verify-first spike (ticket 0053): Compose recomposing inside the nested frames that Raven's
// blocking dialogs run. Headless tests only. Run: ./gradlew :spikes:nested-frames:allTests
plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    sourceSets.nativeTest {
        dependencies {
            implementation(project(":compose-raven-test"))
            implementation(project(":compose-raven-foundation"))
            implementation(project(":compose-raven-native"))
        }
    }
    sourceSets.all { languageSettings.optIn("raven.compose.ui.InternalComposeRavenApi") }
}
