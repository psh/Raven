plugins { kotlin("multiplatform") version "2.4.21" }

kotlin {
    macosArm64(); linuxX64(); mingwX64()
    sourceSets.commonMain.dependencies {
        implementation("androidx.compose.runtime:runtime:1.12.1")
        // animation-core: no macOS or Windows variant, Linux is a stub that needs Compose UI (see README.md)
        implementation("androidx.navigation3:navigation3-runtime:1.2.1")
        implementation("androidx.navigationevent:navigationevent:1.1.1")
        implementation("androidx.navigationevent:navigationevent-compose:1.1.1")
    }
}

tasks.register("resolve") {
    val configs = listOf("macosArm64CompileKlibraries", "linuxX64CompileKlibraries", "mingwX64CompileKlibraries")
        .map { configurations.getByName(it) }
    doLast {
        configs.forEach { config ->
            val modules = config.incoming.resolutionResult.allComponents
                .mapNotNull { (it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier) }
                .map { "${it.group}:${it.module}:${it.version}" }.sorted()
            val ui = modules.filter { it.startsWith("androidx.compose.ui:") || it.startsWith("androidx.compose.foundation:") || it.startsWith("androidx.compose.animation:animation:") }
            println("== ${config.name}: ${modules.size} modules, Compose UI/Foundation/Animation: ${if (ui.isEmpty()) "none" else ui}")
            modules.filter { it.startsWith("androidx.") }.forEach { println("   $it") }
        }
    }
}
