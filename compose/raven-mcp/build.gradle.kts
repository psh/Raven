import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget

// Raven MCP (compose/docs/mcp.md, ADR 0003): one Kotlin/Native module on the MCP Kotlin SDK. It
// builds the Raven MCP library behind include/raven/raven_mcp.h, for C and every binding, and a
// klib for compose-raven. The library calls the app's own shared libraven, never a copy of its own.

plugins {
    id("raven.native-target")
    alias(libs.plugins.kotlin.serialization)
}

val ravenRoot = rootProject.layout.projectDirectory.dir("..")
val hostTarget = HostManager.host
val ravenSharedDir = rootProject.layout.buildDirectory.dir("raven-shared/${hostTarget.name}")

val configureRavenShared = tasks.register<Exec>("configureRavenShared") {
    description = "Configures a shared libraven with CMake (Release, library only), for the Raven MCP library."
    inputs.file(ravenRoot.file("CMakeLists.txt"))
    outputs.file(ravenSharedDir.map { it.file("CMakeCache.txt") })
    val args = mutableListOf(
        "cmake", "-S", ravenRoot.asFile.absolutePath, "-B", ravenSharedDir.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release", "-DRAVEN_BUILD_SHARED=ON",
        "-DRAVEN_BUILD_EXAMPLES=OFF", "-DRAVEN_BUILD_TESTS=OFF", "-DRAVEN_BUILD_TOOLS=OFF", "-DRAVEN_BUILD_WEB=OFF",
    )
    if (hostTarget == KonanTarget.MACOS_ARM64) {
        args += listOf("-DCMAKE_OSX_ARCHITECTURES=arm64", "-DCMAKE_OSX_DEPLOYMENT_TARGET=11.0")
    }
    commandLine(args)
}

val buildRavenShared = tasks.register<Exec>("buildRavenShared") {
    description = "Builds the shared libraven the Raven MCP library links against."
    dependsOn(configureRavenShared)
    inputs.dir(ravenRoot.dir("src"))
    inputs.dir(ravenRoot.dir("include"))
    outputs.dir(ravenSharedDir)
    commandLine("cmake", "--build", ravenSharedDir.get().asFile.absolutePath, "--target", "raven", "--parallel")
}

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops.create("raven") {
            definitionFile.set(file("src/nativeInterop/cinterop/raven.def"))
            includeDirs(ravenRoot.dir("include").asFile)
        }
        val shared = ravenSharedDir.get().asFile.absolutePath
        binaries.sharedLib("raven_mcp") {
            linkerOpts("-L$shared", "-lraven", "-Wl,-rpath,$shared")
            linkTaskProvider.configure { dependsOn(buildRavenShared) }
        }
        binaries.getTest("debug").apply {
            linkerOpts("-L$shared", "-lraven", "-Wl,-rpath,$shared")
            linkTaskProvider.configure { dependsOn(buildRavenShared) }
        }
    }
    sourceSets.nativeMain.dependencies {
        implementation(libs.mcp.sdk.server)
        implementation(libs.ktor.server.cio)
        implementation(libs.kotlin.logging)
    }
    sourceSets.all { languageSettings.optIn("kotlin.concurrent.atomics.ExperimentalAtomicApi") }
}

// The C API end to end: a headless Raven app in Python publishes tools, and an MCP client calls
// them over HTTP. Also checks that nothing reaches stdout but the test's own report.
val targetName = if (hostTarget == KonanTarget.MACOS_ARM64) "macosArm64" else "linuxX64"

val pythonTest = tasks.register<Exec>("pythonTest") {
    description = "Runs src/test/python/test_raven_mcp.py against the Raven MCP library."
    val link = tasks.named("linkRaven_mcpReleaseShared${targetName.replaceFirstChar { it.uppercase() }}")
    dependsOn(link, buildRavenShared)
    val library = if (hostTarget == KonanTarget.MACOS_ARM64) "libraven.dylib" else "libraven.so"
    val mcpLibrary = if (hostTarget == KonanTarget.MACOS_ARM64) "libraven_mcp.dylib" else "libraven_mcp.so"
    environment("RAVEN_LIBRARY", ravenSharedDir.get().file(library).asFile.absolutePath)
    environment("RAVEN_MCP_LIBRARY", layout.buildDirectory.file("bin/$targetName/raven_mcpReleaseShared/$mcpLibrary").get().asFile.absolutePath)
    environment("RAVEN_PYTHON", ravenRoot.dir("bindings/python").asFile.absolutePath)
    commandLine("python3", "-u", file("src/test/python/test_raven_mcp.py").absolutePath)
}
tasks.named("check") { dependsOn(pythonTest) }
