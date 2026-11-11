// The one Kotlin/Native target of this host. Each target builds and is tested on its own host,
// with no cross-compilation, because libraven's C build needs the host's toolchain and headers.

import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    when (HostManager.host) {
        KonanTarget.MACOS_ARM64 -> macosArm64()
        KonanTarget.LINUX_X64 -> linuxX64()
        else -> error("compose-raven builds on macOS (arm64) and Linux (x64) only, not ${HostManager.host}")
    }
    compilerOptions.optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
    sourceSets.named("commonTest") {
        dependencies { implementation(kotlin("test")) }
    }
}
