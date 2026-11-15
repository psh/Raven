// Spike: OpenStreetMap vector tiles drawn by compose-raven itself, on a Canvas, with no native map
// library: the tiles' protobuf decoded, polygons triangulated (a port of earcut), lines and fills
// styled by MapLibre's style JSON, interpreted in part.
// Tiles: fetched from OpenFreeMap as the map needs them, through a disk cache (Ktor); or, with
// MAPS_OFFLINE, ./fetch-data's tiles of central Chicago, kept out of git.
// Run: ./launch-maps; test: ./gradlew :spikes:maps:allTests
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.executable {
            entryPoint = "raven.spike.maps.main"
            baseName = "maps"
        }
    }
    sourceSets.nativeMain.dependencies {
        implementation(project(":compose-raven-foundation"))
        // Labels measure their text with Raven's fonts (Rfont_measure_text).
        implementation(project(":compose-raven-native"))
        implementation(libs.ktor.client.core)
    }
    // Ktor's engine for this host: HttpClient() finds it.
    sourceSets.matching { it.name == "appleMain" }.configureEach { dependencies { implementation(libs.ktor.client.darwin) } }
    sourceSets.matching { it.name == "linuxMain" }.configureEach { dependencies { implementation(libs.ktor.client.curl) } }
    sourceSets.nativeTest.dependencies {
        implementation(project(":compose-raven-test"))
    }
}
