// Throwaway project: resolves the phase 3 dependencies for compose-raven's targets, to check
// that none of them needs Compose UI. Run: gradle -q resolve
pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "compose-raven-dependency-spike"
