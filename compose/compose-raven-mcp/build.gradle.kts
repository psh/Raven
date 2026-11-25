plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Raven MCP for compose-raven apps (compose/docs/mcp.md, "compose-raven: McpTool"): McpConfig and
// the mcp overloads of application, McpTool, McpStatus, and the UI tools over Compose semantics.
// Opt-in: an app without it never sees MCP. It runs on raven-mcp's klib, which links the libraven
// the app already has.

kotlin {
    sourceSets.nativeMain.dependencies {
        api(project(":raven-mcp"))
        api(project(":compose-raven-foundation"))
        api(libs.kotlinx.serialization.json)
        implementation(project(":compose-raven-ui-test"))
        implementation(project(":compose-raven-native"))
    }
    sourceSets.nativeTest.dependencies { implementation(project(":compose-raven-test")) }
    sourceSets.all { languageSettings.optIn("raven.compose.ui.InternalComposeRavenApi") }
}
