// Compose Multiplatform's resources for an app of this repository: the files of
// src/nativeMain/composeResources/ (drawable/, font/, files/), a generated `Res` for them, and the
// folder copied into resources/ beside each executable, test executables too, which is where
// compose-raven resolves relative resource paths by default; or, with `ravenResources { embed = true }`,
// every file built into the executables instead.

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

val ravenResources = extensions.create<RavenResourcesExtension>("ravenResources")
// Compose's default: "<group>.<module>", lowercased, each part a Kotlin name (compose-raven is compose_raven).
ravenResources.packageOfResClass.convention(
    provider {
        val group = project.group.toString().takeIf { it.isNotEmpty() && it != "unspecified" }
        listOfNotNull(group, project.name).joinToString(".").lowercase().split('.').filter { it.isNotEmpty() }
            .joinToString(".") { part ->
                val name = part.map { if (it.isLetterOrDigit() || it == '_') it else '_' }.joinToString("")
                if (name.first().isDigit()) "_$name" else name
            } + ".generated.resources"
    },
)
ravenResources.embed.convention(false)
ravenResources.composeResourcesDirectory.convention(layout.projectDirectory.dir("src/nativeMain/composeResources"))

val composeResources = ravenResources.composeResourcesDirectory

val generateResClass = tasks.register<GenerateResClass>("generateResClass") {
    description = "Generates Res for src/nativeMain/composeResources/."
    group = "raven"
    resources.set(composeResources.map { if (it.asFile.isDirectory) it else null })
    packageName.set(ravenResources.packageOfResClass)
    embed.set(ravenResources.embed)
    outputDirectory.set(layout.buildDirectory.dir("generated/ravenResources/kotlin"))
}

plugins.withId("org.jetbrains.kotlin.multiplatform") {
    val multiplatform = extensions.getByType<KotlinMultiplatformExtension>()
    multiplatform.sourceSets.matching { it.name == "nativeMain" }.configureEach { kotlin.srcDir(generateResClass) }
    multiplatform.targets.withType<KotlinNativeTarget>().configureEach {
        binaries.configureEach {
            if (this !is Executable && this !is TestExecutable) return@configureEach
            val binary = this
            val taskName = "copyComposeResources" + binary.name.replaceFirstChar { it.uppercase() } + binary.target.name.replaceFirstChar { it.uppercase() }
            val copy = tasks.register<Sync>(taskName) {
                description = "Copies composeResources/ into resources/ beside ${binary.name}."
                group = "raven"
                from(composeResources)
                into(binary.outputDirectoryProperty.dir("resources"))
                // Embedded, the files are in the executable: nothing goes beside it.
                val embed = ravenResources.embed
                onlyIf("the resources aren't embedded") { !embed.get() }
            }
            binary.linkTaskProvider.configure { finalizedBy(copy) }
            if (binary is Executable) binary.runTaskProvider?.configure { dependsOn(copy) }
            if (binary is TestExecutable) tasks.withType<KotlinNativeTest>().configureEach { dependsOn(copy) }
        }
    }
}
