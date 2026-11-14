plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    sourceSets.nativeMain.dependencies {
        api(project(":compose-raven-ui"))
        // ScrollState and LazyListState are rememberSaveable.
        api(libs.compose.runtime.saveable)
        implementation(project(":compose-raven-native"))
    }
    sourceSets.nativeTest.dependencies { implementation(project(":compose-raven-test")) }
    // Foundation is built on compose-raven-ui's binding layer.
    sourceSets.all { languageSettings.optIn("raven.compose.ui.InternalComposeRavenApi") }
}
