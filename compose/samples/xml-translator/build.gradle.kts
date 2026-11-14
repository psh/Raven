import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.compose)
}

// The examples check: every .xml file of Raven's examples/ is translated, and the output is compiled
// against the current compose-raven-foundation, so the translator can't drift from its API.
val ravenExamples = rootProject.layout.projectDirectory.dir("../examples")
val translatedExamples = layout.buildDirectory.dir("generated/ravenExamples/kotlin")

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        val target = this
        binaries.executable {
            entryPoint = "raven.sample.xml.main"
            baseName = "xml-translator"
        }
        binaries.executable("translate", listOf(NativeBuildType.DEBUG)) { entryPoint = "raven.sample.xml.translateFiles" }

        val translate = tasks.register<Exec>("translateRavenExamples${target.name.replaceFirstChar(Char::uppercase)}") {
            description = "Translates Raven's examples/*.xml into Kotlin, for the examples check."
            val tool = target.binaries.getExecutable("translate", NativeBuildType.DEBUG)
            val examples = fileTree(ravenExamples) { include("**/*.xml") }
            val outputDir = translatedExamples
            // The tool is an input too: a changed translator must translate again.
            inputs.files(tool.linkTaskProvider)
            inputs.files(examples)
            outputs.dir(outputDir)
            executable = tool.outputFile.absolutePath
            doFirst {
                outputDir.get().asFile.deleteRecursively()
                outputDir.get().asFile.mkdirs()
            }
            argumentProviders += CommandLineArgumentProvider {
                examples.files.sorted().flatMap { file ->
                    val packageName = examplePackage(file.relativeTo(ravenExamples.asFile).invariantSeparatorsPath)
                    val output = outputDir.get().file(packageName.replace('.', '_') + ".kt").asFile
                    listOf(file.absolutePath, output.absolutePath, packageName)
                }
            }
        }

        val examples = compilations.create("examples") {
            defaultSourceSet {
                kotlin.srcDir(translate)
                dependencies { implementation(project(":compose-raven-foundation")) }
            }
        }
        tasks.named("allTests") { dependsOn(examples.compileTaskProvider) }
        tasks.named("check") { dependsOn(examples.compileTaskProvider) }
    }
    sourceSets.nativeMain.dependencies {
        implementation(project(":compose-raven-foundation"))
        // parseRavenXml reads Raven's XML API through raven.cinterop
        implementation(project(":compose-raven-native"))
    }
    // foundation's test harness, until it moves to compose-raven-test
    sourceSets.nativeTest { kotlin.srcDir(project(":compose-raven-foundation").file("src/testHarness/kotlin")) }
}

/** `music/ui/now-playing.xml` becomes the package `examples.music.ui.now_playing`. */
fun examplePackage(path: String): String = "examples." + path.removeSuffix(".xml").split('/').joinToString(".") { segment ->
    val name = segment.lowercase().replace(Regex("[^a-z0-9_]"), "_")
    when {
        name.first().isDigit() -> "_$name"
        name in setOf("as", "fun", "in", "is", "object", "package", "typealias", "val", "var", "when", "while") -> "${name}_"
        else -> name
    }
}
