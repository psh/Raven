plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The notes every language's samples have: add a note, select it, delete it. It publishes
// notes.add, notes.list and notes.delete to agents through Raven MCP, and the UI tools with
// RAVEN_MCP_UI=1, when started with RAVEN_MCP=1.

kotlin {
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.executable {
            entryPoint = "raven.sample.notes.main"
            baseName = "notes"
        }
    }
    sourceSets.nativeMain.dependencies {
        implementation(project(":compose-raven-foundation"))
        implementation(project(":compose-raven-mcp"))
    }
    sourceSets.nativeTest.dependencies { implementation(project(":compose-raven-test")) }
    // The tests reach the server through compose-raven-mcp's test client.
    sourceSets.matching { it.name.endsWith("Test") }.all { languageSettings.optIn("raven.compose.ui.InternalComposeRavenApi") }
}
