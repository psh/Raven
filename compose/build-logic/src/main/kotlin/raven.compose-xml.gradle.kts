// Raven XML in an app of this repository: the files of src/nativeMain/xml/ translated into Kotlin by
// raven-xml2compose during the build, one file each, subfolders as subpackages of
// `ravenXml { packageName }`, the output a source folder of nativeMain. The tool is built from
// :tools:raven-xml2compose first; apps outside this repository wait for its release (phase 4).

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

val ravenXml = extensions.create<RavenXmlExtension>("ravenXml")
ravenXml.source.convention("src/nativeMain/xml")
ravenXml.werror.convention(false)

/** The tool's debug executable for [target], and the task that links it. */
fun toolOf(target: String): Pair<File, String> {
    val tool = rootProject.layout.projectDirectory.dir("tools/raven-xml2compose").asFile
    val pascal = target.replaceFirstChar { it.uppercase() }
    return File(tool, "build/bin/$target/debugExecutable/raven-xml2compose.kexe") to ":tools:raven-xml2compose:linkDebugExecutable$pascal"
}

plugins.withId("org.jetbrains.kotlin.multiplatform") {
    val multiplatform = extensions.getByType<KotlinMultiplatformExtension>()
    multiplatform.targets.withType<KotlinNativeTarget>().configureEach {
        val target = name
        val translate = tasks.register<TranslateRavenXml>("translateRavenXml" + target.replaceFirstChar { it.uppercase() }) {
            description = "Translates the Raven XML of ${ravenXml.source.get()} into Kotlin, for $target."
            group = "raven"
            source.set(layout.projectDirectory.dir(ravenXml.source))
            packageName.set(ravenXml.packageName)
            main.set(ravenXml.main)
            werror.set(ravenXml.werror)
            outputDirectory.set(layout.buildDirectory.dir("generated/ravenXml/$target"))
            if (ravenXml.tool.isPresent) {
                tool.set(ravenXml.tool)
            } else {
                val (file, link) = toolOf(target)
                tool.set(file)
                dependsOn(link)
            }
        }
        multiplatform.sourceSets.matching { it.name == "nativeMain" }.configureEach { kotlin.srcDir(translate) }
    }
}
