import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    id("raven.native-target")
}

// libraven, built by Raven's own CMake build from the repository root.

val ravenRoot = rootProject.layout.projectDirectory.dir("..")
val hostTarget = HostManager.host
val ravenBuildDir = rootProject.layout.buildDirectory.dir("raven/${hostTarget.name}")
val linkLibrariesFile = ravenBuildDir.map { it.file("raven-libraries.txt") }

val configureRaven = tasks.register<Exec>("configureRaven") {
    description = "Configures libraven with CMake (Release, library only)."
    inputs.file(ravenRoot.file("CMakeLists.txt"))
    inputs.dir(ravenRoot.dir("cmake"))
    inputs.file(layout.projectDirectory.file("cmake/write-link-libraries.cmake"))
    outputs.file(ravenBuildDir.map { it.file("CMakeCache.txt") })
    outputs.file(linkLibrariesFile)

    val args = mutableListOf(
        "cmake", "-S", ravenRoot.asFile.absolutePath, "-B", ravenBuildDir.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DRAVEN_BUILD_EXAMPLES=OFF", "-DRAVEN_BUILD_TESTS=OFF",
        "-DRAVEN_BUILD_TOOLS=OFF", "-DRAVEN_BUILD_WEB=OFF",
        "-DCMAKE_PROJECT_INCLUDE=${layout.projectDirectory.file("cmake/write-link-libraries.cmake").asFile.absolutePath}",
        "-DCOMPOSE_RAVEN_LIBRARIES_FILE=${linkLibrariesFile.get().asFile.absolutePath}",
    )
    if (hostTarget == KonanTarget.MACOS_ARM64) {
        // Kotlin/Native's minimum macOS version, so the linker doesn't warn about newer objects.
        args += listOf("-DCMAKE_OSX_ARCHITECTURES=arm64", "-DCMAKE_OSX_DEPLOYMENT_TARGET=11.0")
    }
    commandLine(args)
}

val buildRaven = tasks.register<Exec>("buildRaven") {
    description = "Builds libraven.a for this host."
    dependsOn(configureRaven)
    inputs.dir(ravenRoot.dir("src"))
    inputs.dir(ravenRoot.dir("include"))
    inputs.dir(ravenRoot.dir("third_party"))
    outputs.file(ravenBuildDir.map { it.file("libraven.a") })
    commandLine(
        "cmake", "--build", ravenBuildDir.get().asFile.absolutePath,
        "--target", "raven", "--parallel",
    )
}

// The linker options in raven.def must match the libraries CMake links into libraven.

val checkRavenLinkerOpts = tasks.register("checkRavenLinkerOpts") {
    description = "Fails when raven.def's linkerOpts drift from RAVEN_LIBRARIES in CMakeLists.txt."
    dependsOn(configureRaven)
    val defFile = layout.projectDirectory.file("src/nativeInterop/cinterop/raven.def")
    val librariesFile = linkLibrariesFile
    val defKey = if (hostTarget == KonanTarget.MACOS_ARM64) "linkerOpts.osx" else "linkerOpts.linux"
    val isApple = hostTarget == KonanTarget.MACOS_ARM64
    inputs.file(defFile)
    inputs.file(librariesFile)
    doLast {
        val expected = librariesFile.get().asFile.readLines().filter { it.isNotBlank() }
            .mapNotNull { ravenLibraryToLinkerOpt(it.trim(), isApple) }.toSortedSet()
        val opts = defFile.asFile.readLines().first { it.startsWith("$defKey ") || it.startsWith("$defKey=") }
            .substringAfter("=").trim().split(Regex("\\s+"))
        val actual = sortedSetOf<String>()
        var i = 0
        while (i < opts.size) {
            when {
                opts[i] == "-framework" -> { actual += "-framework ${opts[i + 1]}"; i++ }
                opts[i].startsWith("-l") -> actual += opts[i]
            }
            i++
        }
        if (expected != actual) {
            throw GradleException(
                "raven.def's $defKey has drifted from RAVEN_LIBRARIES in CMakeLists.txt.\n" +
                    "  CMake links: $expected\n  raven.def:   $actual",
            )
        }
    }
}

/** What one RAVEN_LIBRARIES entry means as a linker option; null for what the OS links anyway. */
fun ravenLibraryToLinkerOpt(library: String, isApple: Boolean): String? {
    val name = library.substringAfterLast('/')
    return when {
        name.endsWith(".framework") -> "-framework ${name.removeSuffix(".framework")}"
        library == "Threads::Threads" -> if (isApple) null else "-lpthread"
        isApple && library in setOf("m", "dl", "pthread") -> null // part of libSystem
        name.startsWith("lib") && (name.contains(".so") || name.endsWith(".a") || name.endsWith(".dylib")) ->
            "-l" + name.removePrefix("lib").substringBefore(".")
        else -> "-l$library"
    }
}

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops.create("raven") {
            definitionFile.set(layout.projectDirectory.file("src/nativeInterop/cinterop/raven.def"))
            includeDirs(ravenRoot.dir("include").asFile)
            extraOpts("-libraryPath", ravenBuildDir.get().asFile.absolutePath)
            tasks.named(interopProcessingTaskName) { dependsOn(buildRaven, checkRavenLinkerOpts) }
        }
    }
    compilerOptions.optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
    sourceSets.nativeTest.dependencies {
        implementation(kotlin("test"))
    }
}

tasks.named("check") { dependsOn(checkRavenLinkerOpts) }
