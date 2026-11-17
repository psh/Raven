// Spike: OpenStreetMap vector tiles, styled by MapLibre's style JSON, drawn by Raven's Map widget
// through compose-raven-map's MapView (0140).
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
        // The map drawn by Raven's Map widget (0140).
        implementation(project(":compose-raven-map"))
        implementation(libs.ktor.client.core)
    }
    // Ktor's engine for this host: HttpClient() finds it.
    sourceSets.matching { it.name == "appleMain" }.configureEach { dependencies { implementation(libs.ktor.client.darwin) } }
    sourceSets.matching { it.name == "linuxMain" }.configureEach { dependencies { implementation(libs.ktor.client.curl) } }
}
