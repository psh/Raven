import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("raven.native-target")
    id("raven.compose-xml")
    alias(libs.plugins.kotlin.compose)
}

// The examples check, a check module rather than a port: every .xml file of Raven's examples/ is
// translated through the raven.compose-xml plugin, each example in its subpackage of `examples`,
// and the output is compiled against the current compose-raven-foundation, so the translator can't
// drift from its API.

ravenXml {
    source = "../../../examples"
    packageName = "examples"
}

kotlin {
    sourceSets.nativeMain.dependencies { implementation(project(":compose-raven-foundation")) }
    targets.withType<KotlinNativeTarget>().configureEach {
        val compile = compilations.getByName("main").compileTaskProvider
        tasks.named("check") { dependsOn(compile) }
    }
}
