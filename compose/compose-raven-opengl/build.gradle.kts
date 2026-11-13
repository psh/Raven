plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

// The opt-in OpenGL module: a GL context on a native child window, and GL rendered into a bitmap.
// The app brings its own GL bindings: on macOS, Kotlin/Native's platform.OpenGL3.

kotlin {
    sourceSets.nativeMain.dependencies {
        api(project(":compose-raven-foundation"))
        implementation(project(":raven-native"))
    }
    // The tests lay out with foundation's modifiers; the module's own tests use its internals.
    sourceSets.all { languageSettings.optIn("raven.compose.ui.InternalComposeRavenApi") }
}
