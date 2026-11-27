import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** `ravenXml { }`: the app's Raven XML, translated into Kotlin by `raven-xml2compose` during its build. */
abstract class RavenXmlExtension {
    /** The folder of XML files, relative to the project; by default `src/nativeMain/xml`. */
    abstract val source: Property<String>

    /** The package of the generated code; each subfolder of [source] is a subpackage of it. */
    abstract val packageName: Property<String>

    /** The file, inside [source], whose `<Window>` becomes the app's `main()`; none by default. */
    abstract val main: Property<String>

    /** Fails the build on a warning, as it always does on an error. */
    abstract val werror: Property<Boolean>

    /** The tool; by default the `:tools:raven-xml2compose` executable of this repository, built first. */
    abstract val tool: RegularFileProperty
}

/** Runs `raven-xml2compose` over a folder of XML files into [outputDirectory], which it owns. */
abstract class TranslateRavenXml : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: DirectoryProperty

    /** A changed translator translates again. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val tool: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    @get:Optional
    abstract val main: Property<String>

    @get:Input
    abstract val werror: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val exec: ExecOperations

    @TaskAction
    fun translate() {
        val out = outputDirectory.get().asFile
        // The tool overwrites only its own files; files of XML that is gone go too.
        out.deleteRecursively()
        out.mkdirs()
        val args = mutableListOf(source.get().asFile.absolutePath, "--out", out.absolutePath, "--package", packageName.get())
        main.orNull?.let { args += listOf("--main", it) }
        if (werror.get()) args += "--werror"
        val output = ByteArrayOutputStream()
        val result = exec.exec {
            executable = tool.get().asFile.absolutePath
            args(args)
            errorOutput = output
            standardOutput = output
            isIgnoreExitValue = true
        }
        val lines = output.toString(Charsets.UTF_8).lines().filter { it.isNotBlank() }
        for (line in lines) {
            if (": error: " in line) logger.error(line) else logger.warn(line)
        }
        if (result.exitValue != 0) {
            val errors = lines.count { ": error: " in it }
            val warnings = lines.count { ": warning: " in it }
            val why = when {
                errors > 0 -> "$errors errors"
                werror.get() && warnings > 0 -> "$warnings warnings, with werror = true"
                else -> "exit code ${result.exitValue}"
            }
            throw GradleException("raven-xml2compose failed on ${source.get().asFile}: $why\n" + lines.joinToString("\n"))
        }
    }
}
