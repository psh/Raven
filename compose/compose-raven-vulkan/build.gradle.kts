import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

// The opt-in Vulkan module: Raven's Vulkan handles, viewports drawn with Vulkan, and Vulkan images
// shown as bitmaps. raven_vulkan.h exchanges Vulkan handles as opaque pointers and 64-bit integers,
// so the module needs no Vulkan SDK; the app brings its own Vulkan bindings.

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops.create("ravenVulkan") {
            definitionFile.set(layout.projectDirectory.file("src/nativeInterop/cinterop/raven_vulkan.def"))
            includeDirs(rootProject.layout.projectDirectory.dir("../include").asFile)
        }
    }
    sourceSets.nativeMain.dependencies {
        api(project(":compose-raven-ui"))
        implementation(project(":raven-native"))
    }
    // The tests lay out with foundation's modifiers.
    sourceSets.nativeTest.dependencies { implementation(project(":compose-raven-foundation")) }
    sourceSets.all { languageSettings.optIn("raven.compose.ui.InternalComposeRavenApi") }
}
