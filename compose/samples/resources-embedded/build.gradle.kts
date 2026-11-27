plugins {
    id("raven.native-target")
    id("raven.compose-resources")
    alias(libs.plugins.kotlin.compose)
}

// samples/resources built with its files embedded: the same code, the same composeResources/ and the
// same tests, and no folder beside the executable. A check of `ravenResources { embed = true }`.

val sample = rootProject.layout.projectDirectory.dir("samples/resources")

ravenResources {
    embed = true
    composeResourcesDirectory = sample.dir("src/nativeMain/composeResources")
    // The sample's package, so its code compiles here unchanged.
    packageOfResClass = "compose_raven.samples.resources.generated.resources"
}

kotlin {
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.executable {
            entryPoint = "raven.sample.resources.main"
            baseName = "resources-embedded"
        }
    }
    sourceSets.nativeMain {
        kotlin.srcDir(sample.dir("src/nativeMain/kotlin"))
        dependencies { implementation(project(":compose-raven-foundation")) }
    }
    sourceSets.nativeTest {
        kotlin.srcDir(sample.dir("src/nativeTest/kotlin"))
        dependencies {
            implementation(project(":compose-raven-test"))
            implementation(project(":compose-raven-native"))
        }
    }
}
