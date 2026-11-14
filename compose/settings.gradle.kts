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
    ":compose-raven-navigation3",
    ":samples:animations",
    ":samples:hello",
    ":samples:components",
    ":samples:editor",
    ":samples:glass",
    ":samples:notepad",
    ":samples:overlay",
    ":samples:themes",
    ":samples:workspace",
    ":samples:xml-translator",
    ":spikes:nested-frames",
    ":spikes:navigation3",
    ":spikes:thorvg",
)
