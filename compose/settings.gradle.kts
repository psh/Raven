pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "compose-raven"

include(
    ":compose-raven-native",
    ":compose-raven-ui",
    ":compose-raven-foundation",
    ":compose-raven-vulkan",
    ":compose-raven-opengl",
    ":compose-raven-map",
    ":compose-raven-navigation3",
    ":compose-raven-test",
    ":raven-mcp",
    ":samples:animations",
    ":samples:hello",
    ":samples:music",
    ":samples:components",
    ":samples:editor",
    ":samples:glass",
    ":samples:notepad",
    ":samples:overlay",
    ":samples:themes",
    ":samples:workspace",
    ":samples:xml-translator",
    ":spikes:nested-frames",
    ":spikes:maps",
    ":spikes:navigation3",
    ":spikes:thorvg",
)
