// Verify-first spike (ticket 0053): Compose recomposing inside the nested frames that Raven's
// blocking dialogs run. Headless tests only. Run: ./gradlew :spikes:nested-frames:allTests
plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    // foundation's test harness, until it moves to compose-raven-test
    sourceSets.nativeTest {
        kotlin.srcDir(project(":compose-raven-foundation").file("src/testHarness/kotlin"))
        dependencies {
            implementation(project(":compose-raven-foundation"))
            implementation(project(":compose-raven-native"))
        }
    }
    sourceSets.all { languageSettings.optIn("raven.compose.ui.InternalComposeRavenApi") }
}
