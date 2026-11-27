import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

// Raven XML on the left, the compose-raven code it translates to on the right, live. The examples
// check that used to live here is samples/xml-examples, over the raven.compose-xml plugin.

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.executable {
            entryPoint = "raven.sample.xml.main"
            baseName = "xml-translator"
        }
    }
    sourceSets.nativeMain.dependencies {
        implementation(project(":compose-raven-foundation"))
        implementation(project(":compose-raven-xml"))
    }
    sourceSets.nativeTest.dependencies { implementation(project(":compose-raven-test")) }
}
