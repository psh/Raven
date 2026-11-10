plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    sourceSets.nativeMain.dependencies {
        api(project(":compose-raven-ui"))
        implementation(project(":raven-native"))
    }
}
