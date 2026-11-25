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
            // what a program linking it records: found by its rpath, not at this build's path
            if (hostTarget == KonanTarget.MACOS_ARM64) {
                linkerOpts("-Wl,-install_name,@rpath/libraven_mcp.dylib")
            } else {
                linkerOpts("-Wl,-soname,libraven_mcp.so")
            }
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

// The UI tools from C: the notepad (src/test/c/notes.c) as a C app in its own process, driven by an
// MCP client (src/test/python/test_ui_tools_c.py).
val notesApp = layout.buildDirectory.file("c/notes")
val buildNotesApp = tasks.register<Exec>("buildNotesApp") {
    description = "Builds src/test/c/notes.c against the shared libraven and the Raven MCP library."
    val link = tasks.named("linkRaven_mcpReleaseShared${targetName.replaceFirstChar { it.uppercase() }}")
    dependsOn(link, buildRavenShared)
    val source = file("src/test/c/notes.c")
    inputs.file(source)
    outputs.file(notesApp)
    val ravenDir = ravenSharedDir.get().asFile.absolutePath
    val mcpDir = layout.buildDirectory.dir("bin/$targetName/raven_mcpReleaseShared").get().asFile.absolutePath
    doFirst { notesApp.get().asFile.parentFile.mkdirs() }
    commandLine(
        "cc", "-std=c11", "-Wall", "-Wextra", "-Werror", "-I", ravenRoot.dir("include").asFile.absolutePath,
        source.absolutePath, "-o", notesApp.get().asFile.absolutePath,
        "-L$ravenDir", "-lraven", "-L$mcpDir", "-lraven_mcp", "-Wl,-rpath,$ravenDir", "-Wl,-rpath,$mcpDir",
    )
}
val cTest = tasks.register<Exec>("cTest") {
    description = "Runs src/test/python/test_ui_tools_c.py against the C notepad."
    dependsOn(buildNotesApp)
    val library = if (hostTarget == KonanTarget.MACOS_ARM64) "libraven.dylib" else "libraven.so"
    val mcpLibrary = if (hostTarget == KonanTarget.MACOS_ARM64) "libraven_mcp.dylib" else "libraven_mcp.so"
    environment("NOTES_APP", notesApp.get().asFile.absolutePath)
    environment("RAVEN_LIBRARY", ravenSharedDir.get().file(library).asFile.absolutePath)
    environment("RAVEN_MCP_LIBRARY", layout.buildDirectory.file("bin/$targetName/raven_mcpReleaseShared/$mcpLibrary").get().asFile.absolutePath)
    environment("RAVEN_PYTHON", ravenRoot.dir("bindings/python").asFile.absolutePath)
    workingDir(file("src/test/python"))
    commandLine("python3", "-u", file("src/test/python/test_ui_tools_c.py").absolutePath)
}
tasks.named("check") { dependsOn(cTest) }

// Python's raven_mcp (bindings/python/raven_mcp.py): its own tests, then the notes example's tools
// (examples/notes/test_notes_mcp.py), each called by an MCP client.
val pythonWrapperTest = tasks.register<Exec>("pythonWrapperTest") {
    description = "Runs raven_mcp's tests and the notes example's tools against the Raven MCP library."
    val link = tasks.named("linkRaven_mcpReleaseShared${targetName.replaceFirstChar { it.uppercase() }}")
    dependsOn(link, buildRavenShared)
    val library = if (hostTarget == KonanTarget.MACOS_ARM64) "libraven.dylib" else "libraven.so"
    val mcpLibrary = if (hostTarget == KonanTarget.MACOS_ARM64) "libraven_mcp.dylib" else "libraven_mcp.so"
    val python = ravenRoot.dir("bindings/python").asFile.absolutePath
    environment("RAVEN_LIBRARY", ravenSharedDir.get().file(library).asFile.absolutePath)
    environment("RAVEN_MCP_LIBRARY", layout.buildDirectory.file("bin/$targetName/raven_mcpReleaseShared/$mcpLibrary").get().asFile.absolutePath)
    environment("PYTHONPATH", python)
    workingDir(ravenRoot.dir("examples/notes").asFile)
    commandLine("sh", "-c", "python3 -u -m unittest -v test_raven_mcp && python3 -u test_notes_mcp.py")
}
tasks.named("check") { dependsOn(pythonWrapperTest) }

// Rust: raven-gui's feature `mcp` (examples/mcp.rs, the C API), then the crate raven-gui-mcp in
// bindings/rust/mcp: raven_mcp's tests (tests/mcp.rs) and the notes example's tools (tests/notes_mcp.rs),
// each linking the library and calling tools over HTTP. raven-gui-mcp's dependencies (serde,
// schemars) may be fetched the first time. Part of `check` only where cargo is installed.
val cargo = listOf(System.getenv("HOME") + "/.cargo/bin/cargo", "/opt/homebrew/bin/cargo", "/opt/homebrew/opt/rustup/bin/cargo", "/usr/local/bin/cargo", "/usr/bin/cargo")
    .firstOrNull { File(it).canExecute() }
val rustTest = tasks.register<Exec>("rustTest") {
    description = "Runs the Rust binding's Raven MCP example (--test), raven_mcp's tests and the notes example's tools against the Raven MCP library."
    val link = tasks.named("linkRaven_mcpReleaseShared${targetName.replaceFirstChar { it.uppercase() }}")
    dependsOn(link, buildRavenShared)
    val ravenDir = ravenSharedDir.get().asFile.absolutePath
    val mcpDir = layout.buildDirectory.dir("bin/$targetName/raven_mcpReleaseShared").get().asFile.absolutePath
    workingDir(ravenRoot.dir("bindings/rust").asFile)
    environment("RAVEN_LIB_DIR", ravenDir)
    environment("RAVEN_MCP_LIB_DIR", mcpDir)
    // (rustup's cargo runs rustc from its own folder, which may not be on the PATH)
    if (cargo != null) environment("PATH", File(cargo).parent + File.pathSeparator + System.getenv("PATH"))
    environment("CARGO_TARGET_DIR", layout.buildDirectory.dir("rust").get().asFile.absolutePath)
    // both libraries are found by rpath (cargo run replaces DYLD_FALLBACK_LIBRARY_PATH)
    environment("RUSTFLAGS", "-C link-arg=-Wl,-rpath,$ravenDir -C link-arg=-Wl,-rpath,$mcpDir")
    commandLine(
        "sh", "-c",
        "\"$1\" run --offline --quiet --features mcp --example mcp -- --test && " +
            "cd mcp && \"$1\" test --quiet",
        "rustTest", cargo ?: "cargo",
    )
}
if (cargo != null) tasks.named("check") { dependsOn(rustTest) }
