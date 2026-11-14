plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

// The test harness for apps and for compose-raven's own tests: runRavenTest, the finders, actions
// and assertions, the test clock and the leak checks. Apps add it as a test dependency.

kotlin {
    sourceSets.nativeMain.dependencies {
        api(project(":compose-raven-foundation"))
        // Its failures are kotlin.test's, and its tests are written with it.
        api(kotlin("test"))
        implementation(project(":compose-raven-native"))
    }
    // The harness drives compose-raven's host and node tree, its binding layer.
    // Only its own code: its tests use the public API alone, as an app's do.
    sourceSets.matching { it.name.endsWith("Main") }.all { languageSettings.optIn("raven.compose.ui.InternalComposeRavenApi") }
}
