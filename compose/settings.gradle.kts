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
    ":raven-native",
    ":compose-raven-ui",
    ":compose-raven-foundation",
    ":compose-raven-vulkan",
    ":samples:hello",
    ":samples:components",
    ":samples:notepad",
    ":samples:overlay",
    ":samples:themes",
    ":samples:xml-translator",
    ":spikes:nested-frames",
    ":spikes:navigation3",
    ":spikes:thorvg",
)
