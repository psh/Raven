plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    sourceSets.nativeMain.dependencies {
        api(project(":compose-raven-ui"))
        implementation(project(":raven-native"))
    }
    // The test harness, shared with the samples' tests until it moves to compose-raven-test.
    sourceSets.nativeTest { kotlin.srcDir("src/testHarness/kotlin") }
    // Foundation is built on compose-raven-ui's binding layer.
    sourceSets.all { languageSettings.optIn("raven.compose.ui.InternalComposeRavenApi") }
}
