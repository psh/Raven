plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.executable {
            entryPoint = "raven.sample.hello.main"
            baseName = "hello"
        }
    }
    sourceSets.nativeMain.dependencies {
        implementation(project(":compose-raven-foundation"))
    }
    // foundation's test harness, until it moves to compose-raven-test
    sourceSets.nativeTest {
        kotlin.srcDir(project(":compose-raven-foundation").file("src/testHarness/kotlin"))
        dependencies { implementation(project(":raven-native")) }
    }
}
