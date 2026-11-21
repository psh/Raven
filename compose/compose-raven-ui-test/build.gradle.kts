plugins {
    id("raven.native-target")
}

// What tests and a live app's UI tools share (compose/docs/spec.md, section 9, "Shared with Raven
// MCP"): the matchers, finding nodes, the perform… actions and the fetch… reads, on any RavenHost,
// headless or live. No kotlin.test: a failure is a NodeException, which compose-raven-test reports
// as a test failure and compose-raven-mcp as a tool failure.

kotlin {
    sourceSets.nativeMain.dependencies {
        api(project(":compose-raven-foundation"))
        implementation(project(":compose-raven-native"))
    }
    sourceSets.all { languageSettings.optIn("raven.compose.ui.InternalComposeRavenApi") }
}
