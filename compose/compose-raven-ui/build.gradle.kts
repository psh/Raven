plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    sourceSets.nativeMain.dependencies {
        implementation(project(":raven-native"))
        api(libs.compose.runtime)
    }
    // The module's own tests use its internal API throughout.
    sourceSets.matching { it.name.endsWith("Test") }.all { languageSettings.optIn("raven.compose.ui.InternalComposeRavenApi") }
}
