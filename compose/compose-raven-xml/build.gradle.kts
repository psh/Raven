plugins {
    id("raven.native-target")
}

// The XML translator as a library: Raven XML parsed with Raven's own parser (through
// compose-raven-native, which stays out of its API) and translated into compose-raven source.
// Used by samples/xml-translator and the raven-xml2compose tool; publishing waits for phase 4.

kotlin {
    sourceSets.nativeMain.dependencies {
        implementation(project(":compose-raven-native"))
    }
}
