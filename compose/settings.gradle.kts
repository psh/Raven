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
    ":compose-raven-xml",
    ":compose-raven-ui-test",
    ":compose-raven-test",
    ":raven-mcp",
    ":compose-raven-mcp",
    ":samples:animations",
    ":samples:hello",
    ":samples:music",
    ":samples:components",
    ":samples:editor",
    ":samples:effects",
    ":samples:viewport-opengl",
    ":samples:viewport-vulkan",
    ":samples:glass",
    ":samples:notepad",
    ":samples:notes",
    ":samples:overlay",
    ":samples:resources",
    ":samples:resources-embedded",
    ":samples:themes",
    ":samples:workspace",
    ":samples:xml-translator",
    ":samples:xml-examples",
    ":tools:raven-xml2compose",
    ":spikes:nested-frames",
    ":spikes:maps",
    ":spikes:navigation3",
    ":spikes:thorvg",
)
