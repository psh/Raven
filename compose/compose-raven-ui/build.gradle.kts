plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    sourceSets.nativeMain.dependencies {
        implementation(project(":compose-raven-native"))
        api(libs.compose.runtime)
        // Each window's LifecycleOwner and SavedStateRegistryOwner, and their CompositionLocals.
        api(libs.lifecycle.runtime.compose)
        api(libs.savedstate.compose)
    }
    // The module's own tests use its internal API throughout.
    sourceSets.matching { it.name.endsWith("Test") }.all { languageSettings.optIn("raven.compose.ui.InternalComposeRavenApi") }
}
