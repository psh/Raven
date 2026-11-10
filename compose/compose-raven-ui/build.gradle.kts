plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    sourceSets.nativeMain.dependencies {
        implementation(project(":raven-native"))
        api(libs.compose.runtime)
    }
}
