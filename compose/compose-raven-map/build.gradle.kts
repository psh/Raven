plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

// The opt-in map module: MapView, a composable over Raven's Map widget, which decodes, styles and
// draws vector tiles in C. The app brings its tiles (TileSource): the module fetches nothing itself.

kotlin {
    sourceSets.nativeMain.dependencies {
        api(project(":compose-raven-foundation"))
        implementation(project(":compose-raven-native"))
    }
    sourceSets.nativeTest.dependencies { implementation(project(":compose-raven-test")) }
    // The map is built on compose-raven-ui's binding layer.
    sourceSets.all { languageSettings.optIn("raven.compose.ui.InternalComposeRavenApi") }
}
