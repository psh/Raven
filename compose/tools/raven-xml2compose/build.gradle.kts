import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("raven.native-target")
}

// raven-xml2compose: translates a folder of Raven XML into Kotlin files, headless, on
// compose-raven-xml. Built and tested here; the raven.compose-xml plugin runs it in the build of
// this repository's apps, and its per-host binaries wait for the release (phase 4).

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.executable {
            entryPoint = "raven.xml2compose.main"
            baseName = "raven-xml2compose"
        }
    }
    sourceSets.nativeMain.dependencies { implementation(project(":compose-raven-xml")) }
}
