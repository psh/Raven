import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * raven.compose-xml in a build of its own, over a stand-in for raven-xml2compose (a shell script
 * that writes one file and reports what the XML asks for), so no Raven is needed.
 */
class RavenXmlPluginTest {
    private val dir: File = Files.createTempDirectory("raven-compose-xml").toFile()
    private val task = ":translateRavenXml" + hostTarget().replaceFirstChar { it.uppercase() }

    @AfterTest
    fun removeTheBuild() {
        dir.deleteRecursively()
    }

    private fun hostTarget(): String {
        val os = System.getProperty("os.name").lowercase()
        return if ("mac" in os) "macosArm64" else "linuxX64"
    }

    private fun project(werror: Boolean = false) {
        File(dir, "settings.gradle.kts").writeText("rootProject.name = \"app\"\n")
        File(dir, "build.gradle.kts").writeText(
            """
            plugins {
                id("raven.native-target")
                id("raven.compose-xml")
            }
            ravenXml {
                packageName = "app.ui"
                main = "main.xml"
                werror = $werror
                tool = file("tool.sh")
            }
            """.trimIndent(),
        )
        tool("one")
        xml("main.xml", "<Window/>")
    }

    /** The stand-in: `tool.sh <dir> --out <dir> --package <name> [--main <file>] [--werror]`. */
    private fun tool(version: String) {
        val script = File(dir, "tool.sh")
        script.writeText(
            """
            #!/bin/sh
            # version $version
            src="${'$'}1"; out="${'$'}3"; werror=no
            for a in "${'$'}@"; do [ "${'$'}a" = "--werror" ] && werror=yes; done
            status=0
            echo "package ${'$'}5" > "${'$'}out/Gen.kt"
            for f in "${'$'}src"/*.xml; do
                n=${'$'}(basename "${'$'}f")
                if grep -q WARN "${'$'}f"; then echo "${'$'}f:1:1: warning: a warning in ${'$'}n" >&2; [ ${'$'}werror = yes ] && status=1; fi
                if grep -q ERROR "${'$'}f"; then echo "${'$'}f:1:1: error: an error in ${'$'}n" >&2; status=1; fi
            done
            exit ${'$'}status
            """.trimIndent() + "\n",
        )
        script.setExecutable(true)
    }

    private fun xml(path: String, text: String) {
        File(dir, "src/nativeMain/xml/$path").apply { parentFile.mkdirs() }.writeText(text)
    }

    private fun run(fails: Boolean = false): BuildResult {
        val runner = GradleRunner.create().withProjectDir(dir).withPluginClasspath().withArguments(task, "--stacktrace")
        return if (fails) runner.buildAndFail() else runner.build()
    }

    @Test
    fun itTranslatesIntoTheGeneratedFolderAndIsThenUpToDate() {
        project()
        assertEquals(TaskOutcome.SUCCESS, run().task(task)?.outcome)
        val generated = File(dir, "build/generated/ravenXml/${hostTarget()}/Gen.kt")
        assertEquals("package app.ui\n", generated.readText())
        assertEquals(TaskOutcome.UP_TO_DATE, run().task(task)?.outcome)
    }

    @Test
    fun aChangedXmlFileOrToolTranslatesAgain() {
        project()
        run()
        xml("main.xml", "<Window Title=\"changed\"/>")
        assertEquals(TaskOutcome.SUCCESS, run().task(task)?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, run().task(task)?.outcome)
        tool("two")
        assertEquals(TaskOutcome.SUCCESS, run().task(task)?.outcome)
    }

    @Test
    fun warningsShowInTheOutputAndWerrorFailsOnThem() {
        project()
        xml("page.xml", "<VBox>WARN</VBox>")
        val result = run()
        assertTrue("page.xml:1:1: warning: a warning in page.xml" in result.output, result.output)
        project(werror = true)
        xml("page.xml", "<VBox>WARN</VBox>")
        val failed = run(fails = true)
        assertTrue("1 warnings, with werror = true" in failed.output, failed.output)
    }

    @Test
    fun anErrorAlwaysFails() {
        project()
        xml("page.xml", "<VBox>ERROR</VBox>")
        val failed = run(fails = true)
        assertTrue("page.xml:1:1: error: an error in page.xml" in failed.output && "1 errors" in failed.output, failed.output)
    }
}
